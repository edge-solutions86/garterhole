package io.github.rkaushikdev.garterhole.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RowTest {

    @Nested
    class RowConstruction {

        @Test
        void keepsTheBellsInStrikingOrder() {
            Row row = new Row(2, 1, 4, 3);
            assertThat(row.bells()).containsExactly(2, 1, 4, 3);
            assertThat(row.stage()).isEqualTo(4);
        }

        @Test
        void copiesTheArrayItWasGiven() {
            int[] bells = {2, 1, 4, 3};
            Row row = new Row(bells);
            bells[0] = 9;
            assertThat(row.bells().get(0)).isEqualTo(2);
        }

        @Test
        void isFrozenOnceBuilt() {
            Row row = new Row(1, 2, 3, 4);
            assertThat(Modifier.isFinal(Row.class.getModifiers())).isTrue();
            assertThatThrownBy(() -> row.bells().set(0, 4)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void refusesAnEmptyRow() {
            assertThatThrownBy(Row::new).hasMessageContaining("a row must not be empty");
        }

        @Test
        void refusesARepeatedBell() {
            assertThatThrownBy(() -> new Row(1, 2, 2, 4)).hasMessageContaining("appears more than once");
        }

        @Test
        void refusesABellOutsideTheStage() {
            assertThatThrownBy(() -> new Row(1, 2, 3, 5)).hasMessageContaining("outside 1..4");
            assertThatThrownBy(() -> new Row(0, 2, 3, 4)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void refusesABellThatIsNotAWholeNumber() {
            assertThatThrownBy(() -> Row.of(Arrays.asList(1, 2, null, 4)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("bell null is outside 1..4");
        }
    }

    @Nested
    class RowLookup {

        private final Row row = new Row(3, 1, 4, 2);

        @Test
        void readsTheBellInAPlace() {
            assertThat(row.bellAt(1)).isEqualTo(3);
            assertThat(row.bellAt(4)).isEqualTo(2);
        }

        @Test
        void readsThePlaceOfABell() {
            assertThat(row.placeOf(3)).isEqualTo(1);
            assertThat(row.placeOf(2)).isEqualTo(4);
        }

        @Test
        void refusesAPlaceOutsideTheRow() {
            assertThatThrownBy(() -> row.bellAt(0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> row.bellAt(5)).hasMessageContaining("outside 1..4");
        }

        @Test
        void refusesABellThatIsNotRinging() {
            assertThatThrownBy(() -> row.placeOf(7)).hasMessageContaining("not in a row of 4");
        }

        @Test
        void agreesWithItselfInBothDirections() {
            for (int place = 1; place <= row.stage(); place += 1) {
                assertThat(row.placeOf(row.bellAt(place))).isEqualTo(place);
            }
        }
    }

    @Nested
    class Rounds {

        @Test
        void putsEveryBellInItsOwnPlace() {
            assertThat(Row.rounds(6)).hasToString("123456");
            assertThat(Row.rounds(12)).hasToString("1234567890ET");
        }

        @Test
        void isRecognisedAsRounds() {
            assertThat(Row.rounds(8).isRounds()).isTrue();
            assertThat(new Row(2, 1, 3, 4, 5, 6, 7, 8).isRounds()).isFalse();
        }

        @Test
        void refusesAnUnsupportedStage() {
            assertThatThrownBy(() -> Row.rounds(2)).hasMessageContaining("not supported");
            assertThatThrownBy(() -> Row.rounds(17)).hasMessageContaining("not supported");
        }
    }

    @Nested
    class RowEqualityAndPrinting {

        @Test
        void comparesByContent() {
            assertThat(new Row(2, 1, 3, 4)).isEqualTo(new Row(2, 1, 3, 4));
            assertThat(new Row(2, 1, 3, 4)).hasSameHashCodeAs(new Row(2, 1, 3, 4));
            assertThat(new Row(2, 1, 3, 4)).isNotEqualTo(new Row(1, 2, 3, 4));
        }

        @Test
        void isNotEqualToARowAtAnotherStage() {
            assertThat(Row.rounds(6)).isNotEqualTo(Row.rounds(8));
        }

        @Test
        void isNotEqualToSomethingThatIsNotARow() {
            assertThat(Row.rounds(6).equals("123456")).isFalse();
            assertThat(Row.rounds(6).equals(null)).isFalse();
        }

        @Test
        void printsWithTheBellAlphabet() {
            assertThat(new Row(12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1)).hasToString("TE0987654321");
        }

        @Test
        void usesThePrintedFormAsItsKey() {
            Row row = new Row(3, 1, 2);
            assertThat(row.key()).isEqualTo(row.toString());
        }

        @Test
        void handsOutAMutableCopyOfTheBells() {
            Row row = Row.rounds(4);
            int[] copy = row.toArray();
            copy[0] = 4;
            assertThat(row.bellAt(1)).isEqualTo(1);
        }
    }
}
