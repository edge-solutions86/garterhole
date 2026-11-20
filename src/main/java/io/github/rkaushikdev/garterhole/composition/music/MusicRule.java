package io.github.rkaushikdev.garterhole.composition.music;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * One rule of a music scheme: a name, a weight, and the one thing it counts,
 * which is a pattern, a run length or a named row.
 *
 * <p>A scheme is data rather than code, so a rule can arrive incomplete from a
 * scheme somebody wrote by hand. The scorer refuses such a rule when it is
 * applied, naming it, rather than this record refusing to hold it.
 *
 * @param name what the rule is called on a report
 * @param weight how much each row it counts is worth
 * @param pattern a pattern to count, as {@link Patterns} reads it
 * @param runLength the shortest run to count, as {@link Runs} counts it
 * @param namedRow the name of a row to count, as {@code NamedRows} names it
 */
public record MusicRule(
        String name,
        OptionalDouble weight,
        Optional<String> pattern,
        OptionalInt runLength,
        Optional<String> namedRow) {

    /**
     * @param name what the rule is called
     * @param pattern the pattern
     * @param weight what each matching row is worth
     * @return a rule that counts rows matching a pattern
     */
    public static MusicRule pattern(String name, String pattern, double weight) {
        return new MusicRule(
                name, OptionalDouble.of(weight), Optional.of(pattern), OptionalInt.empty(), Optional.empty());
    }

    /**
     * @param name what the rule is called
     * @param length the shortest run counted
     * @param weight what each row with a run is worth
     * @return a rule that counts rows with a run at either end
     */
    public static MusicRule runs(String name, int length, double weight) {
        return new MusicRule(
                name, OptionalDouble.of(weight), Optional.empty(), OptionalInt.of(length), Optional.empty());
    }

    /**
     * @param name what the rule is called
     * @param row the name of the row, for example {@code Queens}
     * @param weight what each time it is rung is worth
     * @return a rule that counts a named row
     */
    public static MusicRule namedRow(String name, String row, double weight) {
        return new MusicRule(name, OptionalDouble.of(weight), Optional.empty(), OptionalInt.empty(), Optional.of(row));
    }
}
