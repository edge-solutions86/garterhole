package io.github.rkaushikdev.garterhole.store;

import static io.github.rkaushikdev.garterhole.store.Documents.doc;
import static io.github.rkaushikdev.garterhole.store.Documents.docs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.garterhole.StorageException;
import io.github.rkaushikdev.garterhole.ValidationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JournalTest {

    @TempDir
    Path directory;

    private Journal journal;

    @BeforeEach
    void open() {
        journal = new Journal(directory.resolve("records.jsonl"));
    }

    @Nested
    class ANewJournal {

        @Test
        void isNotThereUntilSomethingIsWritten() {
            assertThat(journal.exists()).isFalse();
            assertThat(journal.bytes()).isZero();
            assertThat(journal.read()).isEmpty();
            assertThat(journal.count()).isZero();
        }

        @Test
        void refusesAPathThatIsNotOne() {
            assertThatThrownBy(() -> new Journal("")).hasMessageContaining("needs a path");
            assertThatThrownBy(() -> new Journal((String) null)).isInstanceOf(ValidationException.class);
        }

        @Test
        void makesTheDirectoryItNeeds() {
            Journal nested =
                    new Journal(directory.resolve("deep").resolve("down").resolve("records.jsonl"));
            nested.append(doc("{'a':1}"));
            assertThat(nested.read()).containsExactly(doc("{'a':1}"));
        }
    }

    @Nested
    class Appending {

        @Test
        void writesARecordAndReadsItBack() {
            journal.append(doc("{'kind':'tower','name':'St Mary'}"));
            assertThat(journal.read()).containsExactly(doc("{'kind':'tower','name':'St Mary'}"));
        }

        @Test
        void keepsTheOrderRecordsWereWrittenIn() {
            journal.append(doc("{'n':1}")).append(doc("{'n':2}")).append(doc("{'n':3}"));
            assertThat(journal.read())
                    .extracting(record -> record.path("n").intValue())
                    .containsExactly(1, 2, 3);
        }

        @Test
        void writesSeveralInOneGo() {
            assertThat(journal.appendAll(docs("{'n':1}", "{'n':2}"))).isEqualTo(2);
            assertThat(journal.count()).isEqualTo(2);
        }

        @Test
        void writesNothingForAnEmptyBatch() {
            assertThat(journal.appendAll(List.of())).isZero();
            assertThat(journal.exists()).isFalse();
        }

        @Test
        void growsTheFile() {
            journal.append(doc("{'n':1}"));
            long first = journal.bytes();
            journal.append(doc("{'n':2}"));
            assertThat(journal.bytes()).isGreaterThan(first);
        }
    }

    @Nested
    class Replaying {

        @BeforeEach
        void fill() {
            journal.appendAll(
                    docs("{'kind':'add','n':3}", "{'kind':'add','n':4}", "{'kind':'reset'}", "{'kind':'add','n':10}"));
        }

        @Test
        void foldsTheRecordsIntoAState() {
            int total = journal.replay(
                    (state, record, index) -> record.path("kind").asText().equals("reset")
                            ? 0
                            : state + record.path("n").intValue(),
                    0);
            assertThat(total).isEqualTo(10);
        }

        @Test
        void passesTheIndexOfEachRecord() {
            List<Integer> indexes = journal.replay(
                    (state, record, index) -> {
                        List<Integer> next = new ArrayList<>(state);
                        next.add(index);
                        return next;
                    },
                    List.of());
            assertThat(indexes).containsExactly(0, 1, 2, 3);
        }

        @Test
        void givesTheInitialStateBackForAnEmptyJournal() {
            Journal empty = new Journal(directory.resolve("empty.jsonl"));
            assertThat(empty.<Integer>replay((state, record, index) -> state + 1, 7))
                    .isEqualTo(7);
        }

        @Test
        void refusesToFoldWithoutAFunction() {
            assertThatThrownBy(() -> journal.replay(null, 0)).hasMessageContaining("a function to fold with");
        }
    }

    @Nested
    class DamagedJournals {

        @Test
        void reportsALineThatHasBeenChanged() throws IOException {
            journal.append(doc("{'n':1}"));
            String text = Files.readString(journal.path(), StandardCharsets.UTF_8);
            Files.writeString(journal.path(), text + "00000000 {\"n\":2}\n", StandardCharsets.UTF_8);
            assertThatThrownBy(journal::read).isInstanceOf(StorageException.class);
            assertThatThrownBy(journal::read).hasMessageContaining("line 2 is damaged");
        }

        @Test
        void reportsALineWithNoChecksum() throws IOException {
            Files.writeString(journal.path(), "{\"n\":1}\n", StandardCharsets.UTF_8);
            assertThatThrownBy(journal::read).hasMessageContaining("does not start with a checksum");
        }
    }

    @Nested
    class Rewriting {

        @Test
        void replacesEverything() {
            journal.appendAll(docs("{'n':1}", "{'n':2}"));
            assertThat(journal.rewrite(docs("{'n':9}"))).isEqualTo(1);
            assertThat(journal.read()).containsExactly(doc("{'n':9}"));
        }

        @Test
        void canEmptyTheJournal() {
            journal.append(doc("{'n':1}"));
            assertThat(journal.rewrite(List.<JsonNode>of())).isZero();
            assertThat(journal.read()).isEmpty();
            assertThat(journal.bytes()).isZero();
        }
    }
}
