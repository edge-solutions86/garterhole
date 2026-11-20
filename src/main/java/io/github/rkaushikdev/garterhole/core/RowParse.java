package io.github.rkaushikdev.garterhole.core;

import io.github.rkaushikdev.garterhole.GarterholeException;
import io.github.rkaushikdev.garterhole.NotationException;
import io.github.rkaushikdev.garterhole.util.Guards;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reading rows from text.
 *
 * <p>Rows arrive as text far more often than as arrays: out of a composition
 * file, out of a form field, out of a message from another ringer. Parsing is
 * kept apart from {@link Row} so that the row stays a value object and every
 * complaint about bad input is raised in one place.
 */
public final class RowParse {

    private static final Pattern SEPARATOR = Pattern.compile("[\\s,;]+");

    private RowParse() {}

    /**
     * Read a single row, at whatever stage it is written.
     *
     * @param text the row as printed, for example {@code 13527486}
     * @return the row
     * @throws NotationException when a character is not a bell
     * @throws IllegalArgumentException when the text is empty or the bells are not a permutation
     */
    public static Row parseRow(String text) {
        return read(text, null);
    }

    /**
     * Read a single row that must be at a given stage.
     *
     * @param text the row as printed
     * @param stage the stage the row must be at
     * @return the row
     * @throws NotationException when a character is not a bell or the row is at another stage
     * @throws IllegalArgumentException when the stage is not supported
     */
    public static Row parseRow(String text, int stage) {
        return read(text, stage);
    }

    /**
     * Read a block of rows separated by whitespace, commas or semicolons, at the
     * stage of the first. Blank input gives no rows rather than an error,
     * because an empty block is a legitimate thing for a composition to contain.
     *
     * @param text the rows
     * @return the rows, in order
     */
    public static List<Row> parseRows(String text) {
        List<String> parts = partsOf(text);
        if (parts.isEmpty()) {
            return List.of();
        }
        return readAll(parts, parseRow(parts.get(0)).stage());
    }

    /**
     * Read a block of rows that must all be at a given stage.
     *
     * @param text the rows
     * @param stage the stage every row must be at
     * @return the rows, in order
     */
    public static List<Row> parseRows(String text, int stage) {
        List<String> parts = partsOf(text);
        return parts.isEmpty() ? List.of() : readAll(parts, stage);
    }

    /**
     * Try to read a row and report the failure rather than throwing. Used by
     * the HTTP layer, which wants to collect every bad field in a request
     * before it answers.
     *
     * @param text the row as printed
     * @return the row, or what was wrong with the text
     */
    public static Attempt tryParseRow(String text) {
        return attempt(text, null);
    }

    /**
     * Try to read a row at a given stage and report the failure rather than throwing.
     *
     * @param text the row as printed
     * @param stage the stage the row must be at
     * @return the row, or what was wrong with the text
     */
    public static Attempt tryParseRow(String text, int stage) {
        return attempt(text, stage);
    }

    private static Attempt attempt(String text, Integer stage) {
        try {
            return new Parsed(read(text, stage));
        } catch (GarterholeException | IllegalArgumentException failure) {
            return new Refused(failure.getMessage());
        }
    }

    private static Row read(String text, Integer stage) {
        Guards.nonEmptyString(text, "a row");
        String trimmed = text.strip();
        if (trimmed.isEmpty()) {
            throw new NotationException("a row must not be blank", text, null);
        }
        int[] bells = new int[trimmed.length()];
        for (int index = 0; index < trimmed.length(); index += 1) {
            char symbol = trimmed.charAt(index);
            if (!Bells.isSymbol(symbol)) {
                throw new NotationException(
                        Json.quote(String.valueOf(symbol)) + " at position " + (index + 1) + " of "
                                + Json.quote(trimmed) + " is not a bell",
                        trimmed,
                        index + 1);
            }
            bells[index] = Bells.fromSymbol(symbol);
        }
        if (stage != null) {
            if (!Stage.isSupported(stage)) {
                throw new IllegalArgumentException("stage " + stage + " is not supported");
            }
            if (bells.length != stage) {
                throw new NotationException(
                        "row " + Json.quote(trimmed) + " has " + bells.length + " bells but the stage is " + stage,
                        trimmed,
                        null,
                        Map.of("stage", stage, "found", bells.length));
            }
        }
        return new Row(bells);
    }

    private static List<String> partsOf(String text) {
        Guards.present(text, "a block of rows must be a string");
        return Arrays.stream(SEPARATOR.split(text))
                .filter(part -> !part.isEmpty())
                .toList();
    }

    private static List<Row> readAll(List<String> parts, int stage) {
        List<Row> rows = new ArrayList<>(parts.size());
        for (String part : parts) {
            rows.add(read(part, stage));
        }
        return List.copyOf(rows);
    }

    /** What came of trying to read a row. */
    public sealed interface Attempt permits Parsed, Refused {
        /**
         * @return true when a row was read
         */
        boolean ok();
    }

    /**
     * The text was a row.
     *
     * @param row the row it held
     */
    public record Parsed(Row row) implements Attempt {
        @Override
        public boolean ok() {
            return true;
        }
    }

    /**
     * The text was not a row.
     *
     * @param message what was wrong with it
     */
    public record Refused(String message) implements Attempt {
        @Override
        public boolean ok() {
            return false;
        }
    }
}
