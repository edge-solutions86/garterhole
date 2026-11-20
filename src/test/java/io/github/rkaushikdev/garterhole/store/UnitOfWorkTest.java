package io.github.rkaushikdev.garterhole.store;

import static io.github.rkaushikdev.garterhole.store.Documents.doc;
import static io.github.rkaushikdev.garterhole.store.Documents.docs;
import static io.github.rkaushikdev.garterhole.store.UnitOfWork.inUnitOfWork;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UnitOfWorkTest {

    @TempDir
    Path directory;

    private Journal journal;
    private Repository repository;

    @BeforeEach
    void open() {
        journal = new Journal(directory.resolve("records.jsonl"));
        repository = new Repository(journal);
    }

    @Nested
    class CollectingChanges {

        @Test
        void writesNothingUntilItIsCommitted() {
            UnitOfWork unit = new UnitOfWork(repository);
            unit.put("towers", "a", doc("{'n':1}"));
            assertThat(journal.count()).isZero();
            assertThat(repository.has("towers", "a")).isFalse();
        }

        @Test
        void countsWhatIsWaiting() {
            UnitOfWork unit = new UnitOfWork(repository);
            unit.put("towers", "a", doc("{}")).delete("towers", "b");
            assertThat(unit.size()).isEqualTo(2);
        }

        @Test
        void landsEverythingInOneAppend() {
            UnitOfWork unit = new UnitOfWork(repository);
            unit.put("towers", "a", doc("{'n':1}")).put("towers", "b", doc("{'n':2}"));
            assertThat(unit.commit()).isEqualTo(2);
            assertThat(journal.count()).isEqualTo(2);
            assertThat(repository.count("towers")).isEqualTo(2);
        }

        @Test
        void commitsNothingWhenThereIsNothingToCommit() {
            assertThat(new UnitOfWork(repository).commit()).isZero();
            assertThat(journal.count()).isZero();
        }

        @Test
        void refusesAChangeItCannotUse() {
            UnitOfWork unit = new UnitOfWork(repository);
            assertThatThrownBy(() -> unit.put("", "a", doc("{}"))).hasMessageContaining("a collection name");
            assertThatThrownBy(() -> unit.put("towers", "a", null)).hasMessageContaining("no document to file");
            assertThatThrownBy(() -> unit.delete("towers", "")).hasMessageContaining("a document id");
        }

        @Test
        void refusesToBeBuiltOnSomethingThatIsNotARepository() {
            assertThatThrownBy(() -> new UnitOfWork(null)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class ReadingInsideAUnit {

        @Test
        void seesADocumentItHasFiled() {
            UnitOfWork unit = new UnitOfWork(repository);
            unit.put("towers", "a", doc("{'n':1}"));
            assertThat(unit.find("towers", "a")).hasValue(doc("{'n':1}"));
            assertThat(unit.has("towers", "a")).isTrue();
        }

        @Test
        void seesTheLatestOfSeveralChangesToOneDocument() {
            UnitOfWork unit = new UnitOfWork(repository);
            unit.put("towers", "a", doc("{'n':1}")).put("towers", "a", doc("{'n':2}"));
            assertThat(unit.find("towers", "a")).hasValue(doc("{'n':2}"));
        }

        @Test
        void seesADocumentItHasDeletedAsGone() {
            repository.put("towers", "a", doc("{'n':1}"));
            UnitOfWork unit = new UnitOfWork(repository);
            unit.delete("towers", "a");
            assertThat(unit.has("towers", "a")).isFalse();
        }

        @Test
        void fallsThroughToWhatIsAlreadyFiled() {
            repository.put("towers", "a", doc("{'n':1}"));
            assertThat(new UnitOfWork(repository).find("towers", "a")).hasValue(doc("{'n':1}"));
        }
    }

    @Nested
    class FinishingAUnit {

        @Test
        void cannotBeUsedTwice() {
            UnitOfWork unit = new UnitOfWork(repository);
            unit.commit();
            assertThatThrownBy(() -> unit.put("towers", "a", doc("{}"))).hasMessageContaining("already been finished");
            assertThatThrownBy(unit::commit).isInstanceOf(ValidationException.class);
        }

        @Test
        void throwsTheChangesAwayOnARollback() {
            UnitOfWork unit = new UnitOfWork(repository);
            unit.put("towers", "a", doc("{}")).put("towers", "b", doc("{}"));
            assertThat(unit.rollback()).isEqualTo(2);
            assertThat(journal.count()).isZero();
        }
    }

    @Nested
    class InUnitOfWork {

        @Test
        void commitsWhenTheWorkReturns() {
            String answer = inUnitOfWork(repository, unit -> {
                unit.put("towers", "a", doc("{'n':1}"));
                return "done";
            });
            assertThat(answer).isEqualTo("done");
            assertThat(repository.has("towers", "a")).isTrue();
        }

        @Test
        void rollsBackWhenTheWorkThrows() {
            assertThatThrownBy(() -> inUnitOfWork(repository, unit -> {
                        unit.put("towers", "a", doc("{'n':1}"));
                        throw new IllegalStateException("no");
                    }))
                    .hasMessage("no");
            assertThat(journal.count()).isZero();
            assertThat(repository.has("towers", "a")).isFalse();
        }

        @Test
        void refusesToBeGivenSomethingThatIsNotWork() {
            assertThatThrownBy(() -> inUnitOfWork(repository, null)).hasMessageContaining("something to do");
        }
    }

    @Nested
    class TheBatchTheRepositoryWrites {

        @Test
        void refusesABatchThatIsNotAnArray() {
            assertThatThrownBy(() -> repository.applyBatch(null)).hasMessageContaining("must be an array");
        }

        @Test
        void writesNothingForAnEmptyBatch() {
            assertThat(repository.applyBatch(List.of())).isZero();
            assertThat(journal.count()).isZero();
        }

        @Test
        void refusesTheWholeBatchWhenOneChangeIsBad() {
            assertThatThrownBy(() -> repository.applyBatch(docs(
                            "{'op':'put','collection':'towers','id':'a','document':{}}",
                            "{'op':'merge','collection':'towers','id':'b'}")))
                    .hasMessageContaining("cannot apply: merge");
            assertThat(journal.count()).isZero();
        }
    }
}
