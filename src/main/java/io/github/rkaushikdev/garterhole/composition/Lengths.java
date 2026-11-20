package io.github.rkaushikdev.garterhole.composition;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Json;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * What counts as a peal, a quarter peal or a date touch.
 *
 * <p>The lengths are not arbitrary. A peal on fewer than eight bells has to be a
 * whole number of extents, which is why a peal of Minor is 5040 and not 5000:
 * seven extents of 720. On eight and above there are far more rows than a peal
 * needs, so five thousand changes and a round finish is the whole rule.
 *
 * <p>Quarter peals have no whole extent rule, which is why a quarter of Doubles
 * is 1260 changes rather than the eleven extents that would be 1320.
 */
public final class Lengths {

    /** The smallest number of changes that can be called a peal. */
    public static final int PEAL_MINIMUM = 5000;

    /** The smallest number of changes that can be called a quarter peal. */
    public static final int QUARTER_MINIMUM = 1250;

    /** Below this stage a peal must be whole extents. */
    public static final int WHOLE_EXTENT_STAGE = 8;

    private Lengths() {}

    /** What a length counts as. */
    public enum Kind {
        /** A peal: five thousand changes, or whole extents below Major. */
        PEAL,
        /** A quarter peal: twelve hundred and fifty changes at any stage. */
        QUARTER_PEAL,
        /** Anything shorter. */
        TOUCH;

        /**
         * @return the kind the way a record writes it, for example {@code quarter peal}
         */
        public String label() {
            return name().toLowerCase(Locale.ROOT).replace('_', ' ');
        }

        /**
         * @param label the kind as a record writes it
         * @return the kind
         * @throws ValidationException when no kind has that label
         */
        public static Kind fromLabel(String label) {
            for (Kind kind : values()) {
                if (kind.label().equals(label)) {
                    return kind;
                }
            }
            throw new ValidationException(Json.quote(label) + " is not a kind of performance");
        }

        @Override
        public String toString() {
            return label();
        }
    }

    /**
     * What a length check found.
     *
     * @param ok true when the length qualifies
     * @param needed the shortest length that would
     * @param reason what is wrong with the length, when something is
     */
    public record Check(boolean ok, int needed, Optional<String> reason) {}

    /**
     * How many rows there are at a stage, which is the factorial of the stage.
     *
     * @param stage a number of bells
     * @return the extent
     * @throws ValidationException when the stage is below one
     */
    public static long extentLength(int stage) {
        if (stage < 1) {
            throw new ValidationException("stage " + stage + " has no extent");
        }
        long total = 1;
        for (int bell = 2; bell <= stage; bell += 1) {
            total *= bell;
        }
        return total;
    }

    /**
     * @param stage a number of bells
     * @return true when a peal at this stage has to be a whole number of extents
     */
    public static boolean needsWholeExtents(int stage) {
        return stage < WHOLE_EXTENT_STAGE;
    }

    /**
     * @param stage a number of bells
     * @return the shortest length that counts as a peal at the stage
     */
    public static int pealLength(int stage) {
        if (!needsWholeExtents(stage)) {
            return PEAL_MINIMUM;
        }
        long extent = extentLength(stage);
        return (int) (((PEAL_MINIMUM + extent - 1) / extent) * extent);
    }

    /**
     * The shortest length that counts as a quarter peal. It does not depend on
     * the stage, which is worth stating rather than leaving to be inferred.
     *
     * @return the length
     */
    public static int quarterLength() {
        return QUARTER_MINIMUM;
    }

    /**
     * @param changes a length
     * @param stage the stage it was rung at
     * @return what the length counts as
     * @throws ValidationException when the length is negative
     */
    public static Kind classifyLength(long changes, int stage) {
        if (changes < 0) {
            throw new ValidationException("a touch cannot be " + changes + " changes long");
        }
        if (checkLength(changes, stage, Kind.PEAL).ok()) {
            return Kind.PEAL;
        }
        if (checkLength(changes, stage, Kind.QUARTER_PEAL).ok()) {
            return Kind.QUARTER_PEAL;
        }
        return Kind.TOUCH;
    }

    /**
     * Check a length, and say what is wrong with it when it does not qualify.
     *
     * @param changes a length
     * @param stage the stage it was rung at
     * @param wanted a peal or a quarter peal
     * @return whether it qualifies, what would, and why not
     * @throws ValidationException when asked to check against a touch, which has no rule
     */
    public static Check checkLength(long changes, int stage, Kind wanted) {
        if (wanted != Kind.PEAL && wanted != Kind.QUARTER_PEAL) {
            throw new ValidationException(Json.quote(String.valueOf(wanted)) + " is not a length to check against");
        }
        int needed = wanted == Kind.PEAL ? pealLength(stage) : quarterLength();
        if (changes < needed) {
            return new Check(
                    false,
                    needed,
                    Optional.of(
                            "a " + wanted + " on " + stage + " needs at least " + needed + " changes, not " + changes));
        }
        if (wanted == Kind.PEAL && needsWholeExtents(stage) && changes % extentLength(stage) != 0) {
            return new Check(
                    false,
                    needed,
                    Optional.of("on " + stage + " a peal must be whole extents of " + extentLength(stage)));
        }
        return new Check(true, needed, Optional.empty());
    }

    /**
     * @param changes a length
     * @param stage the stage it was rung at
     * @return how many extents the length comes to, when it is a whole number of them
     */
    public static OptionalLong extentsIn(long changes, int stage) {
        long extent = extentLength(stage);
        return changes % extent == 0 ? OptionalLong.of(changes / extent) : OptionalLong.empty();
    }

    /**
     * Is this a date touch, that is a touch whose length is the date written
     * out? Both the British and the American order are accepted, because a band
     * will take whichever one gives a length they can ring.
     *
     * @param changes a length
     * @param date the day it was rung
     * @return true when the length is the day and month, the month and day, or the year
     * @throws ValidationException when there is no date
     */
    public static boolean isDateTouch(long changes, LocalDate date) {
        if (date == null) {
            throw new ValidationException("a date is needed to check a date touch");
        }
        int day = date.getDayOfMonth();
        int month = date.getMonthValue();
        long dayFirst = Long.parseLong(day + String.format(Locale.ROOT, "%02d", month));
        long monthFirst = Long.parseLong(month + String.format(Locale.ROOT, "%02d", day));
        return changes == dayFirst || changes == monthFirst || changes == date.getYear();
    }
}
