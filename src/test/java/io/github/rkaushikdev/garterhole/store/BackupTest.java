package io.github.rkaushikdev.garterhole.store;

import static io.github.rkaushikdev.garterhole.store.Backup.backup;
import static io.github.rkaushikdev.garterhole.store.Backup.backupName;
import static io.github.rkaushikdev.garterhole.store.Backup.listBackups;
import static io.github.rkaushikdev.garterhole.store.Backup.newestBackup;
import static io.github.rkaushikdev.garterhole.store.Backup.prune;
import static io.github.rkaushikdev.garterhole.store.Backup.restore;
import static io.github.rkaushikdev.garterhole.store.Backup.verify;
import static io.github.rkaushikdev.garterhole.store.Documents.docs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.StorageException;
import io.github.rkaushikdev.garterhole.ValidationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackupTest {

    @TempDir
    Path directory;

    private Journal journal;
    private Path backups;
    private final AtomicLong moment = new AtomicLong();

    /** A clock that moves on a second every time it is read, so every backup has its own name. */
    private final Supplier<Instant> clock = () -> Instant.ofEpochMilli(moment.addAndGet(1000));

    @BeforeEach
    void fill() {
        journal = new Journal(directory.resolve("journal.jsonl"));
        backups = directory.resolve("backups");
        journal.appendAll(docs("{'n':1}", "{'n':2}", "{'n':3}"));
    }

    @Nested
    class TakingABackup {

        @Test
        void copiesTheJournalAndCountsWhatIsInIt() {
            Backup.Taken taken = backup(journal, backups, clock);
            assertThat(taken.path()).exists();
            assertThat(taken.records()).isEqualTo(3);
        }

        @Test
        void makesTheDirectoryItNeeds() {
            assertThat(backups).doesNotExist();
            backup(journal, backups, clock);
            assertThat(backups).exists();
        }

        @Test
        void namesTheFileAfterTheTimeItWasTaken() {
            Backup.Taken taken = backup(journal, backups, clock);
            assertThat(backupName(taken.path())).startsWith("journal-");
            assertThat(backupName(taken.path())).endsWith(".jsonl");
            assertThat(backupName(taken.path())).isEqualTo("journal-1970-01-01T00-00-01-000Z.jsonl");
        }

        @Test
        void refusesWhenThereIsNoJournalToCopy() {
            Journal missing = new Journal(directory.resolve("nothing.jsonl"));
            assertThatThrownBy(() -> backup(missing, backups)).hasMessage("there is no journal to back up");
        }

        @Test
        void refusesOptionsItCannotUse() {
            assertThatThrownBy(() -> backup(null, null)).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> backup(null, backups)).hasMessageContaining("needs a Journal");
            assertThatThrownBy(() -> backup(journal, Path.of(""))).hasMessageContaining("needs a directory");
        }
    }

    @Nested
    class Verifying {

        @Test
        void readsABackupBackAndCountsIt() {
            Backup.Taken taken = backup(journal, backups, clock);
            assertThat(verify(taken.path())).isEqualTo(3);
        }

        @Test
        void refusesABackupThatIsNotThere() {
            assertThatThrownBy(() -> verify(backups.resolve("nothing.jsonl")))
                    .hasMessageContaining("there is no backup at");
        }

        @Test
        void throwsADamagedCopyAwayRatherThanKeepingIt() throws IOException {
            Backup.Taken taken = backup(journal, backups, clock);
            Files.writeString(taken.path(), "not a journal\n", StandardCharsets.UTF_8);
            assertThatThrownBy(() -> verify(taken.path())).isInstanceOf(StorageException.class);
            assertThat(taken.path()).doesNotExist();
        }
    }

    @Nested
    class ListingAndPruning {

        @BeforeEach
        void takeThree() {
            backup(journal, backups, clock);
            backup(journal, backups, clock);
            backup(journal, backups, clock);
        }

        @Test
        void listsThemNewestFirst() {
            List<Backup.BackupFile> listed = listBackups(backups);
            assertThat(listed).hasSize(3);
            assertThat(listed.get(0).name()).isGreaterThan(listed.get(1).name());
        }

        @Test
        void givesNothingForADirectoryThatIsNotThere() {
            assertThat(listBackups(directory.resolve("nowhere"))).isEmpty();
        }

        @Test
        void keepsOnlyTheNewestFew() {
            List<String> removed = prune(backups, 2);
            assertThat(removed).hasSize(1);
            assertThat(listBackups(backups)).hasSize(2);
        }

        @Test
        void removesNothingWhenThereAreFewEnough() {
            assertThat(prune(backups, 5)).isEmpty();
        }

        @Test
        void prunesAsPartOfTakingABackup() {
            Backup.Taken taken = backup(journal, backups, clock, 2);
            assertThat(taken.removed()).isNotEmpty();
            assertThat(listBackups(backups)).hasSize(2);
        }

        @Test
        void refusesAPolicyThatKeepsNothing() {
            assertThatThrownBy(() -> prune(backups, 0)).isInstanceOf(ValidationException.class);
        }

        @Test
        void namesTheNewest() {
            assertThat(newestBackup(backups))
                    .hasValue(listBackups(backups).get(0).path());
            assertThat(newestBackup(directory.resolve("nowhere"))).isEmpty();
        }
    }

    @Nested
    class Restoring {

        @Test
        void putsABackupBackOverTheJournal() {
            Backup.Taken taken = backup(journal, backups, clock);
            journal.rewrite(docs("{'n':9}"));
            assertThat(restore(journal, taken.path())).isEqualTo(3);
            assertThat(journal.read()).isEqualTo(docs("{'n':1}", "{'n':2}", "{'n':3}"));
        }

        @Test
        void restoresIntoAJournalThatIsNotThereYet() {
            Backup.Taken taken = backup(journal, backups, clock);
            Journal fresh = new Journal(directory.resolve("restored").resolve("journal.jsonl"));
            assertThat(restore(fresh, taken.path())).isEqualTo(3);
            assertThat(fresh.count()).isEqualTo(3);
        }

        @Test
        void refusesARestoreItCannotDo() {
            assertThatThrownBy(() -> restore(null, Path.of("x"))).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> restore(journal, backups.resolve("nothing.jsonl")))
                    .isInstanceOf(StorageException.class);
        }

        @Test
        void leavesTheJournalAloneWhenTheBackupIsUnreadable() throws IOException {
            Backup.Taken taken = backup(journal, backups, clock);
            Files.writeString(taken.path(), "rubbish\n", StandardCharsets.UTF_8);
            String before = Files.readString(journal.path(), StandardCharsets.UTF_8);
            assertThatThrownBy(() -> restore(journal, taken.path())).isInstanceOf(StorageException.class);
            assertThat(Files.readString(journal.path(), StandardCharsets.UTF_8)).isEqualTo(before);
        }
    }
}
