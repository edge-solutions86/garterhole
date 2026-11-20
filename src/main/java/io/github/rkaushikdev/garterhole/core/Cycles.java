package io.github.rkaushikdev.garterhole.core;

import io.github.rkaushikdev.garterhole.util.Guards;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A row is a permutation, and every permutation splits into cycles. The cycle
 * structure answers three questions the rest of the library keeps asking: how
 * many parts a composition has, whether a row can be reached at a handstroke,
 * and which bells a part end keeps together.
 */
public final class Cycles {

    private Cycles() {}

    /** Whether a row takes an even or an odd number of swaps to reach rounds. */
    public enum Parity {
        /** An even number of swaps; the row is in course. */
        EVEN,
        /** An odd number of swaps; the row is out of course. */
        ODD;

        @Override
        public String toString() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * Split a row into its cycles. Each cycle is a list of bells, starting with
     * the lowest numbered bell in that cycle, and the cycles come out ordered by
     * that first bell. Bells that stay in their own place appear as cycles of one.
     *
     * @param row a row
     * @return the cycles
     */
    public static List<List<Integer>> cycles(Row row) {
        Guards.present(row, "a row is needed to work out its cycles");
        boolean[] seen = new boolean[row.stage() + 1];
        List<List<Integer>> found = new ArrayList<>();
        for (int bell = 1; bell <= row.stage(); bell += 1) {
            if (seen[bell]) {
                continue;
            }
            List<Integer> cycle = new ArrayList<>();
            int current = bell;
            while (!seen[current]) {
                seen[current] = true;
                cycle.add(current);
                current = row.bellAt(current);
            }
            found.add(List.copyOf(cycle));
        }
        return List.copyOf(found);
    }

    /**
     * The lengths of the cycles, longest first. This is the shape of the
     * permutation, and two rows with the same shape behave the same way when
     * they are used as part ends.
     *
     * @param row a row
     * @return the cycle lengths, longest first
     */
    public static List<Integer> cycleLengths(Row row) {
        return cycles(row).stream()
                .map(List::size)
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    /**
     * How many times a row has to be applied to itself before it comes back to
     * rounds, worked out from the cycle lengths rather than by walking the group.
     *
     * @param row a row
     * @return the order
     */
    public static int orderOf(Row row) {
        int order = 1;
        for (int length : cycleLengths(row)) {
            order = leastCommonMultiple(order, length);
        }
        return order;
    }

    /**
     * @param row a row
     * @return even when it takes an even number of swaps to get back to rounds
     */
    public static Parity parity(Row row) {
        int swaps = cycles(row).stream().mapToInt(cycle -> cycle.size() - 1).sum();
        return swaps % 2 == 0 ? Parity.EVEN : Parity.ODD;
    }

    /**
     * True when the row is in course. Composers split the course heads of a
     * method into the in course half and the out of course half, and a call
     * that changes the parity moves between the two halves.
     *
     * <p>Parity does not simply alternate row by row. A change swaps
     * {@code (stage - places) / 2} pairs, and that count is odd for some changes
     * and even for others, so a lead of Plain Bob Minor moves between the
     * halves at the cross changes and stays put at the place changes.
     *
     * @param row a row
     * @return true when the row is even
     */
    public static boolean isInCourse(Row row) {
        return parity(row) == Parity.EVEN;
    }

    /**
     * The bells that stay in their own place. On a part end these are the bells
     * the composition never moves, which is usually the treble and the tenors.
     *
     * @param row a row
     * @return the fixed bells, ascending
     */
    public static List<Integer> fixedBells(Row row) {
        List<Integer> fixed = new ArrayList<>();
        for (int bell = 1; bell <= row.stage(); bell += 1) {
            if (row.bellAt(bell) == bell) {
                fixed.add(bell);
            }
        }
        return List.copyOf(fixed);
    }

    /**
     * Write the cycle structure the way a composer would, for example
     * {@code (234)(56)}, leaving out the bells that do not move.
     *
     * @param row a row
     * @return the cycles written out, or {@code ()} for rounds
     */
    public static String writeCycles(Row row) {
        List<List<Integer>> moving =
                cycles(row).stream().filter(cycle -> cycle.size() > 1).toList();
        if (moving.isEmpty()) {
            return "()";
        }
        return moving.stream()
                .map(cycle -> "(" + cycle.stream().map(String::valueOf).collect(Collectors.joining()) + ")")
                .collect(Collectors.joining());
    }

    private static int leastCommonMultiple(int left, int right) {
        return left / greatestCommonDivisor(left, right) * right;
    }

    private static int greatestCommonDivisor(int left, int right) {
        int a = left;
        int b = right;
        while (b != 0) {
            int remainder = a % b;
            a = b;
            b = remainder;
        }
        return a;
    }
}
