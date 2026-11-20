package io.github.rkaushikdev.garterhole.striking;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Json;

/**
 * How far out a blow has to be before it is worth naming as a fault. A blow
 * inside these is not worth naming, because no listener can hear the
 * difference.
 *
 * <p>Every threshold must be positive, so a set of thresholds that could not be
 * used is refused as it is made.
 *
 * @param clip how many milliseconds early a blow must be to be a clip
 * @param gap how many milliseconds late a blow must be to be a gap
 * @param drift how many milliseconds out, either way, a blow must be to count towards a drift
 * @param driftRun how many such blows in a row make a drift
 */
public record Thresholds(double clip, double gap, double drift, double driftRun) {

    /** The thresholds used when nobody gives any. */
    public static final Thresholds DEFAULT = new Thresholds(50, 50, 30, 4);

    /**
     * @throws ValidationException naming the first threshold that is not positive
     */
    public Thresholds {
        requirePositive("clip", clip);
        requirePositive("gap", gap);
        requirePositive("drift", drift);
        requirePositive("driftRun", driftRun);
    }

    /**
     * @param milliseconds the new clip threshold
     * @return these thresholds with the clip threshold replaced
     */
    public Thresholds withClip(double milliseconds) {
        return new Thresholds(milliseconds, gap, drift, driftRun);
    }

    /**
     * @param milliseconds the new gap threshold
     * @return these thresholds with the gap threshold replaced
     */
    public Thresholds withGap(double milliseconds) {
        return new Thresholds(clip, milliseconds, drift, driftRun);
    }

    /**
     * @param milliseconds the new drift threshold
     * @return these thresholds with the drift threshold replaced
     */
    public Thresholds withDrift(double milliseconds) {
        return new Thresholds(clip, gap, milliseconds, driftRun);
    }

    /**
     * @param blows the new length of run that makes a drift
     * @return these thresholds with the drift run replaced
     */
    public Thresholds withDriftRun(double blows) {
        return new Thresholds(clip, gap, drift, blows);
    }

    private static void requirePositive(String name, double value) {
        if (value <= 0) {
            throw new ValidationException(
                    "the " + name + " threshold must be a positive number, not " + Json.number(value));
        }
    }
}
