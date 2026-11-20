package io.github.rkaushikdev.garterhole.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import java.time.Instant;
import java.util.HashSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IdSourceTest {

    /** A source whose randomness is a fixed sequence, so the ids are known. */
    private static IdSource fixed(long at) {
        int[] step = {0};
        return new IdSource(() -> at, () -> {
            step[0] += 1;
            return (step[0] % 32) / 32.0;
        });
    }

    private static IdSource fixed() {
        return fixed(1_700_000_000_000L);
    }

    @Nested
    class MakingIdentifiers {

        @Test
        void makesOneOfAStatedLength() {
            String id = new IdSource(() -> 0, () -> 0, 6).next();
            assertThat(id).hasSize(15);
        }

        @Test
        void putsAPrefixInFrontWithAnUnderscore() {
            assertThat(fixed().next("req")).startsWith("req_");
        }

        @Test
        void makesDifferentIdentifiersEachTime() {
            IdSource source = IdSource.defaultIds();
            assertThat(new HashSet<>(source.many(50))).hasSize(50);
        }

        @Test
        void makesSeveralAtOnce() {
            assertThat(fixed().many(3, "req")).hasSize(3);
        }

        @Test
        void makesNoneWhenAskedForNone() {
            assertThat(fixed().many(0)).isEmpty();
        }

        @Test
        void refusesACountThatIsNotOne() {
            assertThatThrownBy(() -> fixed().many(-1)).hasMessageContaining("is not a number of identifiers");
        }

        @Test
        void refusesALengthThatWouldBeUselessOrSilly() {
            assertThatThrownBy(() -> new IdSource(System::currentTimeMillis, () -> 0.5, 2))
                    .hasMessageContaining("cannot have 2 random characters");
            assertThatThrownBy(() -> new IdSource(
                            System::currentTimeMillis,
                            () -> ThreadLocalRandom.current().nextDouble(),
                            64))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class SortingByTime {

        @Test
        void sortsIdentifiersMadeLaterAfterOnesMadeEarlier() {
            String early = fixed(1_700_000_000_000L).next();
            String late = fixed(1_700_000_001_000L).next();
            assertThat(Stream.of(late, early).sorted().toList()).containsExactly(early, late);
        }

        @Test
        void keepsTheSameLengthAsTimePasses() {
            assertThat(fixed(1_000_000_000_000L).next())
                    .hasSameSizeAs(fixed(1_900_000_000_000L).next());
        }
    }

    @Nested
    class LooksLikeId {

        @Test
        void recognisesOneItMade() {
            assertThat(IdSource.looksLikeId(fixed().next())).isTrue();
            assertThat(IdSource.looksLikeId(fixed().next("req"))).isTrue();
        }

        @Test
        void rejectsSomethingTooShort() {
            assertThat(IdSource.looksLikeId("abc")).isFalse();
        }

        @Test
        void rejectsACharacterAnIdentifierNeverContains() {
            assertThat(IdSource.looksLikeId("ABCDEFGHIJKLMN")).isFalse();
            assertThat(IdSource.looksLikeId("abcdefg-hijklmn")).isFalse();
        }

        @Test
        void drawsTheRandomTailFromTheAlphabetThatLeavesOutTheMisreadLetters() {
            String tail = fixed().next().substring(9);
            for (char character : tail.toCharArray()) {
                assertThat("ilou").doesNotContain(String.valueOf(character));
            }
        }

        @Test
        void rejectsSomethingThatIsNotAString() {
            assertThat(IdSource.looksLikeId(null)).isFalse();
        }
    }

    @Nested
    class MadeAt {

        @Test
        void readsTheMomentBackOut() {
            long at = 1_700_000_000_000L;
            assertThat(IdSource.madeAt(fixed(at).next())).hasValue(Instant.ofEpochMilli(at));
        }

        @Test
        void readsItOutOfAPrefixedIdentifierToo() {
            long at = 1_700_000_000_000L;
            assertThat(IdSource.madeAt(fixed(at).next("req"))).hasValue(Instant.ofEpochMilli(at));
        }

        @Test
        void givesNothingForSomethingThatIsNotOne() {
            assertThat(IdSource.madeAt("not an id")).isEmpty();
            assertThat(IdSource.madeAt(null)).isEmpty();
        }
    }
}
