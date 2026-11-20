package io.github.rkaushikdev.garterhole.striking;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.striking.ErrorAccumulator.Summary;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;

/**
 * How far every blow was from where it should have been.
 *
 * <p>The error of a blow is the time it actually struck minus the time the
 * model says it should have. Early is negative and late is positive, and
 * keeping the sign matters: a band that is uniformly late is running slow,
 * while a band whose errors cancel out is ringing unevenly. The two need
 * different advice.
 */
public final class StrikingMetrics {

    private StrikingMetrics() {}

    /**
     * The overall accuracy of a piece of ringing.
     *
     * @param blows how many blows were measured
     * @param rms their root mean square error
     * @param meanError their mean error, keeping its sign
     * @param worst the blow furthest from where it should have been, if any were rung
     */
    public record Accuracy(int blows, double rms, double meanError, Optional<BlowError> worst) {}

    /**
     * The accuracy of one row.
     *
     * @param rowIndex the row, counted from zero
     * @param rms the root mean square error of its blows
     */
    public record RowAccuracy(int rowIndex, double rms) {}

    /**
     * Work out the error of every blow.
     *
     * @param rows the struck rows
     * @param model where each blow should have fallen
     * @return one error per blow, in the order they were struck
     * @throws ValidationException when the rows or the model are missing
     */
    public static List<BlowError> blowErrors(List<StruckRow> rows, TimingModel model) {
        if (rows == null) {
            throw new ValidationException("a list of struck rows is needed");
        }
        if (model == null) {
            throw new ValidationException("a TimingModel is needed to measure against");
        }
        List<BlowError> errors = new ArrayList<>();
        for (StruckRow row : rows) {
            List<Strike> strikes = row.strikes();
            for (int index = 0; index < strikes.size(); index += 1) {
                Strike strike = strikes.get(index);
                int place = index + 1;
                double expected = model.expectedAt(row.index(), place);
                errors.add(new BlowError(
                        strike.bell(), row.index(), place, strike.at(), expected, strike.at() - expected));
            }
        }
        return List.copyOf(errors);
    }

    /**
     * The root mean square of a list of errors, which is the single number a
     * striking competition is decided on.
     *
     * @param errors the errors in milliseconds
     * @return their root mean square, or zero for none
     */
    public static double rootMeanSquare(Iterable<? extends Number> errors) {
        ErrorAccumulator<Void> accumulator = new ErrorAccumulator<>();
        for (Number error : errors) {
            accumulator.add(error.doubleValue());
        }
        return accumulator.rms();
    }

    /**
     * @param errors the blow errors
     * @return the overall accuracy, with the worst blow
     */
    public static Accuracy accuracy(List<BlowError> errors) {
        ErrorAccumulator<BlowError> accumulator = new ErrorAccumulator<>();
        for (BlowError entry : errors) {
            accumulator.add(entry.error(), entry);
        }
        Summary summary = accumulator.summary();
        return new Accuracy(
                summary.blows(),
                summary.rms(),
                summary.meanError(),
                accumulator.worst().flatMap(ErrorAccumulator.Worst::source));
    }

    /**
     * Accuracy broken down by bell, which is what a band actually wants to see.
     *
     * @param errors the blow errors
     * @return a summary per bell, the treble first
     */
    public static Map<Integer, Summary> accuracyByBell(List<BlowError> errors) {
        return byKey(errors, BlowError::bell).summaries();
    }

    /**
     * Accuracy broken down by row, which is how a bad patch is found.
     *
     * @param errors the blow errors
     * @return the accuracy of each row, in order
     */
    public static List<RowAccuracy> accuracyByRow(List<BlowError> errors) {
        return byKey(errors, BlowError::rowIndex).summaries().entrySet().stream()
                .map(entry -> new RowAccuracy(entry.getKey(), entry.getValue().rms()))
                .toList();
    }

    /**
     * @param errors the blow errors
     * @return the bell that struck worst, or nothing when nothing was rung
     */
    public static OptionalInt worstBell(List<BlowError> errors) {
        return byKey(errors, BlowError::bell).worstKey().map(OptionalInt::of).orElseGet(OptionalInt::empty);
    }

    /**
     * The rows whose accuracy is worse than a threshold, so that a band can go
     * and listen to the bad patches rather than the whole recording.
     *
     * @param errors the blow errors
     * @param threshold in milliseconds
     * @return the indexes of the rows above the threshold
     * @throws ValidationException when the threshold is negative
     */
    public static List<Integer> badPatches(List<BlowError> errors, double threshold) {
        if (threshold < 0) {
            throw new ValidationException(Json.number(threshold) + " is not a threshold in milliseconds");
        }
        return accuracyByRow(errors).stream()
                .filter(row -> row.rms() > threshold)
                .map(RowAccuracy::rowIndex)
                .toList();
    }

    private static AccumulatorSet<Integer, BlowError> byKey(
            List<BlowError> errors, Function<BlowError, Integer> keyOf) {
        AccumulatorSet<Integer, BlowError> set = new AccumulatorSet<>();
        for (BlowError entry : errors) {
            set.add(keyOf.apply(entry), entry.error(), entry);
        }
        return set;
    }
}
