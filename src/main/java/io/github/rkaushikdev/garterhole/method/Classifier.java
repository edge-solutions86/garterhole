package io.github.rkaushikdev.garterhole.method;

import io.github.rkaushikdev.garterhole.core.Change;
import io.github.rkaushikdev.garterhole.core.Cycles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The class name of a method. Cambridge is Surprise, Kent is Treble Bob, Plain
 * Bob is Bob, and the difference between the three is not the name anybody gave
 * them but where the internal places fall against the treble's path.
 *
 * <p>Everything here is worked out from the changes. A collection that records
 * a class name is not consulted, because collections disagree with themselves.
 */
public final class Classifier {

    private Classifier() {}

    /**
     * @param place a place
     * @return the pair of places a bell is dodging in, counting pairs from the front
     */
    public static int sectionOf(int place) {
        return (place + 1) / 2;
    }

    /**
     * The indices of the changes where the hunt bell moves from one dodging
     * position to the next. These are the cross sections, and what happens at
     * them decides between Treble Bob, Delight and Surprise.
     *
     * @param path the places the hunt bell rings in
     * @return the indices of the cross sections
     */
    public static List<Integer> crossSections(List<Integer> path) {
        List<Integer> indices = new ArrayList<>();
        for (int index = 0; index + 1 < path.size(); index += 1) {
            if (sectionOf(path.get(index)) != sectionOf(path.get(index + 1))) {
                indices.add(index);
            }
        }
        return List.copyOf(indices);
    }

    /**
     * @param change a change
     * @return true when the change makes a place anywhere except the front and the back
     */
    public static boolean makesInternalPlace(Change change) {
        return change.places().stream().anyMatch(place -> place != 1 && place != change.stage());
    }

    /**
     * @param method a method
     * @param bell its hunt bell
     * @return true when the hunt bell never reaches the back, which makes the method little
     */
    public static boolean isLittle(Method method, int bell) {
        return Collections.max(HuntBells.pathOf(method, bell)) < method.stage();
    }

    /**
     * @param method a method
     * @return true when the working bells do not all belong to cycles of one length
     *     in the lead head, so they do not all ring the same line
     */
    public static boolean isDifferential(Method method) {
        Set<Integer> working = new HashSet<>(HuntBells.workingBells(method));
        if (working.isEmpty()) {
            return false;
        }
        Set<Integer> lengths = new HashSet<>();
        for (List<Integer> cycle : Cycles.cycles(method.leadHead())) {
            if (cycle.stream().anyMatch(working::contains)) {
                lengths.add(cycle.size());
            }
        }
        return lengths.size() > 1;
    }

    /**
     * The class name a method belongs to, without the Little and Differential
     * prefixes.
     *
     * @param method a method
     * @return for example {@code Surprise}, {@code Bob} or {@code Principle}
     * @throws IllegalArgumentException when there is no method
     */
    public static String baseClass(Method method) {
        HuntBells.requireMethod(method);
        List<Integer> hunts = HuntBells.huntBells(method);
        if (hunts.isEmpty()) {
            return "Principle";
        }
        int principal = hunts.get(0);
        List<Integer> path = HuntBells.pathOf(method, principal);
        return switch (HuntBells.huntPathKind(method, principal)) {
            case PLAIN -> hasWorkingDodges(method) ? "Bob" : "Place";
            case TREBLE_BOB -> trebleBobFamily(method, path);
            case TREBLE_PLACE -> HuntBells.dodgePlaces(path, method.stage()).isEmpty() ? "Treble Place" : "Alliance";
        };
    }

    /**
     * The full classification, including the prefixes and the parts that went
     * into the decision.
     *
     * @param method a method
     * @return the classification
     * @throws IllegalArgumentException when there is no method
     */
    public static Classification classify(Method method) {
        HuntBells.requireMethod(method);
        List<Integer> hunts = HuntBells.huntBells(method);
        String base = baseClass(method);
        boolean little = !hunts.isEmpty() && isLittle(method, hunts.get(0));
        boolean differential = isDifferential(method);
        List<String> parts = new ArrayList<>();
        if (differential) {
            parts.add("Differential");
        }
        if (little) {
            parts.add("Little");
        }
        if (!base.isEmpty()) {
            parts.add(base);
        }
        Optional<HuntPath> huntPath =
                hunts.isEmpty() ? Optional.empty() : Optional.of(HuntBells.huntPathKind(method, hunts.get(0)));
        return new Classification(String.join(" ", parts), base, little, differential, hunts, huntPath);
    }

    /**
     * Treble Bob, Delight and Surprise differ only in how many cross sections
     * have an internal place.
     */
    private static String trebleBobFamily(Method method, List<Integer> path) {
        List<Integer> sections = crossSections(path);
        if (sections.isEmpty()) {
            return "Treble Bob";
        }
        long withPlace = sections.stream()
                .filter(index -> makesInternalPlace(method.changes().get(index)))
                .count();
        if (withPlace == sections.size()) {
            return "Surprise";
        }
        if (withPlace == 0) {
            return "Treble Bob";
        }
        return "Delight";
    }

    /**
     * True when any working bell turns round away from the front and the back,
     * which is what separates a Bob method from a Place method.
     */
    private static boolean hasWorkingDodges(Method method) {
        return HuntBells.workingBells(method).stream()
                .anyMatch(bell -> !HuntBells.dodgePlaces(HuntBells.pathOf(method, bell), method.stage())
                        .isEmpty());
    }
}
