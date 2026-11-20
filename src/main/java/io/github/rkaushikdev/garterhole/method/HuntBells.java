package io.github.rkaushikdev.garterhole.method;

import io.github.rkaushikdev.garterhole.core.Cycles;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.util.Guards;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hunt bells and the paths bells take through a lead.
 *
 * <p>A hunt bell is one that rings the same path in every lead. That is the
 * same thing as being left in its own place by the lead head, which is how it is
 * worked out here rather than by comparing two leads row by row.
 *
 * <p>Everything the classifier does starts from this. A method with one hunt
 * bell is a treble dominated method and gets a class name like Surprise; a
 * method with none is a principle and gets no class name at all.
 */
public final class HuntBells {

    private HuntBells() {}

    /**
     * @param method a method
     * @return the hunt bells, ascending
     * @throws IllegalArgumentException when there is no method
     */
    public static List<Integer> huntBells(Method method) {
        requireMethod(method);
        return Cycles.fixedBells(method.leadHead());
    }

    /**
     * The bells that are not hunt bells. These are the ones a composition moves
     * around, and the ones a caller has to keep track of.
     *
     * @param method a method
     * @return the working bells, ascending
     */
    public static List<Integer> workingBells(Method method) {
        List<Integer> hunt = huntBells(method);
        List<Integer> working = new ArrayList<>();
        for (int bell = 1; bell <= method.stage(); bell += 1) {
            if (!hunt.contains(bell)) {
                working.add(bell);
            }
        }
        return List.copyOf(working);
    }

    /**
     * @param method a method
     * @return true when the method has no hunt bell, which makes it a principle
     */
    public static boolean isPrinciple(Method method) {
        return huntBells(method).isEmpty();
    }

    /**
     * The places a bell rings in, one for each row of a plain lead including
     * both ends.
     *
     * @param method a method
     * @param bell a bell
     * @return the places, in order
     * @throws IllegalArgumentException when there is no method
     */
    public static List<Integer> pathOf(Method method, int bell) {
        requireMethod(method);
        List<Row> rows = method.plainLead().rows();
        List<Integer> path = new ArrayList<>(rows.size());
        for (Row row : rows) {
            path.add(row.placeOf(bell));
        }
        return List.copyOf(path);
    }

    /**
     * The places at which the bell turns round, leaving out the two turns every
     * bell makes at the front and the back. What is left is the dodges.
     *
     * @param path the places a bell rings in
     * @param stage the place counted as the back
     * @return the places it turns in
     */
    public static List<Integer> dodgePlaces(List<Integer> path, int stage) {
        List<Integer> places = new ArrayList<>();
        int previous = 0;
        for (int index = 0; index + 1 < path.size(); index += 1) {
            int place = path.get(index);
            int direction = Integer.signum(path.get(index + 1) - place);
            if (direction == 0) {
                continue;
            }
            if (previous != 0 && direction != previous && place != 1 && place != stage) {
                places.add(place);
            }
            previous = direction;
        }
        return List.copyOf(places);
    }

    /**
     * The places where the bell rings two blows running, again leaving out the
     * front and the back where every bell does it.
     *
     * @param path the places a bell rings in
     * @param stage the place counted as the back
     * @return the places it makes
     */
    public static List<Integer> placesMade(List<Integer> path, int stage) {
        List<Integer> places = new ArrayList<>();
        for (int index = 0; index + 1 < path.size(); index += 1) {
            int place = path.get(index);
            if (place == path.get(index + 1) && place != 1 && place != stage) {
                places.add(place);
            }
        }
        return List.copyOf(places);
    }

    /**
     * Describe the shape of a hunt bell's path. The answer decides most of the
     * method's class name.
     *
     * <p>The turns at the top and the bottom of the path are the ones every
     * bell makes, so they are not counted. In a little method the top of the
     * path is not the back of the row, which is why the highest place the bell
     * reaches is used rather than the stage. Getting that wrong makes Little Bob
     * look like an Alliance method.
     *
     * @param method a method
     * @param bell the hunt bell
     * @return the kind of path
     */
    public static HuntPath huntPathKind(Method method, int bell) {
        List<Integer> path = pathOf(method, bell);
        int top = Collections.max(path);
        if (!placesMade(path, top).isEmpty()) {
            return HuntPath.TREBLE_PLACE;
        }
        if (!dodgePlaces(path, top).isEmpty()) {
            return HuntPath.TREBLE_BOB;
        }
        return HuntPath.PLAIN;
    }

    /**
     * How many blows the bell spends in each place over a lead. Used by the
     * reports, and by the check that a hunt bell really does cover the whole
     * row rather than sitting in one place.
     *
     * @param method a method
     * @param bell a bell
     * @return one entry per place, starting at place one
     */
    public static List<Integer> blowsInEachPlace(Method method, int bell) {
        List<Integer> path = pathOf(method, bell);
        return countPlaces(path.subList(0, path.size() - 1), method.stage());
    }

    static List<Integer> countPlaces(List<Integer> path, int stage) {
        int[] counts = new int[stage];
        for (int place : path) {
            counts[place - 1] += 1;
        }
        List<Integer> answer = new ArrayList<>(stage);
        for (int count : counts) {
            answer.add(count);
        }
        return List.copyOf(answer);
    }

    /** Every module here refuses to work without a method, with the same complaint. */
    static void requireMethod(Method method) {
        Guards.present(method, "a Method is needed here");
    }
}
