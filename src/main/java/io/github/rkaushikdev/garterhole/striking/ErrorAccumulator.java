package io.github.rkaushikdev.garterhole.striking;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.Optional;

/**
 * Summing errors without holding on to them.
 *
 * <p>The per bell and per row breakdowns started as three copies of the same
 * grouping loop, each building a list of every error and then folding it. For
 * a peal that is two hundred thousand numbers held in memory to produce a
 * handful of averages.
 *
 * <p>An accumulator keeps a count, a sum and a sum of squares, which is
 * everything the mean and the root mean square need, and it does it in
 * constant space. It also means the analysis can run against a live recording
 * rather than waiting for the band to finish. It is a running total, so it is
 * mutable and not safe to share between threads.
 *
 * @param <S> what each error came from, kept for the worst one
 */
public final class ErrorAccumulator<S> {

    private int count;
    private double total;
    private double totalSquares;
    private Worst<S> largest;

    /**
     * The summary the reports use.
     *
     * @param blows how many errors were added
     * @param rms their root mean square
     * @param meanError their mean, keeping its sign
     */
    public record Summary(int blows, double rms, double meanError) {}

    /**
     * The worst error seen, and what it came from.
     *
     * @param <S> what the error came from
     * @param error the error, keeping its sign
     * @param source what it came from, when that was given
     */
    public record Worst<S>(double error, Optional<S> source) {}

    /**
     * Take one error with nothing to say where it came from.
     *
     * @param error the error in milliseconds
     * @return this accumulator, so that adds can be chained
     * @throws ValidationException when the error is not a finite number
     */
    public ErrorAccumulator<S> add(double error) {
        return record(error, Optional.empty());
    }

    /**
     * Take one error.
     *
     * @param error the error in milliseconds
     * @param source whatever the error came from, kept if it is the worst
     * @return this accumulator, so that adds can be chained
     * @throws ValidationException when the error is not a finite number
     */
    public ErrorAccumulator<S> add(double error, S source) {
        return record(error, Optional.ofNullable(source));
    }

    /**
     * @return how many errors have been added
     */
    public int count() {
        return count;
    }

    /**
     * @return the mean error, keeping its sign, or zero when nothing has been added
     */
    public double meanError() {
        return count == 0 ? 0 : total / count;
    }

    /**
     * @return the root mean square, which is the number a competition is
     *     judged on, or zero when nothing has been added
     */
    public double rms() {
        return count == 0 ? 0 : Math.sqrt(totalSquares / count);
    }

    /**
     * @return the error furthest from zero, the first of them on a tie
     */
    public Optional<Worst<S>> worst() {
        return Optional.ofNullable(largest);
    }

    /**
     * @return the count, the root mean square and the mean
     */
    public Summary summary() {
        return new Summary(count, rms(), meanError());
    }

    private ErrorAccumulator<S> record(double error, Optional<S> source) {
        if (!Double.isFinite(error)) {
            throw new ValidationException(Json.number(error) + " is not an error in milliseconds");
        }
        count += 1;
        total += error;
        totalSquares += error * error;
        if (largest == null || Math.abs(error) > Math.abs(largest.error())) {
            largest = new Worst<>(error, source);
        }
        return this;
    }
}
