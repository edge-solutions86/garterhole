package io.github.rkaushikdev.garterhole.util;

import io.github.rkaushikdev.garterhole.ValidationException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Telling the time.
 *
 * <p>Nothing in this library reads the wall clock directly. Everything that
 * needs the time is handed a clock, and the tests hand it a {@link FixedClock}
 * that does not move unless they move it.
 *
 * <p>That is not testing dogma. A striking analysis, a rate limiter and a lock
 * all behave differently at different times of day, and a test that only
 * passes before midnight is a test that will fail at the worst possible moment.
 */
@FunctionalInterface
public interface Clock {

    /** A second, in milliseconds. */
    long SECOND = 1000;
    /** A minute, in milliseconds. */
    long MINUTE = 60 * SECOND;
    /** An hour, in milliseconds. */
    long HOUR = 60 * MINUTE;
    /** A day, in milliseconds. */
    long DAY = 24 * HOUR;

    /**
     * @return the time now, in milliseconds since the epoch
     */
    long now();

    /**
     * @return the time now, as an instant
     */
    default Instant date() {
        return Instant.ofEpochMilli(now());
    }

    /**
     * @return the real clock
     */
    static Clock system() {
        return System::currentTimeMillis;
    }

    /**
     * A length of time written the way a person would say it.
     *
     * @param milliseconds the length of time
     * @return for example {@code 250ms}, {@code 1.5s}, {@code 5m} or {@code 3h 5m}
     * @throws ValidationException when the value is negative or not a number
     */
    static String describeDuration(double milliseconds) {
        if (!Double.isFinite(milliseconds) || milliseconds < 0) {
            throw new ValidationException(Json.number(milliseconds) + " is not a length of time");
        }
        if (milliseconds < SECOND) {
            return Math.round(milliseconds) + "ms";
        }
        if (milliseconds < MINUTE) {
            return Text.fixed(milliseconds / SECOND, 1) + "s";
        }
        long minutes = (long) Math.floor(milliseconds / MINUTE);
        if (minutes < 60) {
            return minutes + "m";
        }
        long hours = minutes / 60;
        return hours + "h " + (minutes - hours * 60) + "m";
    }

    /**
     * A moment written the way every document in this library writes one:
     * UTC, to the millisecond, for example {@code 2025-05-18T19:30:15.000Z}.
     *
     * @param moment the moment
     * @return the moment as text
     */
    static String isoString(Instant moment) {
        return ISO.format(moment);
    }

    /** The format {@link #isoString} writes, kept here so it is built once. */
    java.time.format.DateTimeFormatter ISO = java.time.format.DateTimeFormatter.ofPattern(
                    "uuuu-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);

    /**
     * The start of the day a moment falls in, in UTC, which is what a diary
     * groups by.
     *
     * @param moment the moment
     * @return midnight at the start of its day
     * @throws ValidationException when there is no moment
     */
    static Instant startOfDay(Instant moment) {
        if (moment == null) {
            throw new ValidationException("a date is needed to find the start of its day");
        }
        return moment.atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant();
    }
}
