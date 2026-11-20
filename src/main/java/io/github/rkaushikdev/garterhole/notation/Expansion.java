package io.github.rkaushikdev.garterhole.notation;

import io.github.rkaushikdev.garterhole.NotationException;
import io.github.rkaushikdev.garterhole.core.Change;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;

/**
 * Folding palindromic notation out into whole leads.
 *
 * <p>Most methods are palindromes, and the printed literature takes advantage
 * of that by writing only half of the lead. {@code x1x1x1,2} is a whole lead of
 * Plain Bob Minor written in seven changes instead of twelve.
 *
 * <p>The rule is the same for every section a comma divides: write the section
 * out, then write it again backwards leaving off the change it ended on. The
 * change that is left off is the one on the axis of the mirror, and repeating
 * it would ring it twice.
 */
public final class Expansion {

    private Expansion() {}

    /**
     * Mirror a single section about its last change.
     *
     * @param section the changes of the section
     * @return the section followed by its reflection
     */
    public static List<Change> mirrorSection(List<Change> section) {
        if (section.isEmpty()) {
            return List.of();
        }
        List<Change> mirrored = new ArrayList<>(section);
        for (int index = section.size() - 2; index >= 0; index -= 1) {
            mirrored.add(section.get(index));
        }
        return List.copyOf(mirrored);
    }

    /**
     * Expand the sections of a folded notation into the changes of a whole
     * lead. A notation with only one section is already written out and is
     * returned as it stands.
     *
     * @param sections the sections
     * @return the changes of the lead
     * @throws NotationException when there are no sections
     */
    public static List<Change> expandSections(List<List<Change>> sections) {
        if (sections == null || sections.isEmpty()) {
            throw new NotationException("there is nothing to expand");
        }
        if (sections.size() == 1) {
            // A copy of its own, so that a caller holding the section and the lead holds two lists.
            return Collections.unmodifiableList(new ArrayList<>(sections.get(0)));
        }
        List<Change> changes = new ArrayList<>();
        for (List<Change> section : sections) {
            changes.addAll(mirrorSection(section));
        }
        return List.copyOf(changes);
    }

    /**
     * Read notation and give back the changes of one whole lead.
     *
     * @param text the notation
     * @param stage the stage
     * @return the changes of the lead
     */
    public static List<Change> expandNotation(String text, int stage) {
        return expandSections(NotationParser.parseNotation(text, stage).sections());
    }

    /**
     * True when a run of changes reads the same forwards as backwards about the
     * two points a folded notation implies. This is what makes a method
     * palindromic, and it is the property the classification rules lean on.
     *
     * @param changes the changes of a lead
     * @return true when the lead has an axis
     */
    public static boolean isPalindromic(List<Change> changes) {
        return findSymmetryPoint(changes).isPresent();
    }

    /**
     * @param changes the changes of a lead
     * @return the index of the change on the first axis, when there is one
     */
    public static OptionalInt findSymmetryPoint(List<Change> changes) {
        List<Integer> axes = symmetryPoints(changes);
        return axes.isEmpty() ? OptionalInt.empty() : OptionalInt.of(axes.get(0));
    }

    /**
     * Every index the lead reflects about, ascending. A palindromic lead has
     * two of them, one at each end of the mirror, and the pair is what the
     * notation writer needs in order to fold the lead back up.
     *
     * @param changes the changes of a lead
     * @return the indexes of the changes on an axis
     */
    public static List<Integer> symmetryPoints(List<Change> changes) {
        List<Integer> axes = new ArrayList<>();
        for (int axis = 0; axis < changes.size(); axis += 1) {
            if (reflectsAbout(changes, axis)) {
                axes.add(axis);
            }
        }
        return List.copyOf(axes);
    }

    /**
     * Rotate a lead so that its axis of symmetry sits at the end of the first
     * section, which is where a folded notation expects to find it.
     *
     * @param changes the changes of a lead
     * @param axis the index of the change on the axis
     * @return the rotated lead
     * @throws IllegalArgumentException when the axis is outside the lead
     */
    public static List<Change> rotateToAxis(List<Change> changes, int axis) {
        if (axis < 0 || axis >= changes.size()) {
            throw new IllegalArgumentException("axis " + axis + " is outside 0.." + (changes.size() - 1));
        }
        List<Change> rotated = new ArrayList<>(changes.subList(axis + 1, changes.size()));
        rotated.addAll(changes.subList(0, axis + 1));
        return List.copyOf(rotated);
    }

    private static boolean reflectsAbout(List<Change> changes, int axis) {
        int count = changes.size();
        for (int step = 1; step < count; step += 1) {
            Change left = changes.get(Math.floorMod(axis - step, count));
            Change right = changes.get((axis + step) % count);
            if (!left.equals(right)) {
                return false;
            }
        }
        return true;
    }
}
