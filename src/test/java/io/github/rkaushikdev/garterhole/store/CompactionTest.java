package io.github.rkaushikdev.garterhole.store;

import static io.github.rkaushikdev.garterhole.store.Compaction.compact;
import static io.github.rkaushikdev.garterhole.store.Compaction.compactIfWorthwhile;
import static io.github.rkaushikdev.garterhole.store.Compaction.compactionGain;
import static io.github.rkaushikdev.garterhole.store.Compaction.liveRecords;
import static io.github.rkaushikdev.garterhole.store.Compaction.worthCompacting;
import static io.github.rkaushikdev.garterhole.store.Documents.doc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.store.Compaction.Thresholds;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompactionTest {

    @TempDir
    Path directory;

    private Journal journal;
    private Snapshot snapshot;
    private Repository repository;

    @BeforeEach
    void open() {
        journal = new Journal(directory.resolve("records.jsonl"));
        snapshot = new Snapshot(directory.resolve("state.json"));
        repository = new Repository(journal, snapshot);
    }

    /** Write a document several times and delete another, so there is waste. */
    private void churn() {
        repository.put("towers", "a", doc("{'n':1}"));
        repository.put("towers", "a", doc("{'n':2}"));
        repository.put("towers", "a", doc("{'n':3}"));
        repository.put("towers", "b", doc("{'n':1}"));
        repository.delete("towers", "b");
    }

    private void rewriteOneDocument(int times) {
        for (int time = 0; time < times; time += 1) {
            repository.put("towers", "a", doc("{'n':" + time + "}"));
        }
    }

    @Nested
    class CompactionGain {

        @Test
        void countsNothingWastedInAFreshJournal() {
            repository.put("towers", "a", doc("{'n':1}"));
            assertThat(compactionGain(repository)).isEqualTo(new Compaction.Gain(1, 1, 0, 0));
        }

        @Test
        void countsTheRecordsNoLongerDoingAnyWork() {
            churn();
            Compaction.Gain gain = compactionGain(repository);
            assertThat(gain.records()).isEqualTo(5);
            assertThat(gain.live()).isEqualTo(1);
            assertThat(gain.wasted()).isEqualTo(4);
            assertThat(gain.ratio()).isCloseTo(0.8, within(1e-6));
        }

        @Test
        void handlesAnEmptyJournal() {
            assertThat(compactionGain(repository).ratio()).isZero();
        }

        @Test
        void refusesSomethingThatIsNotARepository() {
            assertThatThrownBy(() -> compactionGain(null)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class WorthCompacting {

        @Test
        void saysNoForASmallJournalHoweverWasteful() {
            churn();
            assertThat(worthCompacting(repository)).isFalse();
        }

        @Test
        void saysYesOnceTheJournalIsBigAndWasteful() {
            rewriteOneDocument(60);
            assertThat(worthCompacting(repository, Thresholds.DEFAULT.withMinimumRecords(10)))
                    .isTrue();
        }

        @Test
        void saysNoWhenTheJournalIsBigAndTidy() {
            for (int time = 0; time < 60; time += 1) {
                repository.put("towers", "t" + time, doc("{'n':" + time + "}"));
            }
            assertThat(worthCompacting(repository, Thresholds.DEFAULT.withMinimumRecords(10)))
                    .isFalse();
        }
    }

    @Nested
    class LiveRecords {

        @Test
        void givesOnePutPerLivingDocument() {
            churn();
            List<ObjectNode> records = liveRecords(repository);
            assertThat(records).hasSize(1);
            assertThat(records.get(0)).isEqualTo(doc("{'op':'put','collection':'towers','id':'a','document':{'n':3}}"));
        }

        @Test
        void walksTheCollectionsInOrder() {
            repository.put("methods", "m", doc("{}"));
            repository.put("towers", "t", doc("{}"));
            assertThat(liveRecords(repository))
                    .extracting(record -> record.path("collection").asText())
                    .containsExactly("methods", "towers");
        }
    }

    @Nested
    class Compact {

        @Test
        void shortensTheJournal() {
            churn();
            assertThat(compact(repository)).isEqualTo(new Compaction.Result(5, 1));
            assertThat(journal.count()).isEqualTo(1);
        }

        @Test
        void leavesTheStateExactlyAsItWas() {
            churn();
            repository.put("methods", "m", doc("{'title':'Plain Bob Minor'}"));
            compact(repository);
            Repository reopened = new Repository(journal);
            assertThat(reopened.get("towers", "a")).isEqualTo(doc("{'n':3}"));
            assertThat(reopened.get("methods", "m")).isEqualTo(doc("{'title':'Plain Bob Minor'}"));
            assertThat(reopened.has("towers", "b")).isFalse();
        }

        @Test
        void throwsTheSnapshotAwaySinceItCountsTheOldRecords() {
            churn();
            repository.save();
            assertThat(snapshot.exists()).isTrue();
            compact(repository);
            assertThat(snapshot.exists()).isFalse();
        }

        @Test
        void leavesTheRepositoryAbleToWriteAgain() {
            churn();
            compact(repository);
            repository.put("towers", "c", doc("{'n':9}"));
            assertThat(new Repository(journal).get("towers", "c")).isEqualTo(doc("{'n':9}"));
        }
    }

    @Nested
    class CompactIfWorthwhile {

        @Test
        void doesNothingToATidyJournal() {
            repository.put("towers", "a", doc("{'n':1}"));
            assertThat(compactIfWorthwhile(repository)).isEqualTo(new Compaction.Outcome(false, 1, 1));
        }

        @Test
        void compactsAWastefulOne() {
            rewriteOneDocument(60);
            Compaction.Outcome result = compactIfWorthwhile(repository, Thresholds.DEFAULT.withMinimumRecords(10));
            assertThat(result.compacted()).isTrue();
            assertThat(result.after()).isEqualTo(1);
        }
    }
}
