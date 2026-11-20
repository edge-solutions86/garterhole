package io.github.rkaushikdev.garterhole.method;

import io.github.rkaushikdev.garterhole.core.Change;
import io.github.rkaushikdev.garterhole.notation.Expansion;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Methods are named partly for the symmetries they have. A method that reads
 * the same backwards is palindromic and is much easier to learn; one that looks
 * the same turned upside down is double; one that is unchanged by doing both at
 * once has rotational symmetry.
 *
 * <p>All three are decided from the changes, and all three allow for the lead
 * being written starting at a different point, because where a lead starts is a
 * matter of convention rather than of the method.
 */
public final class Symmetry {

    private Symmetry() {}

    /**
     * Every symmetry a method has.
     *
     * @param palindromic true when the lead reads the same backwards
     * @param doubled true when the lead is unchanged turned upside down, which
     *     ringers call a double method
     * @param rotational true when it is unchanged by doing both at once
     * @param names the names of the symmetries it has, in that order
     */
    public record Facts(boolean palindromic, boolean doubled, boolean rotational, List<String> names) {

        /**
         * @param palindromic true for a palindromic lead
         * @param doubled true for a double method
         * @param rotational true for rotational symmetry
         * @param names the names of the symmetries it has
         */
        public Facts {
            names = List.copyOf(names);
        }
    }

    /**
     * Turn a change upside down, so that a place in seconds becomes a place in
     * the second from the back.
     *
     * @param change a change
     * @return the change turned upside down
     */
    public static Change reverseChange(Change change) {
        List<Integer> places = change.places().stream()
                .map(place -> change.stage() + 1 - place)
                .sorted()
                .toList();
        return new Change(change.stage(), places);
    }

    /**
     * @param changes a lead
     * @return the lead turned upside down
     */
    public static List<Change> reverseChanges(List<Change> changes) {
        return changes.stream().map(Symmetry::reverseChange).toList();
    }

    /**
     * @param left one run of changes
     * @param right another
     * @return true when one is a rotation of the other
     */
    public static boolean isRotationOf(List<Change> left, List<Change> right) {
        if (left.size() != right.size()) {
            return false;
        }
        int length = left.size();
        for (int offset = 0; offset < length; offset += 1) {
            boolean matched = true;
            for (int index = 0; index < length; index += 1) {
                if (!left.get(index).equals(right.get((index + offset) % length))) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param method a method
     * @return true when the lead reads the same forwards as backwards
     * @throws IllegalArgumentException when there is no method
     */
    public static boolean isPalindromic(Method method) {
        HuntBells.requireMethod(method);
        return !Expansion.symmetryPoints(method.changes()).isEmpty();
    }

    /**
     * @param method a method
     * @return true when the lead is unchanged by turning it upside down
     * @throws IllegalArgumentException when there is no method
     */
    public static boolean isDouble(Method method) {
        HuntBells.requireMethod(method);
        return isRotationOf(method.changes(), reverseChanges(method.changes()));
    }

    /**
     * @param method a method
     * @return true when the lead is unchanged by turning it upside down and
     *     running it backwards at the same time
     * @throws IllegalArgumentException when there is no method
     */
    public static boolean hasRotationalSymmetry(Method method) {
        HuntBells.requireMethod(method);
        List<Change> flipped = new ArrayList<>(reverseChanges(method.changes()));
        Collections.reverse(flipped);
        return isRotationOf(method.changes(), flipped);
    }

    /**
     * @param method a method
     * @return every symmetry the method has
     * @throws IllegalArgumentException when there is no method
     */
    public static Facts symmetryOf(Method method) {
        boolean palindromic = isPalindromic(method);
        boolean doubled = isDouble(method);
        boolean rotational = hasRotationalSymmetry(method);
        List<String> names = new ArrayList<>();
        if (palindromic) {
            names.add("palindromic");
        }
        if (doubled) {
            names.add("double");
        }
        if (rotational) {
            names.add("rotational");
        }
        return new Facts(palindromic, doubled, rotational, names);
    }
}
