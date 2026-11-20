package io.github.rkaushikdev.garterhole.tower;

import io.github.rkaushikdev.garterhole.ValidationException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A set of intervals held in order and never overlapping. Adding an interval
 * that touches one already in the set merges the two, which is what makes the
 * arithmetic terminate.
 *
 * <p>A set grows as intervals are added to it; the intervals themselves never change.
 */
public final class IntervalSet implements Iterable<Interval> {

    private List<Interval> intervals = new ArrayList<>();

    /** An empty set. */
    public IntervalSet() {}

    /**
     * @param intervals the intervals to start with, in any order
     * @throws ValidationException when one of them is missing
     */
    public IntervalSet(Iterable<Interval> intervals) {
        for (Interval interval : intervals) {
            add(interval);
        }
    }

    /**
     * Add an interval, merging it with any it overlaps or meets.
     *
     * @param interval the interval
     * @return this set
     * @throws ValidationException when there is no interval
     */
    public IntervalSet add(Interval interval) {
        if (interval == null) {
            throw new ValidationException("only an Interval can go into an interval set");
        }
        double start = interval.start();
        double end = interval.end();
        List<Interval> kept = new ArrayList<>();
        for (Interval existing : intervals) {
            if (existing.end() < start || existing.start() > end) {
                kept.add(existing);
                continue;
            }
            start = Math.min(start, existing.start());
            end = Math.max(end, existing.end());
        }
        kept.add(new Interval(start, end));
        kept.sort(Comparator.comparingDouble(Interval::start));
        intervals = kept;
        return this;
    }

    /**
     * @param moment a moment
     * @return true when the set holds it
     */
    public boolean contains(double moment) {
        return intervals.stream().anyMatch(interval -> interval.contains(moment));
    }

    /**
     * @param interval an interval
     * @return true when the whole of it is in the set
     */
    public boolean covers(Interval interval) {
        return intervals.stream().anyMatch(existing -> existing.covers(interval));
    }

    /**
     * @param other another set
     * @return the parts the two sets have in common
     */
    public IntervalSet intersect(IntervalSet other) {
        IntervalSet result = new IntervalSet();
        for (Interval mine : intervals) {
            for (Interval theirs : other.intervals) {
                Optional<Interval> shared = mine.intersect(theirs);
                shared.ifPresent(result::add);
            }
        }
        return result;
    }

    /**
     * @return how much time the set holds altogether
     */
    public double total() {
        return intervals.stream().mapToDouble(Interval::length).sum();
    }

    /**
     * @return how many separate intervals the set holds
     */
    public int size() {
        return intervals.size();
    }

    /**
     * @return the intervals, earliest first, as a list that cannot be changed
     */
    public List<Interval> intervals() {
        return List.copyOf(intervals);
    }

    @Override
    public Iterator<Interval> iterator() {
        return intervals().iterator();
    }

    /**
     * @return the intervals written out in order, separated by spaces
     */
    @Override
    public String toString() {
        return intervals.stream().map(Interval::toString).collect(Collectors.joining(" "));
    }
}
