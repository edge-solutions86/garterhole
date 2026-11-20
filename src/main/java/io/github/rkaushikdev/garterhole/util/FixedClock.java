package io.github.rkaushikdev.garterhole.util;

import io.github.rkaushikdev.garterhole.ValidationException;
import java.time.Instant;

/**
 * A clock that stands still until it is told to move, which is what the tests
 * hand to anything that needs the time.
 */
public final class FixedClock implements Clock {

    private long moment;

    /** A clock standing at the epoch. */
    public FixedClock() {
        this(0L);
    }

    /**
     * @param start milliseconds since the epoch
     */
    public FixedClock(long start) {
        this.moment = start;
    }

    /**
     * @param start the moment to start at
     * @throws ValidationException when there is no moment
     */
    public FixedClock(Instant start) {
        if (start == null) {
            throw new ValidationException("null is not a moment to start a clock at");
        }
        this.moment = start.toEpochMilli();
    }

    @Override
    public long now() {
        return moment;
    }

    /**
     * Move the clock forward.
     *
     * @param milliseconds how far
     * @return this clock
     * @throws ValidationException when asked to move backwards
     */
    public FixedClock advance(long milliseconds) {
        if (milliseconds < 0) {
            throw new ValidationException("a clock cannot move forward by " + milliseconds);
        }
        moment += milliseconds;
        return this;
    }

    /**
     * Put the clock at a particular moment.
     *
     * @param milliseconds milliseconds since the epoch
     * @return this clock
     */
    public FixedClock set(long milliseconds) {
        moment = milliseconds;
        return this;
    }

    /**
     * Put the clock at a particular moment.
     *
     * @param instant the moment
     * @return this clock
     */
    public FixedClock set(Instant instant) {
        moment = instant.toEpochMilli();
        return this;
    }
}
