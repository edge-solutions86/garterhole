package io.github.rkaushikdev.garterhole.method;

import java.util.List;

/**
 * Everything a collection prints beside a method.
 *
 * @param title the name with the stage name in brackets, for example {@code Cambridge (Major)}
 * @param stage the number of bells
 * @param leadLength the changes in a lead
 * @param leadsInCourse the leads in a plain course
 * @param courseLength the changes in a plain course
 * @param huntBells the hunt bells, ascending
 * @param workingBells the working bells, ascending
 * @param placeBellOrder the place bell order, starting from the first working place bell
 * @param className the class name with its prefixes
 */
public record MethodStats(
        String title,
        int stage,
        int leadLength,
        int leadsInCourse,
        int courseLength,
        List<Integer> huntBells,
        List<Integer> workingBells,
        List<Integer> placeBellOrder,
        String className) {

    /**
     * @param title the name with the stage name in brackets
     * @param stage the number of bells
     * @param leadLength the changes in a lead
     * @param leadsInCourse the leads in a plain course
     * @param courseLength the changes in a plain course
     * @param huntBells the hunt bells
     * @param workingBells the working bells
     * @param placeBellOrder the place bell order
     * @param className the class name
     */
    public MethodStats {
        huntBells = List.copyOf(huntBells);
        workingBells = List.copyOf(workingBells);
        placeBellOrder = List.copyOf(placeBellOrder);
    }
}
