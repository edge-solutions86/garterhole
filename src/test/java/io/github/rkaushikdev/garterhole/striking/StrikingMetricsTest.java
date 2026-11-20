package io.github.rkaushikdev.garterhole.striking;

import static io.github.rkaushikdev.garterhole.striking.StrikingMetrics.accuracy;
import static io.github.rkaushikdev.garterhole.striking.StrikingMetrics.accuracyByBell;
import static io.github.rkaushikdev.garterhole.striking.StrikingMetrics.accuracyByRow;
import static io.github.rkaushikdev.garterhole.striking.StrikingMetrics.badPatches;
import static io.github.rkaushikdev.garterhole.striking.StrikingMetrics.blowErrors;
import static io.github.rkaushikdev.garterhole.striking.StrikingMetrics.rootMeanSquare;
import static io.github.rkaushikdev.garterhole.striking.StrikingMetrics.worstBell;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.striking.ErrorAccumulator.Summary;
import io.github.rkaushikdev.garterhole.striking.StrikingMetrics.RowAccuracy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StrikingMetricsTest {

    private static TimingModel model() {
        return TimingModel.of(6, 250);
    }

    /** Strike a lead to the model, moving blows by the shift. */
    private static List<StruckRow> struckLead(Ringing.Shift shift) {
        List<Strike> strikes = new ArrayList<>(Ringing.strikesTo(Ringing.plainLead(), model(), shift));
        strikes.sort(Comparator.comparingDouble(Strike::at));
        return RowSplitter.splitRows(new StrikeStream(6, strikes));
    }

    private static List<StruckRow> struckLead() {
        return struckLead(Ringing.Shift.NONE);
    }

    @Nested
    class BlowErrors {

        @Test
        void findsNoErrorInPerfectStriking() {
            List<BlowError> errors = blowErrors(struckLead(), model());
            assertThat(errors).hasSize(78);
            assertThat(errors).allMatch(entry -> entry.error() == 0);
        }

        @Test
        void recordsTheBellRowAndPlaceOfEveryBlow() {
            assertThat(blowErrors(struckLead(), model()).get(0)).isEqualTo(new BlowError(1, 0, 1, 0, 0, 0));
        }

        @Test
        void signsAnEarlyBlowNegativeAndALateOnePositive() {
            List<BlowError> late = blowErrors(struckLead((bell, row) -> bell == 6 ? 40 : 0), model());
            List<BlowError> early = blowErrors(struckLead((bell, row) -> bell == 6 ? -40 : 0), model());
            assertThat(late).anyMatch(entry -> entry.error() > 0);
            assertThat(early).anyMatch(entry -> entry.error() < 0);
        }

        @Test
        void refusesRowsOrAModelItCannotUse() {
            assertThatThrownBy(() -> blowErrors(null, model())).hasMessageContaining("list of struck rows");
            assertThatThrownBy(() -> blowErrors(struckLead(), null)).hasMessageContaining("a TimingModel is needed");
        }
    }

    @Nested
    class RootMeanSquare {

        @Test
        void isZeroForPerfectStriking() {
            assertThat(rootMeanSquare(List.of(0, 0, 0))).isZero();
        }

        @Test
        void isZeroForNothing() {
            assertThat(rootMeanSquare(List.of())).isZero();
        }

        @Test
        void doesNotLetEarlyAndLateCancelOut() {
            assertThat(rootMeanSquare(List.of(-30, 30))).isEqualTo(30);
        }

        @Test
        void growsWithTheSizeOfTheErrors() {
            assertThat(rootMeanSquare(List.of(10, 10))).isLessThan(rootMeanSquare(List.of(20, 20)));
        }
    }

    @Nested
    class OverallAccuracy {

        @Test
        void summarisesPerfectStriking() {
            StrikingMetrics.Accuracy summary = accuracy(blowErrors(struckLead(), model()));
            assertThat(summary.blows()).isEqualTo(78);
            assertThat(summary.rms()).isZero();
            assertThat(summary.meanError()).isZero();
        }

        @Test
        void findsTheWorstBlow() {
            List<BlowError> errors = blowErrors(struckLead((bell, row) -> bell == 4 && row == 3 ? 90 : 0), model());
            assertThat(accuracy(errors).worst())
                    .get()
                    .extracting(BlowError::bell)
                    .isEqualTo(4);
            assertThat(accuracy(errors).worst())
                    .get()
                    .extracting(BlowError::rowIndex)
                    .isEqualTo(3);
        }

        @Test
        void hasNoWorstBlowWhenNothingWasRung() {
            assertThat(accuracy(List.of()).worst()).isEmpty();
            assertThat(accuracy(List.of()).rms()).isZero();
        }
    }

    @Nested
    class AccuracyByBell {

        @Test
        void givesEveryBellALine() {
            Map<Integer, Summary> summary = accuracyByBell(blowErrors(struckLead(), model()));
            assertThat(summary.keySet()).containsExactly(1, 2, 3, 4, 5, 6);
            assertThat(summary.get(1).blows()).isEqualTo(13);
        }

        @Test
        void blamesOnlyTheBellThatStruckBadly() {
            Map<Integer, Summary> summary =
                    accuracyByBell(blowErrors(struckLead((bell, row) -> bell == 5 ? 60 : 0), model()));
            assertThat(summary.get(5).rms()).isGreaterThan(0);
            assertThat(summary.get(2).rms()).isZero();
        }
    }

    @Nested
    class AccuracyByRow {

        @Test
        void givesEveryRowALineInOrder() {
            List<RowAccuracy> byRow = accuracyByRow(blowErrors(struckLead(), model()));
            assertThat(byRow).hasSize(13);
            assertThat(byRow)
                    .extracting(RowAccuracy::rowIndex)
                    .containsExactlyElementsOf(IntStream.range(0, 13).boxed().toList());
        }

        @Test
        void findsTheRowThatWentWrong() {
            List<BlowError> errors = blowErrors(struckLead((bell, row) -> row == 4 ? 80 : 0), model());
            List<RowAccuracy> byRow = accuracyByRow(errors);
            assertThat(byRow.get(4).rms()).isGreaterThan(0);
            assertThat(byRow.get(3).rms()).isZero();
        }
    }

    @Nested
    class WorstBell {

        @Test
        void namesTheBellWithTheWorstStriking() {
            List<BlowError> errors = blowErrors(struckLead((bell, row) -> bell == 3 ? 70 : 0), model());
            assertThat(worstBell(errors)).hasValue(3);
        }

        @Test
        void givesNothingWhenNothingWasRung() {
            assertThat(worstBell(List.of())).isEmpty();
        }
    }

    @Nested
    class BadPatches {

        @Test
        void listsTheRowsAboveTheThreshold() {
            List<BlowError> errors = blowErrors(struckLead((bell, row) -> row == 7 ? 100 : 0), model());
            assertThat(badPatches(errors, 50)).containsExactly(7);
        }

        @Test
        void listsNothingWhenTheRingingIsClean() {
            assertThat(badPatches(blowErrors(struckLead(), model()), 50)).isEmpty();
        }

        @Test
        void refusesAThresholdThatIsNotATime() {
            assertThatThrownBy(() -> badPatches(List.of(), -1)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class TheAccumulatorUnderneath {

        @Test
        void givesTheSameAnswerAsFoldingTheErrorsByHand() {
            List<BlowError> errors = blowErrors(struckLead((bell, row) -> bell == 2 ? 45 : 0), model());
            double squares = 0;
            for (BlowError entry : errors) {
                squares += entry.error() * entry.error();
            }
            double byHand = Math.sqrt(squares / errors.size());
            assertThat(accuracy(errors).rms()).isCloseTo(byHand, byLessThan(0.0000000005));
        }

        @Test
        void keepsTheWorstBlowRatherThanOnlyItsSize() {
            List<BlowError> errors = blowErrors(struckLead((bell, row) -> bell == 6 && row == 2 ? -120 : 0), model());
            BlowError worst = accuracy(errors).worst().orElseThrow();
            assertThat(worst.bell()).isEqualTo(6);
            assertThat(worst.error()).isEqualTo(-120);
        }
    }
}
