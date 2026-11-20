package io.github.rkaushikdev.garterhole.striking;

import static io.github.rkaushikdev.garterhole.striking.RollingWindows.describeTrend;
import static io.github.rkaushikdev.garterhole.striking.RollingWindows.rollingAccuracy;
import static io.github.rkaushikdev.garterhole.striking.RollingWindows.trend;
import static io.github.rkaushikdev.garterhole.striking.RollingWindows.worstWindow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.striking.RollingWindows.Direction;
import io.github.rkaushikdev.garterhole.striking.RollingWindows.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToDoubleFunction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RollingWindowsTest {

    /** Errors for a run of rows, six blows to a row. */
    private static List<BlowError> errors(IntToDoubleFunction sizeOf, int rows) {
        List<BlowError> made = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < rows; rowIndex += 1) {
            for (int place = 1; place <= 6; place += 1) {
                made.add(new BlowError(place, rowIndex, place, 0, 0, sizeOf.applyAsDouble(rowIndex)));
            }
        }
        return made;
    }

    private static List<BlowError> errors(IntToDoubleFunction sizeOf) {
        return errors(sizeOf, 60);
    }

    @Nested
    class RollingAccuracy {

        @Test
        void givesOneWindowPerStretchOfRows() {
            List<Window> windows = rollingAccuracy(errors(row -> 0), 20);
            assertThat(windows).hasSize(3);
            assertThat(windows.get(0).from()).isZero();
            assertThat(windows.get(0).to()).isEqualTo(19);
            assertThat(windows.get(0).blows()).isEqualTo(120);
        }

        @Test
        void measuresEachWindowOnItsOwn() {
            List<Window> windows = rollingAccuracy(errors(row -> row < 20 ? 0 : 60), 20);
            assertThat(windows.get(0).rms()).isZero();
            assertThat(windows.get(1).rms()).isEqualTo(60);
        }

        @Test
        void slidesByTheStepItIsGiven() {
            List<Window> windows = rollingAccuracy(errors(row -> 0, 30), 20, 10);
            assertThat(windows).extracting(Window::from).containsExactly(0, 10, 20);
        }

        @Test
        void leavesOutAWindowWithNothingInIt() {
            assertThat(rollingAccuracy(errors(row -> 0, 5), 20)).hasSize(1);
        }

        @Test
        void givesNothingForNoErrors() {
            assertThat(rollingAccuracy(List.of())).isEmpty();
        }

        @Test
        void refusesAWindowOrAStepThatMeasuresNothing() {
            assertThatThrownBy(() -> rollingAccuracy(List.of(), 0)).hasMessageContaining("measures nothing");
            assertThatThrownBy(() -> rollingAccuracy(List.of(), RollingWindows.DEFAULT_ROWS, 0))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class Trend {

        @Test
        void saysTheStrikingGotWorse() {
            List<Window> windows = rollingAccuracy(errors(row -> row < 20 ? 0 : 80), 20);
            assertThat(trend(windows))
                    .get()
                    .extracting(RollingWindows.Trend::direction)
                    .isEqualTo(Direction.WORSE);
        }

        @Test
        void saysTheStrikingGotBetter() {
            List<Window> windows = rollingAccuracy(errors(row -> row < 20 ? 80 : 0), 20);
            assertThat(trend(windows))
                    .get()
                    .extracting(RollingWindows.Trend::direction)
                    .isEqualTo(Direction.BETTER);
        }

        @Test
        void callsASmallChangeLevel() {
            List<Window> windows = rollingAccuracy(errors(row -> row < 20 ? 20 : 22), 20);
            assertThat(trend(windows))
                    .get()
                    .extracting(RollingWindows.Trend::direction)
                    .isEqualTo(Direction.LEVEL);
            assertThat(Direction.LEVEL).hasToString("level");
        }

        @Test
        void givesNothingWhenThereIsOnlyOneWindow() {
            assertThat(trend(List.of(new Window(0, 19, 10, 6)))).isEmpty();
            assertThat(trend(List.of())).isEmpty();
        }
    }

    @Nested
    class WorstWindow {

        @Test
        void findsTheWorstStretch() {
            List<Window> windows = rollingAccuracy(errors(row -> row >= 20 && row < 40 ? 90 : 0), 20);
            assertThat(worstWindow(windows)).get().extracting(Window::from).isEqualTo(20);
        }

        @Test
        void givesNothingWhenThereAreNoWindows() {
            assertThat(worstWindow(List.of())).isEmpty();
        }
    }

    @Nested
    class DescribeTrend {

        @Test
        void saysHowThePieceWent() {
            String line = describeTrend(rollingAccuracy(errors(row -> row < 20 ? 0 : 80), 20));
            assertThat(line).contains("the striking got worse");
            assertThat(line).contains("worst between rows");
        }

        @Test
        void saysSoWhenThereIsNothingToMeasure() {
            assertThat(describeTrend(List.of())).isEqualTo("nothing to measure");
        }

        @Test
        void mentionsWhenThereIsOnlyOneWindow() {
            assertThat(describeTrend(rollingAccuracy(errors(row -> 30, 10), 20)))
                    .contains("one window only");
        }
    }
}
