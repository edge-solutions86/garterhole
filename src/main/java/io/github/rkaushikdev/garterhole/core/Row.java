package io.github.rkaushikdev.garterhole.core;

import io.github.rkaushikdev.garterhole.util.Guards;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;

/**
 * A row is one strike of every bell in the tower, in the order they sound. It
 * is held as bell numbers indexed by place, so {@code bellAt(1)} is the bell
 * leading and {@code bellAt(stage())} is the bell lying behind.
 *
 * <p>Rows are immutable values. Every operation that would change one returns a
 * new row, which is what lets the prover hold a whole peal of them without
 * copying, and two rows with the same bells are equal and hash alike.
 */
public final class Row {

    private final int[] bells;

    /** The printed form, worked out the first time it is asked for. */
    private String printed;

    /**
     * @param bells bell numbers in striking order, a permutation of 1..n
     * @throws IllegalArgumentException when the bells are not a permutation
     */
    public Row(int... bells) {
        this.bells = bells.clone();
        requirePermutation(this.bells);
    }

    /**
     * @param bells bell numbers in striking order, a permutation of 1..n
     * @return the row
     * @throws IllegalArgumentException when the bells are not a permutation
     */
    public static Row of(Collection<Integer> bells) {
        int[] values = new int[bells.size()];
        int index = 0;
        for (Integer bell : bells) {
            values[index] = Guards.integerInRange((Object) bell, 1, bells.size(), "bell");
            index += 1;
        }
        return new Row(values);
    }

    /**
     * Rounds at a stage, the row every touch starts and ends on.
     *
     * @param stage a number of bells
     * @return every bell in its own place
     * @throws IllegalArgumentException when the stage is not supported
     */
    public static Row rounds(int stage) {
        if (!Stage.isSupported(stage)) {
            throw new IllegalArgumentException("stage " + stage + " is not supported");
        }
        return new Row(IntStream.rangeClosed(1, stage).toArray());
    }

    /**
     * @return the number of bells in the row
     */
    public int stage() {
        return bells.length;
    }

    /**
     * @return the bells in striking order, as a list that cannot be changed
     */
    public List<Integer> bells() {
        return Arrays.stream(bells).boxed().toList();
    }

    /**
     * The bell striking in a given place, counting places from one.
     *
     * @param place a place in the row
     * @return the bell in it
     * @throws IllegalArgumentException when the place is outside the row
     */
    public int bellAt(int place) {
        Guards.integerInRange(place, 1, stage(), "place");
        return bells[place - 1];
    }

    /**
     * The place a given bell is striking in, counting places from one.
     *
     * @param bell a bell
     * @return its place
     * @throws IllegalArgumentException when the bell is not ringing in this row
     */
    public int placeOf(int bell) {
        for (int index = 0; index < bells.length; index += 1) {
            if (bells[index] == bell) {
                return index + 1;
            }
        }
        throw new IllegalArgumentException("bell " + bell + " is not in a row of " + stage());
    }

    /**
     * @return true when every bell is in its own place
     */
    public boolean isRounds() {
        for (int index = 0; index < bells.length; index += 1) {
            if (bells[index] != index + 1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Rows are used as keys all over the prover, so they need a cheap stable
     * key. The printed form is already unique for a given stage.
     *
     * @return the printed form
     */
    public String key() {
        return toString();
    }

    /**
     * @return a copy of the bells that the caller may change
     */
    public int[] toArray() {
        return bells.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Row row && Arrays.equals(bells, row.bells);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bells);
    }

    /**
     * @return the row written the way it is printed, one symbol per bell
     */
    @Override
    public String toString() {
        String text = printed;
        if (text == null) {
            text = Bells.toSymbols(bells);
            printed = text;
        }
        return text;
    }

    /**
     * Check that the bells really are a permutation of 1..n and say what is
     * wrong when they are not. The message matters because most bad rows come
     * from a ringer typing a touch in by hand.
     */
    private static void requirePermutation(int[] bells) {
        if (bells.length == 0) {
            throw new IllegalArgumentException("a row must not be empty");
        }
        boolean[] seen = new boolean[bells.length + 1];
        for (int bell : bells) {
            Guards.integerInRange(bell, 1, bells.length, "bell");
            if (seen[bell]) {
                throw new IllegalArgumentException("bell " + bell + " appears more than once");
            }
            seen[bell] = true;
        }
    }
}
