package io.github.rkaushikdev.garterhole.composition.music;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.core.Row;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs are the music that bands notice first. Four bells in order at the back,
 * {@code 5678}, is a run of four, and a composition is judged largely on how
 * many of those it has.
 *
 * <p>A run counts whether it goes up or down, and it counts at the front of the
 * row as well as the back. The two ends are counted separately because a
 * composer will happily trade one for the other.
 */
public final class Runs {

    private static final int DEFAULT_SHORTEST = 4;

    private Runs() {}

    /**
     * How many rows have a run of a given length, by end.
     *
     * @param front rows with the run at the front
     * @param back rows with the run at the back
     * @param total rows with the run at either end, each row counted once
     */
    public record RunCount(int front, int back, int total) {}

    /**
     * The length of the run at the back of a row, counting from the tenor end.
     * A row with no run at all still has a run of one, so the smallest
     * interesting answer is two.
     *
     * @param row a row
     * @return the length of the run at the back
     * @throws ValidationException when there is no row
     */
    public static int runLengthAtBack(Row row) {
        requireRow(row);
        return longestRunEndingAt(row.toArray(), row.stage() - 1, -1);
    }

    /**
     * @param row a row
     * @return the length of the run at the front
     * @throws ValidationException when there is no row
     */
    public static int runLengthAtFront(Row row) {
        requireRow(row);
        return longestRunEndingAt(row.toArray(), 0, 1);
    }

    /**
     * @param row a row
     * @param length the shortest run wanted, two or more
     * @return true when the row has a run of at least that length at the back
     */
    public static boolean hasRunAtBack(Row row, int length) {
        return runLengthAtBack(row) >= requireLength(length);
    }

    /**
     * @param row a row
     * @param length the shortest run wanted, two or more
     * @return true when the row has a run of at least that length at the front
     */
    public static boolean hasRunAtFront(Row row, int length) {
        return runLengthAtFront(row) >= requireLength(length);
    }

    /**
     * Count the rows of a block that have a run of at least the given length,
     * split by which end the run is at. A row with a run at both ends is counted
     * once in each.
     *
     * @param rows the rows
     * @param length the shortest run counted, two or more
     * @return the counts
     */
    public static RunCount countRuns(Iterable<Row> rows, int length) {
        requireLength(length);
        int front = 0;
        int back = 0;
        int total = 0;
        for (Row row : rows) {
            boolean atFront = hasRunAtFront(row, length);
            boolean atBack = hasRunAtBack(row, length);
            if (atFront) {
                front += 1;
            }
            if (atBack) {
                back += 1;
            }
            if (atFront || atBack) {
                total += 1;
            }
        }
        return new RunCount(front, back, total);
    }

    /**
     * Count runs of every length from four up to the stage, which is the table
     * a composition is usually judged on.
     *
     * @param rows the rows
     * @param stage the stage they are at
     * @return the counts for each length, shortest first
     */
    public static Map<Integer, RunCount> runTable(Iterable<Row> rows, int stage) {
        return runTable(rows, stage, DEFAULT_SHORTEST);
    }

    /**
     * Count runs of every length from a given length up to the stage.
     *
     * @param rows the rows
     * @param stage the stage they are at
     * @param shortest the shortest run in the table
     * @return the counts for each length, shortest first
     */
    public static Map<Integer, RunCount> runTable(Iterable<Row> rows, int stage, int shortest) {
        List<Row> kept = new ArrayList<>();
        rows.forEach(kept::add);
        Map<Integer, RunCount> table = new LinkedHashMap<>();
        for (int length = shortest; length <= stage; length += 1) {
            table.put(length, countRuns(kept, length));
        }
        return Collections.unmodifiableMap(table);
    }

    /**
     * Walk out from one end of the row for as long as the bells keep going up by
     * one or down by one.
     */
    private static int longestRunEndingAt(int[] bells, int start, int step) {
        return Math.max(runFrom(bells, start, step, 1), runFrom(bells, start, step, -1));
    }

    private static int runFrom(int[] bells, int start, int step, int delta) {
        int length = 1;
        int index = start;
        while (index + step >= 0 && index + step < bells.length) {
            if (bells[index + step] != bells[index] + delta) {
                break;
            }
            length += 1;
            index += step;
        }
        return length;
    }

    private static void requireRow(Row row) {
        if (row == null) {
            throw new ValidationException("a Row is needed to look for runs in it");
        }
    }

    private static int requireLength(int length) {
        if (length < 2) {
            throw new ValidationException("a run of " + length + " is not something to count");
        }
        return length;
    }
}
