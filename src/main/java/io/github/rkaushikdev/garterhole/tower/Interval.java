package io.github.rkaushikdev.garterhole.tower;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.Optional;

/**
 * A half open interval of time.
 *
 * <p>Availability, practice slots and service ringing are all runs of time with
 * a start and an end, and the questions asked of them are all the same: do
 * these two overlap, what do they have in common, and what is left when one is
 * taken out of the other. Doing that arithmetic once here is much safer than
 * doing it again in every module that needs it.
 *
 * <p>An interval includes its start and excludes its end, so two intervals
 * that meet at a point do not overlap. The moments are numbers, usually
 * milliseconds since the epoch, and two intervals are equal when their ends are.
 *
 * @param start the first moment in the interval
 * @param end the first moment after it
 */
public record Interval(double start, double end) {

    /**
     * @throws ValidationException when a moment is not finite or the end is not after the start
     */
    public Interval {
        if (!Double.isFinite(start)) {
            throw new ValidationException(Json.number(start) + " is not a moment in time");
        }
        if (!Double.isFinite(end)) {
            throw new ValidationException(Json.number(end) + " is not a moment in time");
        }
        if (end <= start) {
            throw new ValidationException(
                    "an interval cannot end at " + Json.number(end) + " having started at " + Json.number(start));
        }
    }

    /**
     * @param start the first moment in the interval
     * @param end the first moment after it
     * @return the interval
     */
    public static Interval of(double start, double end) {
        return new Interval(start, end);
    }

    /**
     * @return how long the interval is
     */
    public double length() {
        return end - start;
    }

    /**
     * @param moment a moment
     * @return true when the moment is in the interval
     */
    public boolean contains(double moment) {
        return moment >= start && moment < end;
    }

    /**
     * @param other another interval
     * @return true when the two share any time at all
     */
    public boolean overlaps(Interval other) {
        return start < other.end && other.start < end;
    }

    /**
     * @param other another interval
     * @return true when this interval holds the whole of the other
     */
    public boolean covers(Interval other) {
        return start <= other.start && end >= other.end;
    }

    /**
     * @param other another interval
     * @return the part the two have in common, when they have any
     */
    public Optional<Interval> intersect(Interval other) {
        double from = Math.max(start, other.start);
        double to = Math.min(end, other.end);
        return to > from ? Optional.of(new Interval(from, to)) : Optional.empty();
    }

    /**
     * @return the interval written the way a mathematician would, for example {@code [5, 10)}
     */
    @Override
    public String toString() {
        return "[" + Json.number(start) + ", " + Json.number(end) + ")";
    }
}
