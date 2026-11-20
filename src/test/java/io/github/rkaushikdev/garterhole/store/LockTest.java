package io.github.rkaushikdev.garterhole.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ConflictException;
import io.github.rkaushikdev.garterhole.ValidationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LockTest {

    @TempDir
    Path directory;

    private Path path;
    private long clock;

    @BeforeEach
    void place() {
        path = directory.resolve("lock");
        clock = 1000;
    }

    private Lock lock(long pid) {
        return new Lock(path, () -> clock, pid);
    }

    private Lock lock() {
        return lock(1234);
    }

    @Nested
    class TakingALock {

        @Test
        void isNotHeldUntilItIsTaken() {
            assertThat(lock().exists()).isFalse();
            assertThat(lock().holder()).isEmpty();
            assertThat(lock().age()).isEmpty();
        }

        @Test
        void createsTheFileAndRecordsWhoTookIt() {
            lock().acquire();
            assertThat(lock().holder()).hasValue(new Lock.Holder(1234, 1000));
        }

        @Test
        void makesTheDirectoryItNeeds() {
            Lock nested = new Lock(directory.resolve("deep").resolve("lock"), () -> clock);
            assertThatCode(nested::acquire).doesNotThrowAnyException();
        }

        @Test
        void refusesToTakeALockSomebodyElseHolds() {
            lock(1234).acquire();
            assertThatThrownBy(() -> lock(5678).acquire()).isInstanceOf(ConflictException.class);
            assertThatThrownBy(() -> lock(5678).acquire()).hasMessageContaining("held by process 1234");
        }

        @Test
        void refusesAPathThatIsNotOne() {
            assertThatThrownBy(() -> new Lock("")).hasMessageContaining("a lock needs a path");
        }
    }

    @Nested
    class GivingALockUp {

        @Test
        void removesTheFile() {
            Lock holder = lock();
            holder.acquire();
            assertThat(holder.release()).isTrue();
            assertThat(holder.exists()).isFalse();
        }

        @Test
        void doesNothingWhenItNeverHeldTheLock() {
            lock(1234).acquire();
            assertThat(lock(5678).release()).isFalse();
            assertThat(lock().exists()).isTrue();
        }

        @Test
        void letsTheLockBeTakenAgainAfterwards() {
            Lock first = lock();
            first.acquire();
            first.release();
            assertThatCode(() -> lock(5678).acquire()).doesNotThrowAnyException();
        }
    }

    @Nested
    class ReadingALock {

        @Test
        void measuresHowLongItHasBeenHeld() {
            lock().acquire();
            clock = 5000;
            assertThat(lock().age()).hasValue(4000);
        }

        @Test
        void givesNothingForALockFileItCannotRead() throws IOException {
            Files.writeString(path, "not json", StandardCharsets.UTF_8);
            assertThat(lock().holder()).isEmpty();
            assertThat(lock().age()).isEmpty();
        }

        @Test
        void givesNothingForALockFileWithTheWrongShape() throws IOException {
            Files.writeString(path, "{\"pid\":\"me\"}", StandardCharsets.UTF_8);
            assertThat(lock().holder()).isEmpty();
        }
    }

    @Nested
    class BreakingAStaleLock {

        @Test
        void breaksOneOlderThanTheAgeGiven() {
            lock().acquire();
            clock = 60000;
            assertThat(lock().breakStale(30000)).isTrue();
            assertThat(lock().exists()).isFalse();
        }

        @Test
        void leavesAFreshLockAlone() {
            lock().acquire();
            clock = 1500;
            assertThat(lock().breakStale(30000)).isFalse();
            assertThat(lock().exists()).isTrue();
        }

        @Test
        void doesNothingWhenThereIsNoLock() {
            assertThat(lock().breakStale(0)).isFalse();
        }

        @Test
        void refusesAnAgeThatIsNotOne() {
            assertThatThrownBy(() -> lock().breakStale(-1)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class Around {

        @Test
        void holdsTheLockWhileTheWorkRunsAndGivesItUpAfter() {
            Lock holder = lock();
            boolean seen = holder.around(holder::exists);
            assertThat(seen).isTrue();
            assertThat(holder.exists()).isFalse();
        }

        @Test
        void givesTheLockUpEvenWhenTheWorkThrows() {
            Lock holder = lock();
            assertThatThrownBy(() -> holder.around(() -> {
                        throw new IllegalStateException("no");
                    }))
                    .hasMessage("no");
            assertThat(holder.exists()).isFalse();
        }

        @Test
        void refusesToBeGivenSomethingThatIsNotWork() {
            assertThatThrownBy(() -> lock().around(null)).hasMessageContaining("something to do while it is held");
        }
    }
}
