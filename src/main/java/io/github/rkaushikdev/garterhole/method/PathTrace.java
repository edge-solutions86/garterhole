package io.github.rkaushikdev.garterhole.method;

import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.util.CollectionHelpers;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The blue line. A ringer learns a method as the path one bell takes through a
 * course, described as a run of pieces of work: dodge here, make a place there,
 * lie behind, lead. This turns the rows into that description.
 *
 * <p>The description is deliberately mechanical. It says what happened rather
 * than naming it the way a teacher would, because the names differ between
 * towers and the mechanics do not.
 */
public final class PathTrace {

    private PathTrace() {}

    /** The kinds of work a bell does. */
    public enum Work {
        /** Two or more blows in first place. */
        LEAD,
        /** Two or more blows at the back. */
        LIE,
        /** Two or more blows anywhere else. */
        PLACE,
        /** Two turns one place apart, so the bell ends up where it came from. */
        DODGE,
        /** A turn with no answering turn beside it. */
        POINT,
        /** A blow in a run in the same direction. */
        HUNT;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * One piece of work.
     *
     * @param kind what the bell does
     * @param place where it does it; for a dodge, the lower of the two places
     * @param at the index in the path where it starts
     * @param blows how many blows it takes
     */
    public record WorkPiece(Work kind, int place, int at, int blows) {}

    /**
     * The place a bell rings in for every row of a plain course.
     *
     * @param method a method
     * @param bell a bell
     * @return the places, one per row, starting and ending at the course head
     * @throws IllegalArgumentException when there is no method
     */
    public static List<Integer> coursePath(Method method, int bell) {
        HuntBells.requireMethod(method);
        List<Row> rows = PlainCourse.plainCourse(method).rows();
        List<Integer> path = new ArrayList<>(rows.size());
        for (Row row : rows) {
            path.add(row.placeOf(bell));
        }
        return List.copyOf(path);
    }

    /**
     * Break a path into pieces of work.
     *
     * @param path the places a bell rings in
     * @param stage the number of bells, which decides where the back is
     * @return the pieces, in order, including the hunting
     */
    public static List<WorkPiece> describePath(List<Integer> path, int stage) {
        List<Boolean> turns = turnPoints(path);
        List<WorkPiece> pieces = new ArrayList<>();
        int index = 0;
        while (index + 1 < path.size()) {
            int place = path.get(index);
            int blows = 1;
            while (index + blows < path.size() && path.get(index + blows) == place) {
                blows += 1;
            }
            if (blows > 1) {
                pieces.add(new WorkPiece(stationaryKind(place, stage), place, index, blows));
                index += blows;
                continue;
            }
            if (turns.get(index)) {
                int next = path.get(index + 1);
                if (turns.get(index + 1) && Math.abs(next - place) == 1) {
                    pieces.add(new WorkPiece(Work.DODGE, Math.min(place, next), index, 2));
                    index += 2;
                    continue;
                }
                pieces.add(new WorkPiece(Work.POINT, place, index, 1));
                index += 1;
                continue;
            }
            pieces.add(new WorkPiece(Work.HUNT, place, index, 1));
            index += 1;
        }
        return List.copyOf(pieces);
    }

    /**
     * Mark every index where the bell reverses direction.
     *
     * @param path the places a bell rings in
     * @return one flag per entry of the path
     */
    public static List<Boolean> turnPoints(List<Integer> path) {
        List<Boolean> turns = new ArrayList<>(path.size());
        for (int index = 0; index < path.size(); index += 1) {
            turns.add(index >= 1 && index + 1 < path.size() && reverses(path, index));
        }
        return List.copyOf(turns);
    }

    /**
     * The pieces of work a bell does over a plain course, with the runs of
     * plain hunting left out because nobody describes those out loud.
     *
     * @param method a method
     * @param bell a bell
     * @return the pieces, in order
     */
    public static List<WorkPiece> workOf(Method method, int bell) {
        return describePath(coursePath(method, bell), method.stage()).stream()
                .filter(piece -> piece.kind() != Work.HUNT)
                .toList();
    }

    /**
     * The path written out as one line of places per lead, which is how a blue
     * line is printed on paper.
     *
     * @param method a method
     * @param bell a bell
     * @return one list of places per lead
     */
    public static List<List<Integer>> pathByLead(Method method, int bell) {
        List<Integer> path = coursePath(method, bell);
        return CollectionHelpers.chunk(path.subList(0, path.size() - 1), method.leadLength());
    }

    /**
     * True when two bells ring the same line, which is what makes a method
     * double. The comparison rotates one path against the other, because two
     * bells doing the same work start it at different points in the course.
     *
     * @param left one path
     * @param right the other
     * @return true when one is a rotation of the other
     */
    public static boolean samePathRotated(List<Integer> left, List<Integer> right) {
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

    private static boolean reverses(List<Integer> path, int index) {
        int before = Integer.signum(path.get(index) - path.get(index - 1));
        int after = Integer.signum(path.get(index + 1) - path.get(index));
        return before != 0 && after != 0 && before != after;
    }

    private static Work stationaryKind(int place, int stage) {
        if (place == 1) {
            return Work.LEAD;
        }
        if (place == stage) {
            return Work.LIE;
        }
        return Work.PLACE;
    }
}
