package io.github.rkaushikdev.garterhole.core;

import io.github.rkaushikdev.garterhole.NotationException;
import io.github.rkaushikdev.garterhole.util.Guards;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * A change is what happens between one row and the next. Every bell either
 * stays where it is, which ringers call making a place, or swaps with the bell
 * next to it. A change is therefore completely described by the set of places
 * that are made.
 *
 * <p>The awkward part is that the places have to leave an even number of bells
 * in every gap, because the bells in a gap can only pair off. A change that
 * does not is not a change at all, and this class refuses to build one.
 */
public final class Change {

    private final int stage;
    private final List<Integer> places;
    private final boolean[] made;

    /**
     * @param stage a supported stage
     * @param places places made, one based, in any order and with repeats allowed
     * @throws IllegalArgumentException when the stage is unsupported or a place is outside it
     * @throws NotationException when the places would leave an odd gap
     */
    public Change(int stage, List<Integer> places) {
        requireStage(stage);
        List<Integer> sorted = normalisePlaces(stage, places);
        requirePairable(stage, sorted);
        this.stage = stage;
        this.places = sorted;
        this.made = new boolean[stage + 1];
        for (int place : sorted) {
            made[place] = true;
        }
    }

    /**
     * @param stage a supported stage
     * @param places places made, one based
     * @return the change
     */
    public static Change of(int stage, int... places) {
        return new Change(stage, Arrays.stream(places).boxed().toList());
    }

    /**
     * The change where nothing makes a place and every bell swaps. Only
     * possible at an even stage.
     *
     * @param stage a supported stage
     * @return the cross change
     */
    public static Change cross(int stage) {
        return new Change(stage, List.of());
    }

    /**
     * Build a change, filling in the places at the front and the back that the
     * printed notation leaves out. Writing {@code 1} on minor means places one
     * and six, because otherwise the five bells above the treble cannot pair off.
     *
     * @param stage a supported stage
     * @param places the places written
     * @return the completed change
     */
    public static Change withImpliedPlaces(int stage, List<Integer> places) {
        requireStage(stage);
        List<Integer> completed = new ArrayList<>(normalisePlaces(stage, places));
        if (completed.isEmpty()) {
            if (stage % 2 == 1) {
                completed.add(stage);
            }
        } else {
            if ((completed.get(0) - 1) % 2 == 1) {
                completed.add(0, 1);
            }
            if ((stage - completed.get(completed.size() - 1)) % 2 == 1) {
                completed.add(stage);
            }
        }
        return new Change(stage, completed);
    }

    /**
     * @param stage a supported stage
     * @param places the places written
     * @return the completed change
     */
    public static Change withImpliedPlaces(int stage, int... places) {
        return withImpliedPlaces(stage, Arrays.stream(places).boxed().toList());
    }

    /**
     * @return the stage the change is rung at
     */
    public int stage() {
        return stage;
    }

    /**
     * @return the places made, ascending, as a list that cannot be changed
     */
    public List<Integer> places() {
        return places;
    }

    /**
     * @return true when no bell makes a place
     */
    public boolean isCross() {
        return places.isEmpty();
    }

    /**
     * @param place a place
     * @return true when a place is made there
     */
    public boolean makesPlace(int place) {
        return place >= 1 && place <= stage && made[place];
    }

    /**
     * Ring the change, giving the row that follows the one supplied.
     *
     * @param row the row before
     * @return the row after
     * @throws IllegalArgumentException when there is no row, or it is at another stage
     */
    public Row apply(Row row) {
        Guards.present(row, "a change is applied to a Row");
        if (row.stage() != stage) {
            throw new IllegalArgumentException(
                    "a change on " + stage + " cannot be applied to a row of " + row.stage());
        }
        int[] bells = row.toArray();
        int place = 1;
        while (place <= stage) {
            if (made[place]) {
                place += 1;
                continue;
            }
            int held = bells[place - 1];
            bells[place - 1] = bells[place];
            bells[place] = held;
            place += 2;
        }
        return new Row(bells);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Change change && change.stage == stage && change.places.equals(places);
    }

    @Override
    public int hashCode() {
        return 31 * stage + places.hashCode();
    }

    /**
     * @return the change written as place notation, using {@code x} for a cross change
     */
    @Override
    public String toString() {
        if (isCross()) {
            return "x";
        }
        StringBuilder text = new StringBuilder(places.size());
        for (int place : places) {
            text.append(Bells.symbol(place));
        }
        return text.toString();
    }

    private static void requireStage(int stage) {
        if (!Stage.isSupported(stage)) {
            throw new IllegalArgumentException("stage " + stage + " is not supported");
        }
    }

    private static List<Integer> normalisePlaces(int stage, List<Integer> places) {
        Guards.present(places, "places must be an array");
        TreeSet<Integer> seen = new TreeSet<>();
        for (Integer place : places) {
            if (place == null || place < 1 || place > stage) {
                throw new IllegalArgumentException("place " + place + " is outside 1.." + stage);
            }
            seen.add(place);
        }
        return List.copyOf(seen);
    }

    /**
     * Every run of bells between two made places has to be even, or the bells in
     * it cannot swap in pairs.
     */
    private static void requirePairable(int stage, List<Integer> places) {
        String written = places.isEmpty()
                ? "a cross change"
                : "places " + places.stream().map(String::valueOf).collect(Collectors.joining(","));
        int previous = 0;
        for (int place : places) {
            int gap = place - previous - 1;
            if (gap % 2 == 1) {
                String where = previous == 0 ? "below place " + place : "between places " + previous + " and " + place;
                throw unpairable(written, gap, where, stage, places);
            }
            previous = place;
        }
        int tail = stage - previous;
        if (tail % 2 == 1) {
            String where = previous == 0 ? "in the change" : "above place " + previous;
            throw unpairable(written, tail, where, stage, places);
        }
    }

    private static NotationException unpairable(
            String written, int gap, String where, int stage, List<Integer> places) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("stage", stage);
        details.put("places", List.copyOf(places));
        details.put("gap", gap);
        String bells = gap == 1 ? "1 bell" : gap + " bells";
        return new NotationException(
                written + " would leave " + bells + " " + where + " on " + stage, null, null, details);
    }
}
