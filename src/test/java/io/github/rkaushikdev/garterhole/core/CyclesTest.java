package io.github.rkaushikdev.garterhole.core;

import static io.github.rkaushikdev.garterhole.core.Cycles.cycleLengths;
import static io.github.rkaushikdev.garterhole.core.Cycles.cycles;
import static io.github.rkaushikdev.garterhole.core.Cycles.fixedBells;
import static io.github.rkaushikdev.garterhole.core.Cycles.isInCourse;
import static io.github.rkaushikdev.garterhole.core.Cycles.orderOf;
import static io.github.rkaushikdev.garterhole.core.Cycles.parity;
import static io.github.rkaushikdev.garterhole.core.Cycles.writeCycles;
import static io.github.rkaushikdev.garterhole.core.RowParse.parseRow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.core.Cycles.Parity;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CyclesTest {

    @Nested
    class CyclesOfARow {

        @Test
        void givesEveryBellItsOwnCycleInRounds() {
            assertThat(cycles(Row.rounds(4))).containsExactly(List.of(1), List.of(2), List.of(3), List.of(4));
        }

        @Test
        void findsAThreeCycle() {
            assertThat(cycles(parseRow("23145"))).containsExactly(List.of(1, 2, 3), List.of(4), List.of(5));
        }

        @Test
        void findsTwoCyclesAtOnce() {
            assertThat(cycles(parseRow("214536"))).containsExactly(List.of(1, 2), List.of(3, 4, 5), List.of(6));
        }

        @Test
        void startsEachCycleAtItsLowestBell() {
            for (List<Integer> cycle : cycles(parseRow("13527486"))) {
                assertThat(cycle.get(0)).isEqualTo(Collections.min(cycle));
            }
        }

        @Test
        void findsTheSevenCycleInAPlainBobMajorLeadHead() {
            assertThat(cycles(parseRow("13527486"))).containsExactly(List.of(1), List.of(2, 3, 5, 7, 8, 6, 4));
        }

        @Test
        void refusesSomethingThatIsNotARow() {
            assertThatThrownBy(() -> cycles(null)).hasMessageContaining("a row is needed");
        }
    }

    @Nested
    class CycleLengths {

        @Test
        void sortsLongestFirst() {
            assertThat(cycleLengths(parseRow("214536"))).containsExactly(3, 2, 1);
        }

        @Test
        void addsUpToTheStage() {
            Row row = parseRow("1234567890ET");
            assertThat(cycleLengths(row).stream().mapToInt(Integer::intValue).sum())
                    .isEqualTo(12);
        }
    }

    @Nested
    class OrderOf {

        @Test
        void saysRoundsHasOrderOne() {
            assertThat(orderOf(Row.rounds(8))).isEqualTo(1);
        }

        @Test
        void takesTheLeastCommonMultipleOfTheCycleLengths() {
            assertThat(orderOf(parseRow("214536"))).isEqualTo(6);
            assertThat(orderOf(parseRow("13527486"))).isEqualTo(7);
        }

        @Test
        void agreesWithWalkingTheGroup() {
            for (String text : List.of("13527486", "214365", "23145", "1234567890ET", "654321")) {
                Row row = parseRow(text);
                assertThat(orderOf(row)).isEqualTo(Transposition.order(row));
            }
        }
    }

    @Nested
    class ParityOfARow {

        @Test
        void callsRoundsEven() {
            assertThat(parity(Row.rounds(6))).isEqualTo(Parity.EVEN);
            assertThat(parity(Row.rounds(6))).hasToString("even");
            assertThat(isInCourse(Row.rounds(6))).isTrue();
        }

        @Test
        void callsASingleSwapOdd() {
            assertThat(parity(parseRow("214365"))).isEqualTo(Parity.ODD);
            assertThat(parity(parseRow("213456"))).isEqualTo(Parity.ODD);
        }

        @Test
        void callsAThreeCycleEven() {
            assertThat(parity(parseRow("23145"))).isEqualTo(Parity.EVEN);
        }

        @Test
        void followsTheNumberOfPairsEachChangeSwapsRatherThanAlternating() {
            List<Parity> seen = List.of("214365", "241635", "426153", "462513").stream()
                    .map(text -> parity(parseRow(text)))
                    .toList();
            assertThat(seen).containsExactly(Parity.ODD, Parity.ODD, Parity.EVEN, Parity.EVEN);
        }

        @Test
        void splitsTheCourseHeadsIntoAnInCourseHalfAndAnOutOfCourseHalf() {
            assertThat(isInCourse(parseRow("13527486"))).isTrue();
            assertThat(isInCourse(parseRow("13526487"))).isFalse();
        }
    }

    @Nested
    class FixedBells {

        @Test
        void listsEveryBellInRounds() {
            assertThat(fixedBells(Row.rounds(5))).containsExactly(1, 2, 3, 4, 5);
        }

        @Test
        void listsTheTrebleInAPlainBobLeadHead() {
            assertThat(fixedBells(parseRow("13527486"))).containsExactly(1);
        }

        @Test
        void listsNothingWhenEveryBellMoves() {
            assertThat(fixedBells(parseRow("214365"))).isEmpty();
        }
    }

    @Nested
    class WriteCycles {

        @Test
        void leavesOutTheBellsThatDoNotMove() {
            assertThat(writeCycles(parseRow("214536"))).isEqualTo("(12)(345)");
        }

        @Test
        void writesAnEmptyPairOfBracketsForRounds() {
            assertThat(writeCycles(Row.rounds(8))).isEqualTo("()");
        }

        @Test
        void usesTheBellAlphabetAboveNine() {
            assertThat(writeCycles(parseRow("1234567890ET"))).isEqualTo("()");
        }
    }
}
