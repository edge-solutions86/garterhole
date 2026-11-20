package io.github.rkaushikdev.garterhole.composition.music;

import static io.github.rkaushikdev.garterhole.composition.music.Runs.countRuns;
import static io.github.rkaushikdev.garterhole.composition.music.Runs.hasRunAtBack;
import static io.github.rkaushikdev.garterhole.composition.music.Runs.hasRunAtFront;
import static io.github.rkaushikdev.garterhole.composition.music.Runs.runLengthAtBack;
import static io.github.rkaushikdev.garterhole.composition.music.Runs.runLengthAtFront;
import static io.github.rkaushikdev.garterhole.composition.music.Runs.runTable;
import static io.github.rkaushikdev.garterhole.core.RowParse.parseRow;
import static io.github.rkaushikdev.garterhole.core.RowParse.parseRows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.composition.music.Runs.RunCount;
import io.github.rkaushikdev.garterhole.core.Row;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RunsTest {

    @Nested
    class RunLengthAtBack {

        @Test
        void findsAnAscendingRun() {
            assertThat(runLengthAtBack(parseRow("42135678"))).isEqualTo(4);
        }

        @Test
        void findsADescendingRun() {
            assertThat(runLengthAtBack(parseRow("24138765"))).isEqualTo(4);
        }

        @Test
        void findsTheWholeRowInRounds() {
            assertThat(runLengthAtBack(Row.rounds(8))).isEqualTo(8);
        }

        @Test
        void givesOneWhenTheLastTwoBellsAreNotNextToEachOther() {
            assertThat(runLengthAtBack(parseRow("13527468"))).isEqualTo(1);
        }

        @Test
        void refusesSomethingThatIsNotARow() {
            assertThatThrownBy(() -> runLengthAtBack(null)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class RunLengthAtFront {

        @Test
        void findsAnAscendingRun() {
            assertThat(runLengthAtFront(parseRow("12346857"))).isEqualTo(4);
        }

        @Test
        void findsADescendingRun() {
            assertThat(runLengthAtFront(parseRow("43216857"))).isEqualTo(4);
        }

        @Test
        void givesOneWhenTheFirstTwoBellsAreNotNextToEachOther() {
            assertThat(runLengthAtFront(parseRow("13527468"))).isEqualTo(1);
        }
    }

    @Nested
    class HasRunAtBackAndHasRunAtFront {

        @Test
        void acceptsARunAtLeastAsLongAsAskedFor() {
            assertThat(hasRunAtBack(parseRow("42135678"), 4)).isTrue();
            assertThat(hasRunAtBack(parseRow("42135678"), 3)).isTrue();
            assertThat(hasRunAtBack(parseRow("42135678"), 5)).isFalse();
        }

        @Test
        void looksAtTheRightEndOfTheRow() {
            assertThat(hasRunAtFront(parseRow("42135678"), 4)).isFalse();
            assertThat(hasRunAtFront(parseRow("12346857"), 4)).isTrue();
        }

        @Test
        void refusesALengthThatIsNotWorthCounting() {
            assertThatThrownBy(() -> hasRunAtBack(Row.rounds(8), 1)).hasMessageContaining("is not something to count");
            assertThatThrownBy(() -> hasRunAtBack(Row.rounds(8), 0)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class CountRuns {

        private final List<Row> rows = parseRows("42135678 12346857 13527468 87654321");

        @Test
        void countsTheTwoEndsSeparately() {
            assertThat(countRuns(rows, 4)).isEqualTo(new RunCount(2, 2, 3));
        }

        @Test
        void countsARowWithARunAtBothEndsOnceInTheTotal() {
            assertThat(countRuns(parseRows("12345678"), 4)).isEqualTo(new RunCount(1, 1, 1));
        }

        @Test
        void countsNothingWhenNothingQualifies() {
            assertThat(countRuns(parseRows("13527468"), 4)).isEqualTo(new RunCount(0, 0, 0));
        }

        @Test
        void countsNothingInAnEmptyBlock() {
            assertThat(countRuns(List.of(), 4)).isEqualTo(new RunCount(0, 0, 0));
        }
    }

    @Nested
    class RunTable {

        private final List<Row> rows = parseRows("42135678 12346857 13527468 87654321");

        @Test
        void hasALineForEveryLengthFromFourUp() {
            assertThat(runTable(rows, 8).keySet()).containsExactly(4, 5, 6, 7, 8);
        }

        @Test
        void countsFewerLongRunsThanShortOnes() {
            Map<Integer, RunCount> table = runTable(rows, 8);
            assertThat(table.get(4).total()).isGreaterThanOrEqualTo(table.get(5).total());
            assertThat(table.get(8).total()).isEqualTo(1);
        }

        @Test
        void startsWhereverItIsToldTo() {
            assertThat(runTable(rows, 8, 6).keySet()).containsExactly(6, 7, 8);
        }
    }
}
