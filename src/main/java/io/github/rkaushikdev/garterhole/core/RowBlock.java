package io.github.rkaushikdev.garterhole.core;

import io.github.rkaushikdev.garterhole.ValidationException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A block of rows is what a touch actually is once it has been rung out. Every
 * row is at the same stage, and a block built from changes holds the row it
 * started on as well as every row after it.
 *
 * <p>Blocks are built once and read many times. The prover walks one row by
 * row, the striking analysis lines one up against a stream of strike times, and
 * the music scorer counts patterns in it, so lookup has to be cheap and nothing
 * may change underneath those three.
 */
public final class RowBlock implements Iterable<Row> {

    private final List<Row> rows;
    private final int stage;

    /**
     * @param rows the rows, at least one and all at the same stage
     * @throws ValidationException when the block would be empty or mixes stages
     */
    public RowBlock(List<Row> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new ValidationException("a block must contain at least one row");
        }
        List<Row> copy = new ArrayList<>(rows.size());
        int first = -1;
        for (int index = 0; index < rows.size(); index += 1) {
            Row row = rows.get(index);
            if (row == null) {
                throw new ValidationException("entry " + index + " of the block is not a row");
            }
            if (first == -1) {
                first = row.stage();
            } else if (row.stage() != first) {
                throw new ValidationException(
                        "row " + index + " is on " + row.stage() + " bells but the block is on " + first);
            }
            copy.add(row);
        }
        this.rows = Collections.unmodifiableList(copy);
        this.stage = first;
    }

    /**
     * Ring a run of changes from a starting row.
     *
     * @param start the row before the first change
     * @param changes the changes, in order
     * @return the block, holding the start and one row per change
     * @throws ValidationException when there is no starting row
     */
    public static RowBlock fromChanges(Row start, List<Change> changes) {
        if (start == null) {
            throw new ValidationException("a block starts from a row");
        }
        List<Row> rows = new ArrayList<>(changes.size() + 1);
        rows.add(start);
        Row current = start;
        for (Change change : changes) {
            current = change.apply(current);
            rows.add(current);
        }
        return new RowBlock(rows);
    }

    /**
     * @return the rows, in order, as a list that cannot be changed
     */
    public List<Row> rows() {
        return rows;
    }

    /**
     * @return the stage every row is at
     */
    public int stage() {
        return stage;
    }

    /**
     * How many rows the block holds. A lead of Plain Bob Minor rung from rounds
     * is thirteen rows long, because it holds both the row it started on and
     * the row it finished on.
     *
     * @return the number of rows
     */
    public int length() {
        return rows.size();
    }

    /**
     * @return the number of changes, which is what a peal length counts
     */
    public int changeCount() {
        return rows.size() - 1;
    }

    /**
     * @return the row the block starts on
     */
    public Row first() {
        return rows.get(0);
    }

    /**
     * @return the row the block ends on
     */
    public Row last() {
        return rows.get(rows.size() - 1);
    }

    /**
     * @param index counting from zero
     * @return the row at that index
     * @throws IllegalArgumentException when the index is outside the block
     */
    public Row at(int index) {
        if (index < 0 || index >= rows.size()) {
            throw new IllegalArgumentException("index " + index + " is outside 0.." + (rows.size() - 1));
        }
        return rows.get(index);
    }

    /**
     * @return the changes that take each row to the next
     */
    public List<Change> changes() {
        List<Change> changes = new ArrayList<>(rows.size() - 1);
        for (int index = 1; index < rows.size(); index += 1) {
            changes.add(changeBetween(rows.get(index - 1), rows.get(index)));
        }
        return changes;
    }

    /**
     * True when no row appears twice. Truth is the property a composition lives
     * or dies by, though the prover has a faster way of deciding it for a whole
     * peal than building a block first.
     *
     * @return true when every row is different
     */
    public boolean isTrue() {
        Set<Row> seen = new HashSet<>();
        for (Row row : rows) {
            if (!seen.add(row)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return true when the block ends where it started, which is what a touch
     *     has to do before anybody will call it rung
     */
    public boolean comesRound() {
        return first().equals(last());
    }

    /**
     * @param row a row
     * @return true when the block holds it
     */
    public boolean contains(Row row) {
        return rows.contains(row);
    }

    /**
     * The rows from an index to the end. A negative index counts from the end.
     *
     * @param start the first index taken
     * @return the smaller block
     */
    public RowBlock slice(int start) {
        return slice(start, rows.size());
    }

    /**
     * The rows from one index up to but not including another. A negative index
     * counts from the end, and an index beyond the block stops at its end.
     *
     * @param start the first index taken
     * @param end the first index not taken
     * @return the smaller block
     * @throws ValidationException when nothing is left
     */
    public RowBlock slice(int start, int end) {
        int from = clamp(start);
        int to = clamp(end);
        return new RowBlock(from >= to ? List.of() : rows.subList(from, to));
    }

    /**
     * Join two blocks. The second block has to start on the row the first one
     * ended on, and that shared row appears only once in the answer.
     *
     * @param other the block that follows
     * @return the joined block
     * @throws ValidationException when the blocks do not meet
     */
    public RowBlock concat(RowBlock other) {
        if (other == null) {
            throw new ValidationException("a block is joined to another block");
        }
        if (!last().equals(other.first())) {
            throw new ValidationException("block ends on " + last() + " but the next one starts on " + other.first());
        }
        List<Row> joined = new ArrayList<>(rows.size() + other.rows.size() - 1);
        joined.addAll(rows);
        joined.addAll(other.rows.subList(1, other.rows.size()));
        return new RowBlock(joined);
    }

    @Override
    public Iterator<Row> iterator() {
        return rows.iterator();
    }

    /**
     * Work out the change that takes one row to the next. Two rows that are not
     * one change apart have no answer, and saying so is more useful than
     * inventing one.
     *
     * @param from the row before
     * @param to the row after
     * @return the change between them
     * @throws ValidationException when no single change takes one to the other
     */
    public static Change changeBetween(Row from, Row to) {
        if (from.stage() != to.stage()) {
            throw new ValidationException("cannot change from " + from.stage() + " bells to " + to.stage());
        }
        List<Integer> places = new ArrayList<>();
        int place = 1;
        while (place <= from.stage()) {
            if (from.bellAt(place) == to.bellAt(place)) {
                places.add(place);
                place += 1;
                continue;
            }
            if (place == from.stage()
                    || from.bellAt(place) != to.bellAt(place + 1)
                    || from.bellAt(place + 1) != to.bellAt(place)) {
                throw new ValidationException(from + " and " + to + " are not one change apart");
            }
            place += 2;
        }
        return new Change(from.stage(), places);
    }

    private int clamp(int index) {
        int size = rows.size();
        int resolved = index < 0 ? size + index : index;
        return Math.max(0, Math.min(size, resolved));
    }
}
