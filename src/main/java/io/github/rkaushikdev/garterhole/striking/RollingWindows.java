package io.github.rkaushikdev.garterhole.striking;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.CollectionHelpers;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Watching the striking change as a touch goes on.
 *
 * <p>One number for a whole quarter peal hides what a band actually wants to
 * know, which is that the ringing was fine for twenty minutes and then fell
 * apart. A rolling window over the rows shows that, and it shows it as a shape
 * rather than as a list of nine hundred numbers.
 *
 * <p>The window is measured in rows rather than in seconds, because a band that
 * slows down should not get a longer window as a consolation.
 */
public final class RollingWindows {

    /** How many rows a window covers when nobody says. */
    public static final int DEFAULT_ROWS = 20;

    /** The change in root mean square, in milliseconds, below which the striking is level. */
    private static final double LEVEL = 5;

    private RollingWindows() {}

    /**
     * The accuracy of one window of rows.
     *
     * @param from the first row of the window
     * @param to the last row of the window, inclusive
     * @param rms the root mean square error of the blows in it
     * @param blows how many blows it held
     */
    public record Window(int from, int to, double rms, int blows) {}

    /** Which way the striking went over a piece. */
    public enum Direction {
        /** The last window was more accurate than the first. */
        BETTER,
        /** The last window was less accurate than the first. */
        WORSE,
        /** The two were within a few milliseconds of each other. */
        LEVEL;

        /**
         * @return the word, for example {@code better}
         */
        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * How the striking moved between the first window and the last.
     *
     * @param direction better, worse or level
     * @param change the last window's root mean square minus the first's
     */
    public record Trend(Direction direction, double change) {}

    /**
     * The accuracy of each window of twenty rows.
     *
     * @param errors the blow errors
     * @return the windows that held any blows, in order
     */
    public static List<Window> rollingAccuracy(List<BlowError> errors) {
        return rollingAccuracy(errors, DEFAULT_ROWS);
    }

    /**
     * The accuracy of each window of rows, the windows side by side.
     *
     * @param errors the blow errors
     * @param rows how many rows each window covers
     * @return the windows that held any blows, in order
     */
    public static List<Window> rollingAccuracy(List<BlowError> errors, int rows) {
        return rollingAccuracy(errors, rows, rows);
    }

    /**
     * The accuracy of each window of rows.
     *
     * @param errors the blow errors
     * @param rows how many rows each window covers
     * @param step how many rows each window starts after the one before
     * @return the windows that held any blows, in order
     * @throws ValidationException when the window or the step is less than a row
     */
    public static List<Window> rollingAccuracy(List<BlowError> errors, int rows, int step) {
        if (rows < 1) {
            throw new ValidationException("a window of " + rows + " rows measures nothing");
        }
        if (step < 1) {
            throw new ValidationException("a step of " + step + " rows would not move");
        }
        TreeMap<Integer, List<BlowError>> byRow = new TreeMap<>(CollectionHelpers.groupBy(errors, BlowError::rowIndex));
        if (byRow.isEmpty()) {
            return List.of();
        }
        int first = byRow.firstKey();
        int last = byRow.lastKey();
        List<Window> windows = new ArrayList<>();
        for (int from = first; from <= last; from += step) {
            int to = from + rows - 1;
            ErrorAccumulator<BlowError> accumulator = new ErrorAccumulator<>();
            for (int row = from; row <= to; row += 1) {
                for (BlowError entry : byRow.getOrDefault(row, List.of())) {
                    accumulator.add(entry.error());
                }
            }
            if (accumulator.count() > 0) {
                windows.add(new Window(from, to, accumulator.rms(), accumulator.count()));
            }
        }
        return List.copyOf(windows);
    }

    /**
     * Whether the striking got better or worse over the piece, as the
     * difference between the first window and the last.
     *
     * @param windows the windows, in order
     * @return the trend, or nothing when there are fewer than two windows
     */
    public static Optional<Trend> trend(List<Window> windows) {
        if (windows.size() < 2) {
            return Optional.empty();
        }
        double change = windows.get(windows.size() - 1).rms() - windows.get(0).rms();
        if (Math.abs(change) < LEVEL) {
            return Optional.of(new Trend(Direction.LEVEL, change));
        }
        return Optional.of(new Trend(change < 0 ? Direction.BETTER : Direction.WORSE, change));
    }

    /**
     * @param windows the windows
     * @return the window the ringing was worst in, the first of them on a tie,
     *     or nothing when there are none
     */
    public static Optional<Window> worstWindow(List<Window> windows) {
        Window worst = null;
        for (Window window : windows) {
            if (worst == null || window.rms() > worst.rms()) {
                worst = window;
            }
        }
        return Optional.ofNullable(worst);
    }

    /**
     * A one line description of how the piece went.
     *
     * @param windows the windows
     * @return the description
     */
    public static String describeTrend(List<Window> windows) {
        Optional<Window> worst = worstWindow(windows);
        if (worst.isEmpty()) {
            return "nothing to measure";
        }
        String opening = trend(windows)
                .map(moving -> "the striking got " + moving.direction())
                .orElse("one window only");
        Window window = worst.get();
        return opening + ", worst between rows " + window.from() + " and " + window.to() + " at "
                + Math.round(window.rms()) + "ms";
    }
}
