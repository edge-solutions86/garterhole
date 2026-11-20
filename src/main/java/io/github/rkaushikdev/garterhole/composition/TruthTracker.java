package io.github.rkaushikdev.garterhole.composition;

import io.github.rkaushikdev.garterhole.core.Row;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A running check on truth. Rows are handed in one at a time and the tracker
 * says at once whether the row has been rung before, so a search that is
 * building a composition can stop at the first repeat rather than ringing the
 * whole thing and proving it afterwards.
 *
 * <p>A tracker belongs to one search or one proof and is not safe to share
 * between threads.
 */
public final class TruthTracker {

    /** Every index each row was offered at, in the order offered. */
    private final Map<Row, List<Integer>> seen = new HashMap<>();

    private int count;
    private int repeated;

    /** An empty tracker, which is true. */
    public TruthTracker() {
        // Rows are offered with add.
    }

    /**
     * A row rung more than once, and where.
     *
     * @param row the row, as printed
     * @param at every index it was rung at, from zero
     */
    public record Repeat(String row, List<Integer> at) {}

    /**
     * Offer a row.
     *
     * @param row the next row
     * @return false when the row has been rung before
     */
    public boolean add(Row row) {
        List<Integer> at = seen.get(row);
        if (at == null) {
            List<Integer> first = new ArrayList<>(1);
            first.add(count);
            seen.put(row, first);
            count += 1;
            return true;
        }
        if (at.size() == 1) {
            repeated += 1;
        }
        at.add(count);
        count += 1;
        return false;
    }

    /**
     * Take back the last offer of a row, so that a search can try another branch
     * from where this one started. Rows are taken back in the reverse of the
     * order they were offered.
     *
     * @param row a row offered earlier
     */
    void undo(Row row) {
        List<Integer> at = seen.get(row);
        if (at == null) {
            return;
        }
        at.remove(at.size() - 1);
        count -= 1;
        if (at.isEmpty()) {
            seen.remove(row);
        } else if (at.size() == 1) {
            repeated -= 1;
        }
    }

    /**
     * @return true when no row has been offered twice
     */
    public boolean isTrue() {
        return repeated == 0;
    }

    /**
     * @return the repeats found so far, in the order they started
     */
    public List<Repeat> repeats() {
        List<Repeat> repeats = new ArrayList<>();
        seen.forEach((row, at) -> {
            if (at.size() > 1) {
                repeats.add(new Repeat(row.toString(), List.copyOf(at)));
            }
        });
        repeats.sort(Comparator.comparingInt(repeat -> repeat.at().get(0)));
        return List.copyOf(repeats);
    }

    /**
     * @return how many rows have been offered, repeats included
     */
    public int count() {
        return count;
    }

    /**
     * @return how many different rows have been offered
     */
    public int distinct() {
        return seen.size();
    }
}
