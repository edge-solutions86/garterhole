package io.github.rkaushikdev.garterhole.method;

import io.github.rkaushikdev.garterhole.core.Change;
import io.github.rkaushikdev.garterhole.notation.NotationFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recognising the same method written two ways.
 *
 * <p>A lead can be written starting at any of its changes, and a method turned
 * upside down is a different method with its own name but the same shape. A
 * library that files both spellings of one method will build a splicing table
 * that cannot be rung, so it needs a form that is the same for every spelling.
 *
 * <p>The canonical form is the smallest of the rotations, in the plain order
 * that string comparison gives. That is arbitrary and it does not matter: what
 * matters is that it is the same for every rotation of the same lead.
 */
public final class Canonical {

    private Canonical() {}

    /**
     * @param changes a lead
     * @return every rotation of the lead, as notation, starting with the lead as written
     */
    public static List<String> rotations(List<Change> changes) {
        List<String> written = new ArrayList<>(changes.size());
        for (int offset = 0; offset < changes.size(); offset += 1) {
            List<Change> rotated = new ArrayList<>(changes.subList(offset, changes.size()));
            rotated.addAll(changes.subList(0, offset));
            written.add(NotationFormat.writeChanges(rotated));
        }
        return List.copyOf(written);
    }

    /**
     * @param changes a lead
     * @return the smallest of its rotations, or an empty string for no changes
     */
    public static String canonicalNotation(List<Change> changes) {
        List<String> all = rotations(changes);
        return all.isEmpty() ? "" : Collections.min(all);
    }

    /**
     * @param method a method
     * @return the canonical form with its stage in front, so that two stages never collide
     * @throws IllegalArgumentException when there is no method
     */
    public static String canonicalForm(Method method) {
        HuntBells.requireMethod(method);
        return method.stage() + ":" + canonicalNotation(method.changes());
    }

    /**
     * @param method a method
     * @return the canonical form of the method turned upside down
     * @throws IllegalArgumentException when there is no method
     */
    public static String reversedForm(Method method) {
        HuntBells.requireMethod(method);
        return method.stage() + ":" + canonicalNotation(Symmetry.reverseChanges(method.changes()));
    }

    /**
     * @param left one method
     * @param right another
     * @return true when they are the same lead written from different starting points
     */
    public static boolean isRotationOfMethod(Method left, Method right) {
        return canonicalForm(left).equals(canonicalForm(right));
    }

    /**
     * @param left one method
     * @param right another
     * @return true when one is the other turned upside down
     */
    public static boolean isReverseOfMethod(Method left, Method right) {
        return canonicalForm(left).equals(reversedForm(right));
    }

    /**
     * Group methods so that every spelling of one method lands together.
     *
     * @param methods some methods
     * @return the groups, keyed by canonical form, in the order first seen
     */
    public static Map<String, List<Method>> groupBySameLead(Iterable<Method> methods) {
        Map<String, List<Method>> groups = new LinkedHashMap<>();
        for (Method method : methods) {
            groups.computeIfAbsent(canonicalForm(method), key -> new ArrayList<>())
                    .add(method);
        }
        return groups;
    }

    /**
     * @param methods some methods
     * @return the methods that are spellings of something earlier in the list
     */
    public static List<Method> duplicateSpellings(Iterable<Method> methods) {
        Set<String> seen = new HashSet<>();
        List<Method> duplicates = new ArrayList<>();
        for (Method method : methods) {
            if (!seen.add(canonicalForm(method))) {
                duplicates.add(method);
            }
        }
        return List.copyOf(duplicates);
    }
}
