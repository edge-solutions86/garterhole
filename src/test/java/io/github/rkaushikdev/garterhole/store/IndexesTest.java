package io.github.rkaushikdev.garterhole.store;

import static io.github.rkaushikdev.garterhole.store.Documents.doc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IndexesTest {

    @TempDir
    Path directory;

    private Repository repository;

    @BeforeEach
    void fill() {
        repository = new Repository(new Journal(directory.resolve("records.jsonl")));
        repository.put("performances", "a", doc("{'tower':'St Mary','stage':6,'band':['Alice','Bob']}"));
        repository.put("performances", "b", doc("{'tower':'St Mary','stage':8,'band':['Alice']}"));
        repository.put("performances", "c", doc("{'tower':'All Saints','stage':6,'band':['Bob']}"));
    }

    private static Index byTower() {
        return Index.of("by tower", "performances", (document, id) -> List.of(document.path("tower")));
    }

    private static Index byRinger() {
        return Index.of("by ringer", "performances", (document, id) -> document.path("band"));
    }

    @Nested
    class IndexOverACollection {

        @Test
        void filesEachDocumentUnderItsKey() {
            Index index = byTower().build(repository);
            assertThat(index.lookup("St Mary")).containsExactly("a", "b");
            assertThat(index.lookup("All Saints")).containsExactly("c");
        }

        @Test
        void filesADocumentUnderSeveralKeys() {
            Index index = byRinger().build(repository);
            assertThat(index.lookup("Alice")).containsExactly("a", "b");
            assertThat(index.lookup("Bob")).containsExactly("a", "c");
        }

        @Test
        void givesNothingForAKeyItDoesNotHold() {
            assertThat(byTower().build(repository).lookup("Nowhere")).isEmpty();
        }

        @Test
        void turnsAKeyIntoAStringSoANumberWorksToo() {
            Index index = Index.of("by stage", "performances", (document, id) -> List.of(document.path("stage")))
                    .build(repository);
            assertThat(index.lookup(6)).containsExactly("a", "c");
            assertThat(index.lookup("6")).containsExactly("a", "c");
        }

        @Test
        void listsItsKeysAndCountsThem() {
            Index index = byTower().build(repository);
            assertThat(index.keys()).containsExactly("All Saints", "St Mary");
            assertThat(index.size()).isEqualTo(2);
            assertThat(index.counts())
                    .containsExactly(new Index.KeyCount("St Mary", 2), new Index.KeyCount("All Saints", 1));
        }

        @Test
        void rebuildsFromWhatTheRepositoryHoldsNow() {
            Index index = byTower().build(repository);
            repository.delete("performances", "b");
            index.build(repository);
            assertThat(index.lookup("St Mary")).containsExactly("a");
        }

        @Test
        void refusesADefinitionItCannotUse() {
            assertThatThrownBy(() -> new Index(null)).hasMessageContaining("needs a definition object");
            assertThatThrownBy(() -> new Index(new Index.Definition("", "a", (document, id) -> List.of())))
                    .hasMessageContaining("an index name");
            assertThatThrownBy(() -> new Index(new Index.Definition("a", "b", null)))
                    .hasMessageContaining("the key function");
        }

        @Test
        void refusesToBeBuiltFromSomethingThatIsNotARepository() {
            assertThatThrownBy(() -> byTower().build(null)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class IndexSetOverARepository {

        @Test
        void buildsEachIndexAsItIsAdded() {
            IndexSet set = new IndexSet(repository).add(byTower()).add(byRinger());
            assertThat(set.names()).containsExactly("by ringer", "by tower");
            assertThat(set.get("by tower").lookup("St Mary")).containsExactly("a", "b");
        }

        @Test
        void looksDocumentsUpThroughAnIndex() {
            IndexSet set = new IndexSet(repository).add(byTower());
            List<Repository.Entry> found = set.find("by tower", "St Mary");
            assertThat(found).hasSize(2);
            assertThat(found.get(0).document().path("tower").asText()).isEqualTo("St Mary");
        }

        @Test
        void rebuildsEveryIndexAtOnce() {
            IndexSet set = new IndexSet(repository).add(byTower()).add(byRinger());
            repository.put("performances", "d", doc("{'tower':'St Mary','stage':6,'band':['Carol']}"));
            set.rebuild();
            assertThat(set.get("by tower").lookup("St Mary")).hasSize(3);
            assertThat(set.get("by ringer").lookup("Carol")).containsExactly("d");
        }

        @Test
        void refusesAnIndexNobodyAdded() {
            assertThatThrownBy(() -> new IndexSet(repository).get("by nothing"))
                    .hasMessageContaining("there is no index called \"by nothing\"");
        }

        @Test
        void refusesAnythingThatIsNotAnIndex() {
            assertThatThrownBy(() -> new IndexSet(repository).add(null)).isInstanceOf(ValidationException.class);
        }

        @Test
        void refusesToBeBuiltOnSomethingThatIsNotARepository() {
            assertThatThrownBy(() -> new IndexSet(null)).hasMessageContaining("needs a Repository");
        }
    }
}
