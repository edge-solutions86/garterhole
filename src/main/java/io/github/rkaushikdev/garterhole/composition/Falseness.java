package io.github.rkaushikdev.garterhole.composition;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.core.Cycles;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.core.Transposition;
import io.github.rkaushikdev.garterhole.method.Method;
import io.github.rkaushikdev.garterhole.method.PlainCourse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * False course heads.
 *
 * <p>Two courses of a method clash when a row in one is also a row in the
 * other. Which pairs of courses clash depends only on the method, so the set
 * can be worked out once and then used to check any composition of it without
 * ringing a single row twice.
 *
 * <p>The set is the transposition between every pair of rows in a plain course,
 * kept where it leaves the treble at the front. A composer reads it as: ring
 * the plain course and any course headed by one of these, and the two are false
 * against each other.
 */
public final class Falseness {

    private static final int DEFAULT_TENORS = 2;

    private Falseness() {}

    /**
     * The course head that takes one row of a course to another.
     *
     * @param from a row of one course
     * @param to a row of another
     * @return the head that, transposed by {@code from}, gives {@code to}
     */
    public static Row courseHeadBetween(Row from, Row to) {
        return Transposition.transpose(to, Transposition.inverse(from));
    }

    /**
     * Every course head that is false against the plain course, rounds left out.
     *
     * <p>This compares every pair of rows in the plain course, so it is worked
     * on the bells directly: each row's inverse is found once, and a pair whose
     * head would move the treble is dropped before a row is built for it.
     *
     * @param method the method
     * @return the heads, sorted by their printed form
     * @throws ValidationException when there is no method
     */
    public static List<Row> falseCourseHeads(Method method) {
        if (method == null) {
            throw new ValidationException("a Method is needed to find its false course heads");
        }
        List<Row> course = PlainCourse.plainCourse(method).rows();
        int count = course.size() - 1;
        int stage = method.stage();
        int[][] bells = new int[count][];
        int[][] inverses = new int[count][];
        for (int index = 0; index < count; index += 1) {
            bells[index] = course.get(index).toArray();
            inverses[index] = Transposition.inverse(course.get(index)).toArray();
        }
        Set<Row> heads = new HashSet<>();
        int[] head = new int[stage];
        for (int from = 0; from < count; from += 1) {
            int[] inverse = inverses[from];
            for (int to = 0; to < count; to += 1) {
                int[] target = bells[to];
                if (target[inverse[0] - 1] != 1) {
                    continue;
                }
                boolean rounds = true;
                for (int place = 0; place < stage; place += 1) {
                    head[place] = target[inverse[place] - 1];
                    rounds &= head[place] == place + 1;
                }
                if (!rounds) {
                    heads.add(new Row(head));
                }
            }
        }
        List<Row> sorted = new ArrayList<>(heads);
        sorted.sort(Comparator.comparing(Row::toString));
        return Collections.unmodifiableList(sorted);
    }

    /**
     * The false course heads that leave the two tenors where they are, which is
     * the list a composer of tenors together music actually uses.
     *
     * @param method the method
     * @return the heads that fix the back two bells
     */
    public static List<Row> tenorsTogetherFalseCourseHeads(Method method) {
        return tenorsTogetherFalseCourseHeads(method, DEFAULT_TENORS);
    }

    /**
     * The false course heads that leave a number of bells at the back where
     * they are.
     *
     * @param method the method
     * @param tenors how many bells at the back must be fixed
     * @return the heads that fix them
     */
    public static List<Row> tenorsTogetherFalseCourseHeads(Method method, int tenors) {
        int lowest = method.stage() - tenors + 1;
        return falseCourseHeads(method).stream()
                .filter(head -> {
                    for (int place = lowest; place <= method.stage(); place += 1) {
                        if (head.bellAt(place) != place) {
                            return false;
                        }
                    }
                    return true;
                })
                .toList();
    }

    /**
     * @param method the method
     * @param courseHead the head of another course
     * @return true when a course headed by the row clashes with the plain course
     */
    public static boolean isFalseAgainstPlainCourse(Method method, Row courseHead) {
        return falseCourseHeads(method).contains(courseHead);
    }

    /**
     * @param left one set of rows
     * @param right another
     * @return the rows the two have in common, each once, sorted by their printed form
     */
    public static List<String> sharedRows(Iterable<Row> left, Iterable<Row> right) {
        Set<Row> seen = new HashSet<>();
        left.forEach(seen::add);
        Set<String> shared = new TreeSet<>();
        for (Row row : right) {
            if (seen.contains(row)) {
                shared.add(row.key());
            }
        }
        return List.copyOf(shared);
    }

    /**
     * The candidate course heads that are true against the plain course. A
     * composer hands in the heads a plan would use and gets back the ones that
     * survive.
     *
     * @param method the method
     * @param candidates the course heads a plan would use
     * @return the candidates that are true against the plain course, in the order given
     */
    public static List<Row> trueCourseHeads(Method method, Iterable<Row> candidates) {
        Set<Row> bad = new HashSet<>(falseCourseHeads(method));
        List<Row> kept = new ArrayList<>();
        for (Row head : candidates) {
            if (!bad.contains(head)) {
                kept.add(head);
            }
        }
        return List.copyOf(kept);
    }

    /**
     * Group the false course heads by the shape of the transposition, that is by
     * the lengths of its cycles. Heads with the same shape sit together in the
     * printed tables, and a composer who has ruled one out has usually ruled
     * out the rest of its group as well.
     *
     * @param method the method
     * @return the heads keyed by their cycle lengths joined with full stops, for example {@code 3.2.1}
     */
    public static Map<String, List<Row>> falseCourseHeadGroups(Method method) {
        Map<String, List<Row>> groups = new LinkedHashMap<>();
        for (Row head : falseCourseHeads(method)) {
            String key = Cycles.cycleLengths(head).stream().map(String::valueOf).collect(Collectors.joining("."));
            groups.computeIfAbsent(key, shape -> new ArrayList<>()).add(head);
        }
        Map<String, List<Row>> frozen = new LinkedHashMap<>();
        groups.forEach((key, bucket) -> frozen.put(key, List.copyOf(bucket)));
        return Collections.unmodifiableMap(frozen);
    }
}
