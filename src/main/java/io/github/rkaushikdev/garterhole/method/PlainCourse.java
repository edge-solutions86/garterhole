package io.github.rkaushikdev.garterhole.method;

import io.github.rkaushikdev.garterhole.core.Cycles;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.core.RowBlock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A plain course is the method rung with no calls until it comes back to
 * rounds. Its length is the lead length times the order of the lead head, and
 * that number is the first thing a composer needs before working out how many
 * courses a peal takes.
 */
public final class PlainCourse {

    private PlainCourse() {}

    /**
     * Where one bell goes over a lead.
     *
     * @param bell the bell
     * @param from the place it starts the lead in, which is its own place at the course head
     * @param to the place it finishes the lead in
     */
    public record Movement(int bell, int from, int to) {}

    /**
     * @param method a method
     * @return how many leads a plain course has
     * @throws IllegalArgumentException when there is no method
     */
    public static int leadsInCourse(Method method) {
        HuntBells.requireMethod(method);
        return Cycles.orderOf(method.leadHead());
    }

    /**
     * @param method a method
     * @return how many changes a plain course has
     */
    public static int courseLength(Method method) {
        return leadsInCourse(method) * method.leadLength();
    }

    /**
     * @param method a method
     * @return every lead head in a plain course from rounds, starting with rounds
     *     and ending with the lead head that brings it back
     */
    public static List<Row> courseLeadHeads(Method method) {
        HuntBells.requireMethod(method);
        return courseLeadHeads(method, Row.rounds(method.stage()));
    }

    /**
     * @param method a method
     * @param start the course head
     * @return every lead head in the plain course, starting and ending with the course head
     */
    public static List<Row> courseLeadHeads(Method method, Row start) {
        HuntBells.requireMethod(method);
        List<Row> heads = new ArrayList<>();
        heads.add(start);
        Row current = start;
        int limit = leadsInCourse(method);
        for (int lead = 0; lead < limit; lead += 1) {
            current = method.leadHead(current);
            heads.add(current);
        }
        return List.copyOf(heads);
    }

    /**
     * @param method a method
     * @return the whole plain course rung from rounds
     */
    public static RowBlock plainCourse(Method method) {
        HuntBells.requireMethod(method);
        return plainCourse(method, Row.rounds(method.stage()));
    }

    /**
     * Ring the whole plain course from a course head.
     *
     * @param method a method
     * @param start the course head
     * @return the course, holding the course head at both ends
     */
    public static RowBlock plainCourse(Method method, Row start) {
        HuntBells.requireMethod(method);
        int leads = leadsInCourse(method);
        List<Row> rows = new ArrayList<>(leads * method.leadLength() + 1);
        RowBlock lead = method.plainLead(start);
        rows.addAll(lead.rows());
        for (int count = 1; count < leads; count += 1) {
            lead = method.plainLead(lead.last());
            rows.addAll(lead.rows().subList(1, lead.length()));
        }
        return new RowBlock(rows);
    }

    /**
     * True when every row of the plain course is different. A method whose
     * plain course repeats a row is false in the plain, and no composition of it
     * can be true either.
     *
     * @param method a method
     * @return true when the plain course is true
     */
    public static boolean isTrueInThePlain(Method method) {
        RowBlock course = plainCourse(method);
        Set<Row> seen = new HashSet<>();
        for (int index = 0; index < course.length() - 1; index += 1) {
            if (!seen.add(course.at(index))) {
                return false;
            }
        }
        return true;
    }

    /**
     * The place each bell starts a course in, paired with the place it finishes
     * the first lead in. Callers use this to work out which bell is where when
     * a call comes up.
     *
     * @param method a method
     * @return one movement per bell
     */
    public static List<Movement> leadHeadMovement(Method method) {
        HuntBells.requireMethod(method);
        Row head = method.leadHead();
        List<Movement> movement = new ArrayList<>(method.stage());
        for (int bell = 1; bell <= method.stage(); bell += 1) {
            movement.add(new Movement(bell, bell, head.placeOf(bell)));
        }
        return List.copyOf(movement);
    }
}
