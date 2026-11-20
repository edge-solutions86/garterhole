package io.github.rkaushikdev.garterhole.striking;

import io.github.rkaushikdev.garterhole.core.Row;
import java.util.List;

/**
 * One row as it was actually struck: the row the bells made, and the blows that
 * made it.
 *
 * @param row the order the bells struck in
 * @param index the row's position in the touch, counted from zero
 * @param strikes the blows of the row, in time order
 * @param start when the first blow of the row fell
 * @param end when the last blow of the row fell
 */
public record StruckRow(Row row, int index, List<Strike> strikes, double start, double end) {

    /**
     * Copies the blows, so that a struck row cannot change underneath an analysis.
     */
    public StruckRow {
        strikes = List.copyOf(strikes);
    }
}
