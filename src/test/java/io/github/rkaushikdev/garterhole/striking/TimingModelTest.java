package io.github.rkaushikdev.garterhole.striking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.core.RowBlock;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TimingModelTest {

    /** Strike a block exactly as a model says it should be struck. */
    private static StrikeStream strikeTo(RowBlock block, TimingModel model) {
        return new StrikeStream(block.stage(), Ringing.strikesTo(block, model, Ringing.Shift.NONE));
    }

    @Nested
    class TheModel {

        private TimingModel model() {
            return TimingModel.of(6, 250);
        }

        @Test
        void defaultsTheHandstrokeGapToOneBlow() {
            assertThat(model().handstrokeGap()).isEqualTo(250);
        }

        @Test
        void startsTheFirstRowAtTheStartTime() {
            assertThat(model().rowStart(0)).isZero();
            assertThat(TimingModel.of(6, 250).startingAt(1000).rowStart(0)).isEqualTo(1000);
        }

        @Test
        void putsNoGapBetweenAHandstrokeRowAndItsBackstroke() {
            assertThat(model().rowStart(1)).isEqualTo(1500);
        }

        @Test
        void putsTheHandstrokeGapBeforeTheNextHandstroke() {
            assertThat(model().rowStart(2)).isEqualTo(3250);
            assertThat(model().rowStart(3)).isEqualTo(4750);
        }

        @Test
        void spacesTheBlowsWithinARowEvenly() {
            assertThat(model().expectedAt(0, 1)).isZero();
            assertThat(model().expectedAt(0, 6)).isEqualTo(1250);
        }

        @Test
        void measuresAWholePull() {
            assertThat(model().wholePull()).isEqualTo(3250);
        }

        @Test
        void givesAPealSpeedInMinutes() {
            assertThat(Math.round(model().pealMinutes())).isEqualTo(135);
        }

        @Test
        void listsTheIdealTimesForARunOfRows() {
            assertThat(model().idealTimes(2)).hasSize(12);
            assertThat(model().idealTimes(0)).isEmpty();
        }

        @Test
        void refusesSettingsThatMakeNoSense() {
            assertThatThrownBy(() -> TimingModel.of(6, Double.NaN)).hasMessageContaining("not a length of time");
            assertThatThrownBy(() -> TimingModel.of(0, 250)).hasMessageContaining("cannot be built for 0");
            assertThatThrownBy(() -> TimingModel.of(6, 0)).hasMessageContaining("not a length of time");
            assertThatThrownBy(() -> TimingModel.of(6, 250, -1)).isInstanceOf(ValidationException.class);
        }

        @Test
        void refusesARowOrPlaceOutsideTheModel() {
            assertThatThrownBy(() -> model().rowStart(-1)).hasMessageContaining("is not a row of a touch");
            assertThatThrownBy(() -> model().expectedAt(0, 7)).hasMessageContaining("outside 1..6");
            assertThatThrownBy(() -> model().idealTimes(-1)).hasMessageContaining("is not a number of rows");
        }
    }

    @Nested
    class Fit {

        @Test
        void recoversTheModelAStreamWasStruckTo() {
            TimingModel original = TimingModel.of(6, 250, 250);
            List<StruckRow> rows = RowSplitter.splitRows(strikeTo(Ringing.plainCourse(), original));
            TimingModel fitted = TimingModel.fit(rows, 6);
            assertThat(fitted.interval()).isCloseTo(250, byLessThan(0.000005));
            assertThat(fitted.handstrokeGap()).isCloseTo(250, byLessThan(0.000005));
        }

        @Test
        void recoversADifferentHandstrokeGap() {
            TimingModel original = TimingModel.of(6, 200, 400);
            List<StruckRow> rows = RowSplitter.splitRows(strikeTo(Ringing.plainCourse(), original));
            TimingModel fitted = TimingModel.fit(rows, 6);
            assertThat(fitted.interval()).isCloseTo(200, byLessThan(0.000005));
            assertThat(fitted.handstrokeGap()).isCloseTo(400, byLessThan(0.000005));
        }

        @Test
        void takesTheStartTimeFromTheFirstBlow() {
            TimingModel original = TimingModel.of(6, 250).startingAt(5000);
            List<StruckRow> rows = RowSplitter.splitRows(strikeTo(Ringing.plainLead(), original));
            assertThat(TimingModel.fit(rows, 6).start()).isEqualTo(5000);
        }

        @Test
        void refusesTooFewRowsToFitAnything() {
            assertThatThrownBy(() -> TimingModel.fit(List.of(), 6)).hasMessageContaining("at least two rows");
        }

        @Test
        void refusesRowsWithNoTimeBetweenTheBlows() {
            StruckRow flat = new StruckRow(new Row(1, 2), 0, List.of(new Strike(1, 0), new Strike(2, 0)), 0, 0);
            assertThatThrownBy(() -> TimingModel.fit(List.of(flat, flat), 2))
                    .hasMessageContaining("no measurable blow interval");
        }
    }

    @Nested
    class ForPealSpeed {

        @Test
        void buildsAModelThatRingsAPealInTheTimeAskedFor() {
            assertThat(TimingModel.forPealSpeed(8, 180).pealMinutes()).isCloseTo(180, byLessThan(0.0000005));
        }

        @Test
        void getsSlowerAsThePealTimeGoesUp() {
            assertThat(TimingModel.forPealSpeed(8, 180).interval())
                    .isGreaterThan(TimingModel.forPealSpeed(8, 160).interval());
        }

        @Test
        void refusesASpeedThatIsNotATime() {
            assertThatThrownBy(() -> TimingModel.forPealSpeed(8, 0)).hasMessageContaining("is not a peal speed");
        }
    }
}
