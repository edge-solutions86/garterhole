package io.github.rkaushikdev.garterhole.method;

import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.util.CollectionHelpers;
import io.github.rkaushikdev.garterhole.util.LocaleOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The numbers that go beside a method in a collection.
 *
 * <p>Place bell order is the useful one and the one nobody works out by hand
 * correctly. It says which place bell a ringer becomes at the end of each lead,
 * which is exactly what a ringer has to know to get through a touch, and it
 * follows from the lead head with no room for opinion.
 */
public final class Stats {

    private Stats() {}

    /**
     * The place bell order: starting from the first working place bell, which
     * place bell each one becomes at the lead head, until it comes back.
     *
     * @param method a method
     * @return the place bells in order, empty when every bell hunts
     * @throws IllegalArgumentException when there is no method
     */
    public static List<Integer> placeBellOrder(Method method) {
        HuntBells.requireMethod(method);
        Row head = method.leadHead();
        List<Integer> hunt = HuntBells.huntBells(method);
        int place = 1;
        while (place <= method.stage() && hunt.contains(place)) {
            place += 1;
        }
        if (place > method.stage()) {
            return List.of();
        }
        int start = place;
        int current = start;
        List<Integer> order = new ArrayList<>();
        do {
            order.add(current);
            current = head.placeOf(current);
        } while (current != start && order.size() <= method.stage());
        return List.copyOf(order);
    }

    /**
     * How many blows a bell spends in each place over a whole course, which is
     * what a band means by saying a method is front work heavy.
     *
     * @param method a method
     * @param bell a bell
     * @return one entry per place, starting at place one
     * @throws IllegalArgumentException when there is no method
     */
    public static List<Integer> blowsOverCourse(Method method, int bell) {
        HuntBells.requireMethod(method);
        List<Integer> path = PathTrace.coursePath(method, bell);
        return HuntBells.countPlaces(path.subList(0, path.size() - 1), method.stage());
    }

    /**
     * How many of each piece of work a bell does in a course.
     *
     * @param method a method
     * @param bell a bell
     * @return the count of each kind of work, keyed by its name, in name order
     */
    public static Map<String, Integer> workCounts(Method method, int bell) {
        Map<String, Integer> counts = new TreeMap<>(LocaleOrder.ORDER);
        CollectionHelpers.countBy(
                        PathTrace.workOf(method, bell), piece -> piece.kind().toString())
                .forEach(counts::put);
        return Collections.unmodifiableMap(new LinkedHashMap<>(counts));
    }

    /**
     * Everything a collection prints beside a method.
     *
     * @param method a method
     * @return the statistics
     * @throws IllegalArgumentException when there is no method
     */
    public static MethodStats methodStats(Method method) {
        HuntBells.requireMethod(method);
        Classification facts = Classifier.classify(method);
        return new MethodStats(
                method.name() + " (" + method.stageName() + ")",
                method.stage(),
                method.leadLength(),
                PlainCourse.leadsInCourse(method),
                PlainCourse.courseLength(method),
                facts.huntBells(),
                HuntBells.workingBells(method),
                placeBellOrder(method),
                facts.className());
    }

    /**
     * True when the bell strikes the same number of blows in every place over a
     * lead, which is what makes a hunt bell path even.
     *
     * @param method a method
     * @param bell a bell
     * @return true when every place gets the same number of blows
     */
    public static boolean pathIsEven(Method method, int bell) {
        List<Integer> counts = HuntBells.blowsInEachPlace(method, bell);
        return counts.stream().allMatch(count -> count.equals(counts.get(0)));
    }
}
