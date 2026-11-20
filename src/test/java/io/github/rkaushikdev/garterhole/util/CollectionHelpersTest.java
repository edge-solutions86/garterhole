package io.github.rkaushikdev.garterhole.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.util.CollectionHelpers.Pair;
import io.github.rkaushikdev.garterhole.util.CollectionHelpers.Partition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CollectionHelpersTest {

    record Keyed(String id) {}

    record Ranked(String id, int n) {}

    record Entry(int stage, String name) {}

    @Nested
    class GroupBy {

        @Test
        void collectsItemsUnderTheirKey() {
            Map<Character, List<String>> groups =
                    CollectionHelpers.groupBy(List.of("aa", "ab", "ba"), word -> word.charAt(0));
            assertThat(groups.get('a')).containsExactly("aa", "ab");
            assertThat(groups.get('b')).containsExactly("ba");
        }

        @Test
        void keepsTheOrderItemsArrivedIn() {
            Map<Integer, List<Integer>> groups = CollectionHelpers.groupBy(List.of(3, 1, 4, 1, 5), n -> n % 2);
            assertThat(groups.get(1)).containsExactly(3, 1, 1, 5);
        }

        @Test
        void givesAnEmptyMapForNothing() {
            assertThat(CollectionHelpers.groupBy(List.<String>of(), x -> x)).isEmpty();
        }
    }

    @Nested
    class CountBy {

        @Test
        void countsItemsUnderTheirKey() {
            Map<String, Integer> counts =
                    CollectionHelpers.countBy(List.of("bob", "single", "bob", "plain"), call -> call);
            assertThat(counts.get("bob")).isEqualTo(2);
            assertThat(counts.get("plain")).isEqualTo(1);
            assertThat(counts.get("extreme")).isNull();
        }
    }

    @Nested
    class Chunk {

        @Test
        void breaksAListIntoRuns() {
            assertThat(CollectionHelpers.chunk(List.of(1, 2, 3, 4, 5, 6), 2))
                    .containsExactly(List.of(1, 2), List.of(3, 4), List.of(5, 6));
        }

        @Test
        void leavesAShortRunAtTheEnd() {
            assertThat(CollectionHelpers.chunk(List.of(1, 2, 3, 4, 5), 2))
                    .containsExactly(List.of(1, 2), List.of(3, 4), List.of(5));
        }

        @Test
        void givesNothingForAnEmptyList() {
            assertThat(CollectionHelpers.chunk(List.of(), 3)).isEmpty();
        }

        @Test
        void refusesASizeThatIsNotAPositiveWholeNumber() {
            assertThatThrownBy(() -> CollectionHelpers.chunk(List.of(1), 0))
                    .hasMessageContaining("must be a positive whole number");
            assertThatThrownBy(() -> CollectionHelpers.chunk(List.of(1), -2))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Unique {

        @Test
        void keepsTheFirstOfEachRepeat() {
            assertThat(CollectionHelpers.unique(List.of(1, 2, 1, 3, 2))).containsExactly(1, 2, 3);
        }

        @Test
        void usesAKeyFunctionWhenOneIsGiven() {
            List<Keyed> rows = List.of(new Keyed("a"), new Keyed("b"), new Keyed("a"));
            assertThat(CollectionHelpers.unique(rows, Keyed::id)).hasSize(2);
        }

        @Test
        void doesNotDisturbTheInput() {
            List<Integer> input = new ArrayList<>(List.of(2, 1, 2));
            CollectionHelpers.unique(input);
            assertThat(input).containsExactly(2, 1, 2);
        }
    }

    @Nested
    class SortBy {

        @Test
        void sortsByOneKey() {
            assertThat(CollectionHelpers.sortBy(List.of(3, 1, 2), Comparator.naturalOrder()))
                    .containsExactly(1, 2, 3);
        }

        @Test
        void fallsBackToTheNextKeyOnATie() {
            List<Entry> items = List.of(new Entry(8, "Yorkshire"), new Entry(6, "Cambridge"), new Entry(8, "Bristol"));
            List<Entry> sorted = CollectionHelpers.sortBy(
                    items, Comparator.comparingInt(Entry::stage).thenComparing(Entry::name));
            assertThat(sorted).extracting(Entry::name).containsExactly("Cambridge", "Bristol", "Yorkshire");
        }

        @Test
        void leavesTheInputAlone() {
            List<Integer> input = new ArrayList<>(List.of(3, 1, 2));
            CollectionHelpers.sortBy(input, Comparator.naturalOrder());
            assertThat(input).containsExactly(3, 1, 2);
        }
    }

    @Nested
    class PartitionTest {

        @Test
        void splitsByTheTest() {
            assertThat(CollectionHelpers.partition(List.of(1, 2, 3, 4), n -> n % 2 == 0))
                    .isEqualTo(new Partition<>(List.of(2, 4), List.of(1, 3)));
        }

        @Test
        void givesTwoEmptyListsForNothing() {
            assertThat(CollectionHelpers.partition(List.of(), item -> true))
                    .isEqualTo(new Partition<>(List.of(), List.of()));
        }
    }

    @Nested
    class Zip {

        @Test
        void pairsItemsUp() {
            assertThat(CollectionHelpers.zip(List.of(1, 2), List.of("a", "b")))
                    .containsExactly(new Pair<>(1, "a"), new Pair<>(2, "b"));
        }

        @Test
        void stopsAtTheShorterList() {
            assertThat(CollectionHelpers.zip(List.of(1, 2, 3), List.of("a"))).containsExactly(new Pair<>(1, "a"));
            assertThat(CollectionHelpers.zip(List.of(), List.of("a"))).isEmpty();
        }
    }

    @Nested
    class SumAndMean {

        @Test
        void addsNumbersUp() {
            assertThat(CollectionHelpers.sum(List.of(1, 2, 3))).isEqualTo(6.0);
            assertThat(CollectionHelpers.sum(List.of())).isZero();
        }

        @Test
        void takesTheMean() {
            assertThat(CollectionHelpers.mean(List.of(2, 4, 6))).hasValue(4.0);
        }

        @Test
        void givesNothingRatherThanNaNForAnEmptyMean() {
            assertThat(CollectionHelpers.mean(List.of())).isEmpty();
        }
    }

    @Nested
    class MaxBy {

        @Test
        void findsTheHighestScore() {
            List<Ranked> items = List.of(new Ranked("a", 1), new Ranked("b", 5), new Ranked("c", 3));
            assertThat(CollectionHelpers.maxBy(items, Ranked::n)).hasValue(new Ranked("b", 5));
        }

        @Test
        void breaksTiesInFavourOfTheFirst() {
            Ranked first = new Ranked("a", 5);
            assertThat(CollectionHelpers.maxBy(List.of(first, new Ranked("b", 5)), Ranked::n))
                    .get()
                    .isSameAs(first);
        }

        @Test
        void givesNothingForAnEmptyList() {
            assertThat(CollectionHelpers.maxBy(List.<Ranked>of(), item -> 1)).isEmpty();
        }
    }
}
