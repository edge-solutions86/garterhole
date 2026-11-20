package io.github.rkaushikdev.garterhole.store;

import static io.github.rkaushikdev.garterhole.store.Documents.doc;
import static io.github.rkaushikdev.garterhole.store.Documents.docs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import io.github.rkaushikdev.garterhole.StorageException;
import io.github.rkaushikdev.garterhole.ValidationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotTest {

    @TempDir
    Path directory;

    private Snapshot snapshot;
    private Journal journal;

    private static final Fold<JsonNode> ADD = (state, record, index) ->
            IntNode.valueOf(state.intValue() + record.path("n").intValue());

    private static final Supplier<JsonNode> ZERO = () -> IntNode.valueOf(0);

    @BeforeEach
    void open() {
        snapshot = new Snapshot(directory.resolve("state.json"));
        journal = new Journal(directory.resolve("records.jsonl"));
    }

    private void writeFile(String text) throws IOException {
        Files.writeString(snapshot.path(), text, StandardCharsets.UTF_8);
    }

    @Nested
    class SnapshotFile {

        @Test
        void isNotThereUntilSomethingIsWritten() {
            assertThat(snapshot.exists()).isFalse();
            assertThat(snapshot.read()).isEmpty();
        }

        @Test
        void writesAndReadsAStateBack() {
            snapshot.write(doc("{'total':7}"), 3);
            assertThat(snapshot.read()).hasValue(new Snapshot.Saved(doc("{'total':7}"), 3));
        }

        @Test
        void leavesNoHalfWrittenFileBehind() {
            snapshot.write(doc("{'total':7}"), 3);
            assertThat(snapshot.exists()).isTrue();
            assertThatCode(snapshot::read).doesNotThrowAnyException();
            assertThat(Files.exists(Path.of(snapshot.path() + ".writing"))).isFalse();
        }

        @Test
        void overwritesAnEarlierSnapshot() {
            snapshot.write(doc("{'total':1}"), 1);
            snapshot.write(doc("{'total':2}"), 2);
            assertThat(snapshot.read().orElseThrow().state().path("total").intValue())
                    .isEqualTo(2);
        }

        @Test
        void refusesAPathThatIsNotOne() {
            assertThatThrownBy(() -> new Snapshot("")).hasMessageContaining("needs a path");
        }

        @Test
        void refusesARecordCountThatIsNotOne() {
            assertThatThrownBy(() -> snapshot.write(doc("{}"), -1)).hasMessageContaining("is not a number of records");
            assertThatThrownBy(() -> snapshot.write(doc("{}"), Integer.MIN_VALUE))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void refusesAStateJsonCannotHold() {
            assertThatThrownBy(() -> snapshot.write(Documents.function(), 0))
                    .hasMessageContaining("cannot be written as JSON");
        }

        @Test
        void removesItself() {
            snapshot.write(doc("{'total':1}"), 1);
            assertThat(snapshot.remove()).isTrue();
            assertThat(snapshot.exists()).isFalse();
            assertThat(snapshot.remove()).isFalse();
        }
    }

    @Nested
    class DamagedSnapshots {

        @Test
        void reportsAFileThatIsNotASnapshot() throws IOException {
            writeFile("not json");
            assertThatThrownBy(snapshot::read).hasMessageContaining("cannot be read");
        }

        @Test
        void reportsAVersionItDoesNotKnow() throws IOException {
            writeFile("{\"version\":99,\"through\":0,\"checksum\":\"x\",\"state\":{}}");
            assertThatThrownBy(snapshot::read).hasMessageContaining("is version 99");
        }

        @Test
        void reportsASnapshotWithNoRecordCount() throws IOException {
            writeFile("{\"version\":" + Snapshot.SNAPSHOT_VERSION + ",\"checksum\":\"x\",\"state\":{}}");
            assertThatThrownBy(snapshot::read).hasMessageContaining("does not say how far it goes");
        }

        @Test
        void reportsAStateThatDoesNotMatchItsChecksum() throws IOException {
            writeFile("{\"version\":" + Snapshot.SNAPSHOT_VERSION
                    + ",\"through\":0,\"checksum\":\"00000000\",\"state\":{\"a\":1}}");
            assertThatThrownBy(snapshot::read).isInstanceOf(StorageException.class);
            assertThatThrownBy(snapshot::read).hasMessageContaining("is damaged");
        }

        @Test
        void reportsAFileHoldingSomethingThatIsNotAnObject() throws IOException {
            writeFile("42");
            assertThatThrownBy(snapshot::read).hasMessageContaining("is not a snapshot");
        }
    }

    @Nested
    class LoadState {

        @BeforeEach
        void fill() {
            journal.appendAll(docs("{'n':1}", "{'n':2}", "{'n':3}"));
        }

        @Test
        void replaysTheWholeJournalWhenThereIsNoSnapshot() {
            Snapshot.Loaded<JsonNode> loaded = Snapshot.loadState(journal, snapshot, ADD, ZERO);
            assertThat(loaded.state().intValue()).isEqualTo(6);
            assertThat(loaded.fromSnapshot()).isFalse();
            assertThat(loaded.replayed()).isEqualTo(3);
        }

        @Test
        void replaysOnlyWhatCameAfterASnapshot() {
            snapshot.write(IntNode.valueOf(3), 2);
            Snapshot.Loaded<JsonNode> loaded = Snapshot.loadState(journal, snapshot, ADD, ZERO);
            assertThat(loaded.state().intValue()).isEqualTo(6);
            assertThat(loaded.fromSnapshot()).isTrue();
            assertThat(loaded.replayed()).isEqualTo(1);
        }

        @Test
        void replaysNothingWhenTheSnapshotIsUpToDate() {
            snapshot.write(IntNode.valueOf(6), 3);
            assertThat(Snapshot.loadState(journal, snapshot, ADD, ZERO).replayed())
                    .isZero();
        }

        @Test
        void refusesASnapshotFromFurtherAheadThanTheJournal() {
            snapshot.write(IntNode.valueOf(6), 9);
            assertThatThrownBy(() -> Snapshot.loadState(journal, snapshot, ADD, ZERO))
                    .hasMessage("the snapshot claims 9 records but the journal holds 3");
        }

        @Test
        void refusesOptionsItCannotUse() {
            assertThatThrownBy(() -> Snapshot.loadState(null, null, ADD, ZERO))
                    .hasMessageContaining("needs a journal and a snapshot");
            assertThatThrownBy(() -> Snapshot.loadState(journal, snapshot, null, ZERO))
                    .hasMessageContaining("needs a fold and a starting point");
        }
    }
}
