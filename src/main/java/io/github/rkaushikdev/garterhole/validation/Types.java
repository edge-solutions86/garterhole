package io.github.rkaushikdev.garterhole.validation;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.garterhole.GarterholeException;
import io.github.rkaushikdev.garterhole.composition.TouchParser;
import io.github.rkaushikdev.garterhole.core.RowParse;
import io.github.rkaushikdev.garterhole.core.Stage;
import io.github.rkaushikdev.garterhole.method.Title;
import io.github.rkaushikdev.garterhole.notation.Expansion;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The field types this library actually has, as schemas.
 *
 * <p>Every one of these is a thin wrapper around a reader that already exists.
 * That matters: the HTTP layer must accept exactly what the domain accepts, and
 * the only way to be sure of that is to ask the domain rather than to write a
 * regular expression that agrees with it today.
 */
public final class Types {

    private static final Pattern ISO_DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final int MOST_CHANGES = 100_000;

    private Types() {}

    /**
     * @return a schema for a stage this library can work with
     */
    public static Schema stage() {
        Schema bounds = Schemas.number().integer().min(Stage.MIN).max(Stage.MAX);
        return (value, path) -> {
            List<Problem> problems = bounds.problems(value, path);
            if (!problems.isEmpty()) {
                return problems;
            }
            return Stage.isSupported(value.doubleValue()) ? List.of() : Problem.at(path, "is not a stage");
        };
    }

    /**
     * @return a schema for a row at any stage, written out
     */
    public static Schema row() {
        return readBy(RowParse::tryParseRow);
    }

    /**
     * @param stage the stage the row must be at
     * @return a schema for a row at that stage, written out
     */
    public static Schema row(int stage) {
        return readBy(text -> RowParse.tryParseRow(text, stage));
    }

    /**
     * @param stage the stage the notation is for
     * @return a schema for place notation that expands at that stage
     */
    public static Schema placeNotation(int stage) {
        return readText(text -> Expansion.expandNotation(text, stage));
    }

    /**
     * @return a schema for a method title that ends in its stage
     */
    public static Schema methodTitle() {
        Schema text = Schemas.string().min(1);
        return (value, path) -> {
            List<Problem> problems = text.problems(value, path);
            if (!problems.isEmpty()) {
                return problems;
            }
            return Title.isWellFormed(value.textValue()) ? List.of() : Problem.at(path, "is not a method title");
        };
    }

    /**
     * @return a schema for a touch, written either way
     */
    public static Schema touch() {
        return readText(TouchParser::parseTouch);
    }

    /**
     * @return a schema for a date written the way a form sends it
     */
    public static Schema isoDate() {
        Schema text = Schemas.string().pattern(ISO_DATE);
        return (value, path) -> {
            if (!text.problems(value, path).isEmpty()) {
                return Problem.at(path, "must be a date like 2025-05-18");
            }
            return Coerce.parseDate(value.textValue()).isPresent() ? List.of() : Problem.at(path, "is not a real date");
        };
    }

    /**
     * @return a schema for a number of changes
     */
    public static Schema changeCount() {
        return Schemas.number().integer().min(1).max(MOST_CHANGES);
    }

    /** A non empty string, then whatever the reader makes of it. */
    private static Schema readText(Function<String, ?> reader) {
        Schema text = Schemas.string().min(1);
        return (value, path) -> {
            List<Problem> problems = text.problems(value, path);
            if (!problems.isEmpty()) {
                return problems;
            }
            try {
                reader.apply(value.textValue());
                return List.of();
            } catch (GarterholeException | IllegalArgumentException refused) {
                return Problem.at(path, refused.getMessage());
            }
        };
    }

    /** A non empty string that a row reader reports on rather than throwing. */
    private static Schema readBy(Function<String, RowParse.Attempt> attempt) {
        Schema text = Schemas.string().min(1);
        return (JsonNode value, String path) -> {
            List<Problem> problems = text.problems(value, path);
            if (!problems.isEmpty()) {
                return problems;
            }
            return attempt.apply(value.textValue()) instanceof RowParse.Refused refused
                    ? Problem.at(path, refused.message())
                    : List.of();
        };
    }
}
