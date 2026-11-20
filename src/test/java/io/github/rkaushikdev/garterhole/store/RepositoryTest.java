package io.github.rkaushikdev.garterhole.store;

import static io.github.rkaushikdev.garterhole.store.Documents.doc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.IntNode;
import io.github.rkaushikdev.garterhole.NotFoundException;
import io.github.rkaushikdev.garterhole.StorageException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryTest {

    @TempDir
    Path directory;

    private Journal journal;
    private Snapshot snapshot;

    @BeforeEach
    void files() {
        journal = new Journal(directory.resolve("records.jsonl"));
        snapshot = new Snapshot(directory.resolve("state.json"));
    }

    private Repository open() {
        return new Repository(journal);
    }

    private Repository openWithSnapshot() {
        return new Repository(journal, snapshot);
    }

    @Nested
    class ANewRepository {

        @Test
        void startsEmpty() {
            Repository repository = open();
            assertThat(repository.collectionNames()).isEmpty();
            assertThat(repository.count("towers")).isZero();
            assertThat(repository.all("towers")).isEmpty();
        }

        @Test
        void refusesOptionsItCannotUse() {
            assertThatThrownBy(() -> new Repository(null)).hasMessageContaining("needs a Journal");
            assertThatThrownBy(() -> new Repository(null, snapshot)).hasMessageContaining("needs a Journal");
        }
    }

    @Nested
    class FilingDocuments {

        @Test
        void filesAndFindsOne() {
            Repository repository = open();
            repository.put("towers", "st-mary", doc("{'name':'St Mary','bells':8}"));
            assertThat(repository.get("towers", "st-mary")).isEqualTo(doc("{'name':'St Mary','bells':8}"));
            assertThat(repository.has("towers", "st-mary")).isTrue();
        }

        @Test
        void replacesADocumentWithTheSameId() {
            Repository repository = open();
            repository.put("towers", "st-mary", doc("{'bells':6}"));
            repository.put("towers", "st-mary", doc("{'bells':8}"));
            assertThat(repository.get("towers", "st-mary")).isEqualTo(doc("{'bells':8}"));
            assertThat(repository.count("towers")).isEqualTo(1);
        }

        @Test
        void keepsCollectionsApart() {
            Repository repository = open();
            repository.put("towers", "a", doc("{'n':1}"));
            repository.put("methods", "a", doc("{'n':2}"));
            assertThat(repository.get("towers", "a")).isEqualTo(doc("{'n':1}"));
            assertThat(repository.collectionNames()).containsExactly("methods", "towers");
        }

        @Test
        void throwsForADocumentItDoesNotHold() {
            assertThatThrownBy(() -> open().get("towers", "nothing")).isInstanceOf(NotFoundException.class);
            assertThat(open().find("towers", "nothing")).isEmpty();
        }

        @Test
        void listsACollectionInIdOrder() {
            Repository repository = open();
            repository.put("towers", "c", doc("{}"));
            repository.put("towers", "a", doc("{}"));
            repository.put("towers", "b", doc("{}"));
            assertThat(repository.all("towers"))
                    .extracting(Repository.Entry::id)
                    .containsExactly("a", "b", "c");
        }

        @Test
        void refusesADocumentItCannotFile() {
            Repository repository = open();
            assertThatThrownBy(() -> repository.put("", "a", doc("{}"))).hasMessageContaining("a collection name");
            assertThatThrownBy(() -> repository.put("towers", "", doc("{}"))).hasMessageContaining("a document id");
            assertThatThrownBy(() -> repository.put("towers", "a", null)).hasMessageContaining("no document to file");
        }
    }

    @Nested
    class Deleting {

        @Test
        void takesADocumentOut() {
            Repository repository = open();
            repository.put("towers", "a", doc("{}"));
            assertThat(repository.delete("towers", "a")).isTrue();
            assertThat(repository.has("towers", "a")).isFalse();
        }

        @Test
        void saysSoWhenThereWasNothingToDelete() {
            assertThat(open().delete("towers", "a")).isFalse();
        }

        @Test
        void writesNothingToTheJournalForADeleteThatDoesNothing() {
            open().delete("towers", "a");
            assertThat(journal.count()).isZero();
        }
    }

    @Nested
    class RebuildingFromTheJournal {

        @Test
        void givesTheSameStateWhenReopened() {
            Repository first = open();
            first.put("towers", "a", doc("{'n':1}"));
            first.put("towers", "b", doc("{'n':2}"));
            first.delete("towers", "a");
            Repository second = open();
            assertThat(second.count("towers")).isEqualTo(1);
            assertThat(second.get("towers", "b")).isEqualTo(doc("{'n':2}"));
        }

        @Test
        void reportsHowMuchItReplayed() {
            Repository first = open();
            first.put("towers", "a", doc("{}"));
            first.put("towers", "b", doc("{}"));
            assertThat(open().load()).isEqualTo(new Repository.Loaded(false, 2));
        }

        @Test
        void refusesAJournalHoldingSomethingThatIsNotAChange() {
            journal.append(doc("{'nonsense':true}"));
            assertThatThrownBy(RepositoryTest.this::open).isInstanceOf(StorageException.class);
            assertThatThrownBy(RepositoryTest.this::open).hasMessageContaining("does not say what it changes");
        }

        @Test
        void refusesAnOperationItCannotApply() {
            journal.append(doc("{'op':'merge','collection':'towers','id':'a'}"));
            assertThatThrownBy(RepositoryTest.this::open)
                    .hasMessageContaining("has an operation this cannot apply: merge");
        }

        @Test
        void refusesARecordThatIsNotAnObject() {
            journal.append(IntNode.valueOf(42));
            assertThatThrownBy(RepositoryTest.this::open).hasMessageContaining("is not a change");
        }
    }

    @Nested
    class Snapshots {

        @Test
        void writesOneAndLoadsFromIt() {
            Repository first = openWithSnapshot();
            first.put("towers", "a", doc("{'n':1}"));
            assertThat(first.save()).isTrue();
            Repository second = openWithSnapshot();
            assertThat(second.get("towers", "a")).isEqualTo(doc("{'n':1}"));
        }

        @Test
        void replaysOnlyWhatCameAfterTheSnapshot() {
            Repository first = openWithSnapshot();
            first.put("towers", "a", doc("{'n':1}"));
            first.save();
            first.put("towers", "b", doc("{'n':2}"));
            Repository second = openWithSnapshot();
            assertThat(second.load().fromSnapshot()).isTrue();
            assertThat(second.count("towers")).isEqualTo(2);
        }

        @Test
        void doesNothingWhenThereIsNoSnapshotToWrite() {
            assertThat(open().save()).isFalse();
        }

        @Test
        void refusesADocumentIdThatIsNotAString() {
            assertThatThrownBy(() -> open().put("towers", null, doc("{}")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> open().put("towers", null, doc("{}")))
                    .hasMessageContaining("a document id must be a non empty string");
        }
    }
}
