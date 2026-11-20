package io.github.rkaushikdev.garterhole.core;

import static io.github.rkaushikdev.garterhole.core.RowParse.parseRow;
import static io.github.rkaushikdev.garterhole.core.Transposition.cyclicGroup;
import static io.github.rkaushikdev.garterhole.core.Transposition.inverse;
import static io.github.rkaushikdev.garterhole.core.Transposition.order;
import static io.github.rkaushikdev.garterhole.core.Transposition.permutationBetween;
import static io.github.rkaushikdev.garterhole.core.Transposition.power;
import static io.github.rkaushikdev.garterhole.core.Transposition.transpose;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TranspositionTest {

    @Nested
    class Transpose {

        @Test
        void leavesARowAloneWhenTransposedByRounds() {
            Row row = parseRow("13527486");
            assertThat(transpose(row, Row.rounds(8))).hasToString("13527486");
        }

        @Test
        void reordersTheLeftRowByThePlacesTheRightRowNames() {
            assertThat(transpose(parseRow("13524"), parseRow("21345"))).hasToString("31524");
        }

        @Test
        void appliesAPlainBobLeadHeadToItself() {
            Row leadHead = parseRow("13527486");
            assertThat(transpose(leadHead, leadHead)).hasToString("15738264");
        }

        @Test
        void refusesRowsAtDifferentStages() {
            assertThatThrownBy(() -> transpose(Row.rounds(6), Row.rounds(8)))
                    .hasMessage("cannot combine a row of 6 with a row of 8");
        }

        @Test
        void refusesSomethingThatIsNotARow() {
            assertThatThrownBy(() -> transpose(null, Row.rounds(6))).hasMessageContaining("must be a Row");
            assertThatThrownBy(() -> transpose(Row.rounds(6), null)).hasMessageContaining("must be a Row");
        }
    }

    @Nested
    class Inverse {

        @Test
        void undoesARowFromEitherSide() {
            Row row = parseRow("13527486");
            assertThat(transpose(row, inverse(row)).isRounds()).isTrue();
            assertThat(transpose(inverse(row), row).isRounds()).isTrue();
        }

        @Test
        void leavesRoundsAlone() {
            assertThat(inverse(Row.rounds(8))).hasToString("12345678");
        }

        @Test
        void isItsOwnInverse() {
            Row row = parseRow("42135");
            assertThat(inverse(inverse(row))).isEqualTo(row);
        }

        @Test
        void invertsAKnownRow() {
            assertThat(inverse(parseRow("31425"))).hasToString("24135");
        }
    }

    @Nested
    class PermutationBetween {

        @Test
        void findsTheRowThatTakesOneRowToAnother() {
            Row from = parseRow("13527486");
            Row to = parseRow("17583624");
            Row step = permutationBetween(from, to);
            assertThat(transpose(from, step)).isEqualTo(to);
        }

        @Test
        void givesRoundsWhenTheRowsAreTheSame() {
            Row row = parseRow("654321");
            assertThat(permutationBetween(row, row).isRounds()).isTrue();
        }
    }

    @Nested
    class Power {

        @Test
        void givesRoundsForExponentZero() {
            assertThat(power(parseRow("13527486"), 0).isRounds()).isTrue();
        }

        @Test
        void givesTheRowItselfForExponentOne() {
            Row row = parseRow("13527486");
            assertThat(power(row, 1)).isEqualTo(row);
        }

        @Test
        void agreesWithRepeatedTransposition() {
            Row row = parseRow("134256");
            Row expected = Row.rounds(6);
            for (int step = 0; step < 5; step += 1) {
                expected = transpose(expected, row);
            }
            assertThat(power(row, 5)).isEqualTo(expected);
        }

        @Test
        void invertsForANegativeExponent() {
            Row row = parseRow("134256");
            assertThat(power(row, -1)).isEqualTo(inverse(row));
            assertThat(transpose(power(row, 3), power(row, -3)).isRounds()).isTrue();
        }

        @Test
        void refusesAFractionalExponent() throws NoSuchMethodException {
            // A fractional exponent cannot be written: the exponent is declared a whole number.
            assertThat(Transposition.class.getMethod("power", Row.class, int.class)
                            .getParameterTypes()[1])
                    .isEqualTo(int.class);
        }
    }

    @Nested
    class Order {

        @Test
        void saysRoundsHasOrderOne() {
            assertThat(order(Row.rounds(8))).isEqualTo(1);
        }

        @Test
        void saysASingleSwapHasOrderTwo() {
            assertThat(order(new Row(2, 1, 3, 4))).isEqualTo(2);
        }

        @Test
        void saysAThreeCycleHasOrderThree() {
            assertThat(order(parseRow("23145"))).isEqualTo(3);
        }

        @Test
        void takesTheLeastCommonMultipleOfTheCycleLengths() {
            assertThat(order(parseRow("214536"))).isEqualTo(6);
        }

        @Test
        void givesTheNumberOfLeadsInAPlainBobMajorCourse() {
            assertThat(order(parseRow("13527486"))).isEqualTo(7);
        }

        @Test
        void agreesWithPower() {
            Row row = parseRow("13572468");
            assertThat(power(row, order(row)).isRounds()).isTrue();
        }
    }

    @Nested
    class CyclicGroup {

        @Test
        void startsAtRoundsAndHasOneMemberPerOrder() {
            Row row = parseRow("13527486");
            List<Row> group = cyclicGroup(row);
            assertThat(group).hasSize(order(row));
            assertThat(group.get(0).isRounds()).isTrue();
        }

        @Test
        void hasNoRepeats() {
            List<Row> group = cyclicGroup(parseRow("214365"));
            assertThat(new HashSet<>(group)).hasSameSizeAs(group);
            assertThat(group).hasSize(2);
        }
    }
}
