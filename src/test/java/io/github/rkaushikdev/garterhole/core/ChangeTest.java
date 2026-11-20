package io.github.rkaushikdev.garterhole.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.NotationException;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ChangeTest {

    @Nested
    class ChangeConstruction {

        @Test
        void sortsAndDeDuplicatesThePlacesItIsGiven() {
            Change change = Change.of(8, 8, 7, 1, 1, 2);
            assertThat(change.places()).containsExactly(1, 2, 7, 8);
        }

        @Test
        void isFrozen() {
            Change change = Change.of(6, 1, 6);
            assertThat(Modifier.isFinal(Change.class.getModifiers())).isTrue();
            assertThatThrownBy(() -> change.places().add(3)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void refusesAPlaceOutsideTheStage() {
            assertThatThrownBy(() -> Change.of(6, 7)).hasMessageContaining("place 7 is outside 1..6");
            assertThatThrownBy(() -> Change.of(6, 0)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void refusesAnUnsupportedStage() {
            assertThatThrownBy(() -> Change.of(2)).hasMessageContaining("stage 2 is not supported");
        }

        @Test
        void refusesPlacesThatLeaveAnOddGap() {
            assertThatThrownBy(() -> Change.of(6, 1)).isInstanceOf(NotationException.class);
            assertThatThrownBy(() -> Change.of(6, 1)).hasMessage("places 1 would leave 5 bells above place 1 on 6");
            assertThatThrownBy(() -> Change.of(6, 2)).hasMessage("places 2 would leave 1 bell below place 2 on 6");
            assertThatThrownBy(() -> Change.of(8, 1, 3))
                    .hasMessage("places 1,3 would leave 1 bell between places 1 and 3 on 8");
        }

        @Test
        void refusesACrossChangeAtAnOddStage() {
            assertThatThrownBy(() -> Change.cross(5))
                    .hasMessage("a cross change would leave 5 bells in the change on 5");
        }

        @Test
        void acceptsACrossChangeAtAnEvenStage() {
            assertThat(Change.cross(8).isCross()).isTrue();
            assertThat(Change.cross(8).places()).isEmpty();
        }
    }

    @Nested
    class ImpliedPlaces {

        @Test
        void addsTheBackPlaceWhenTheNotationLeavesOneOff() {
            assertThat(Change.withImpliedPlaces(6, 1).places()).containsExactly(1, 6);
            assertThat(Change.withImpliedPlaces(6, 3).places()).containsExactly(3, 6);
        }

        @Test
        void addsTheFrontPlaceWhenTheNotationLeavesOneOff() {
            assertThat(Change.withImpliedPlaces(8, 2).places()).containsExactly(1, 2);
        }

        @Test
        void leavesACompleteChangeAlone() {
            assertThat(Change.withImpliedPlaces(8, 1, 8).places()).containsExactly(1, 8);
            assertThat(Change.withImpliedPlaces(6, 3, 4).places()).containsExactly(3, 4);
        }

        @Test
        void makesTheBackPlaceAtAnOddStageWithNoPlacesWritten() {
            assertThat(Change.withImpliedPlaces(5, List.of()).places()).containsExactly(5);
        }

        @Test
        void leavesACrossChangeAloneAtAnEvenStage() {
            assertThat(Change.withImpliedPlaces(8, List.of()).isCross()).isTrue();
        }

        @Test
        void stillRefusesPlacesThatCannotBeCompleted() {
            assertThatThrownBy(() -> Change.withImpliedPlaces(8, 3, 4, 6)).isInstanceOf(NotationException.class);
        }
    }

    @Nested
    class ApplyingAChange {

        @Test
        void swapsEveryPairForACrossChange() {
            assertThat(Change.cross(4).apply(Row.rounds(4))).hasToString("2143");
            assertThat(Change.cross(8).apply(Row.rounds(8))).hasToString("21436587");
        }

        @Test
        void holdsTheBellsThatMakePlaces() {
            assertThat(Change.of(4, 1, 4).apply(Row.rounds(4))).hasToString("1324");
        }

        @Test
        void ringsTheFirstTwoChangesOfPlainBobMinor() {
            Row first = Change.cross(6).apply(Row.rounds(6));
            assertThat(first).hasToString("214365");
            Row second = Change.withImpliedPlaces(6, 1).apply(first);
            assertThat(second).hasToString("241635");
        }

        @Test
        void isItsOwnInverse() {
            Change change = Change.withImpliedPlaces(8, 3);
            Row row = RowParse.parseRow("17583624");
            assertThat(change.apply(change.apply(row))).isEqualTo(row);
        }

        @Test
        void refusesARowAtAnotherStage() {
            assertThatThrownBy(() -> Change.cross(6).apply(Row.rounds(8)))
                    .hasMessage("a change on 6 cannot be applied to a row of 8");
        }

        @Test
        void refusesSomethingThatIsNotARow() {
            assertThatThrownBy(() -> Change.cross(6).apply(null)).hasMessageContaining("applied to a Row");
        }
    }

    @Nested
    class ChangeIdentity {

        @Test
        void reportsThePlacesItMakes() {
            Change change = Change.of(8, 1, 8);
            assertThat(change.makesPlace(1)).isTrue();
            assertThat(change.makesPlace(8)).isTrue();
            assertThat(change.makesPlace(4)).isFalse();
        }

        @Test
        void comparesByStageAndPlaces() {
            assertThat(Change.of(8, 1, 2)).isEqualTo(Change.of(8, 2, 1));
            assertThat(Change.of(8, 1, 2)).isNotEqualTo(Change.of(8, 1, 8));
            assertThat(Change.of(8, 1, 2)).isNotEqualTo(Change.of(6, 1, 2));
            assertThat(Change.of(8, 1, 2).equals("12")).isFalse();
        }

        @Test
        void writesItselfAsPlaceNotation() {
            assertThat(Change.cross(8)).hasToString("x");
            assertThat(Change.of(8, 1, 2)).hasToString("12");
            assertThat(Change.of(12, 1, 12)).hasToString("1T");
            assertThat(Change.of(10, 1, 10)).hasToString("10");
        }
    }
}
