package io.github.rkaushikdev.garterhole.striking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StrikeStreamTest {

    /** Six bells struck evenly, one row after another. */
    private static List<Strike> evenStriking(int rows) {
        List<Strike> strikes = new ArrayList<>();
        double at = 0;
        for (int row = 0; row < rows; row += 1) {
            for (int bell = 1; bell <= 6; bell += 1) {
                strikes.add(new Strike(bell, at));
                at += 250;
            }
        }
        return strikes;
    }

    @Nested
    class StrikeStreamConstruction {

        @Test
        void keepsTheStrikesItWasGiven() {
            StrikeStream stream = new StrikeStream(6, evenStriking(2));
            assertThat(stream.length()).isEqualTo(12);
            assertThat(stream.stage()).isEqualTo(6);
        }

        @Test
        void isFrozen() {
            StrikeStream stream = new StrikeStream(6, evenStriking(1));
            assertThat(Modifier.isFinal(StrikeStream.class.getModifiers())).isTrue();
            assertThat(Strike.class.isRecord()).isTrue();
            assertThatThrownBy(() -> stream.strikes().add(new Strike(1, 0)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void acceptsAnEmptyStream() {
            assertThat(new StrikeStream(6, List.of()).length()).isZero();
            assertThat(new StrikeStream(6, List.of()).duration()).isZero();
        }

        @Test
        void refusesAnUnsupportedStage() {
            assertThatThrownBy(() -> new StrikeStream(2, List.of())).hasMessageContaining("stage 2 is not supported");
        }

        @Test
        void refusesStrikesThatAreNotAnArray() {
            assertThatThrownBy(() -> new StrikeStream(6, null)).hasMessageContaining("needs an array of strikes");
        }

        @Test
        void refusesABellThatIsNotOnTheStage() {
            assertThatThrownBy(() -> new StrikeStream(6, List.of(new Strike(7, 0))))
                    .hasMessage("strike 0 names bell 7, which is not on 6");
        }

        @Test
        void refusesAStrikeWithNoTime() {
            assertThatThrownBy(() -> new StrikeStream(6, List.of(new Strike(1, Double.POSITIVE_INFINITY))))
                    .hasMessage("strike 0 has no time");
            assertThatThrownBy(() -> new StrikeStream(6, List.of(new Strike(1, Double.NaN))))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void refusesAStrikeThatIsNotAnObject() {
            assertThatThrownBy(() -> new StrikeStream(6, Arrays.asList((Strike) null)))
                    .hasMessage("strike 0 is not an object");
        }

        @Test
        void refusesStrikesThatAreOutOfOrder() {
            assertThatThrownBy(() -> new StrikeStream(6, List.of(new Strike(1, 100), new Strike(2, 50))))
                    .hasMessage("strike 1 is at 50 which is before strike 0");
        }
    }

    @Nested
    class From {

        @Test
        void sortsTheStrikesIntoTimeOrder() {
            StrikeStream stream = StrikeStream.from(6, List.of(new Strike(2, 250), new Strike(1, 0)));
            assertThat(stream.strikes()).extracting(Strike::bell).containsExactly(1, 2);
        }

        @Test
        void refusesSomethingThatIsNotAnArray() {
            assertThatThrownBy(() -> StrikeStream.from(6, null)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class ReadingAStream {

        private final StrikeStream stream = new StrikeStream(6, evenStriking(4));

        @Test
        void measuresTheRecording() {
            assertThat(stream.duration()).isEqualTo(23 * 250);
        }

        @Test
        void reportsTheGapsBetweenBlows() {
            assertThat(new HashSet<>(stream.gaps())).containsExactly(250.0);
            assertThat(stream.gaps()).hasSize(23);
        }

        @Test
        void countsTheBlowsEachBellStruck() {
            assertThat(stream.blowsPerBell().get(1)).isEqualTo(4);
            assertThat(stream.blowsPerBell().get(6)).isEqualTo(4);
        }

        @Test
        void findsNothingMissingInAnEvenStream() {
            assertThat(stream.bellsWithMissingBlows()).isEmpty();
        }

        @Test
        void findsABellThatStoppedRinging() {
            StrikeStream shorter = new StrikeStream(
                    6,
                    evenStriking(4).stream()
                            .filter(strike -> strike.bell() != 3 || strike.at() < 3000)
                            .toList());
            assertThat(shorter.bellsWithMissingBlows()).contains(3);
        }

        @Test
        void takesASliceByTime() {
            assertThat(stream.between(0, 1500)).hasSize(6);
            assertThat(stream.between(1500, 3000)).hasSize(6);
        }

        @Test
        void iterates() {
            List<Strike> seen = new ArrayList<>();
            stream.forEach(seen::add);
            assertThat(seen).hasSize(24);
        }
    }
}
