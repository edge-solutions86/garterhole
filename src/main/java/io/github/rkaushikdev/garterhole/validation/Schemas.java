package io.github.rkaushikdev.garterhole.validation;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The building blocks for schemas.
 *
 * <p>There is no schema library behind this on purpose. A schema library is a
 * large thing to take on for what this needs, which is a dozen field types and
 * a list of problems rather than the first one. Every factory here gives back a
 * {@link Schema}; the ones with rules give back a schema that can be narrowed,
 * so {@code string().min(1).max(80)} reads the way the rule is said.
 */
public final class Schemas {

    private Schemas() {}

    /**
     * @return a schema that accepts anything at all, including nothing
     */
    public static Schema anything() {
        return (value, path) -> List.of();
    }

    /**
     * @return a schema for a string of any length
     */
    public static StringSchema string() {
        return new StringSchema(null, null, null);
    }

    /**
     * @return a schema for any finite number
     */
    public static NumberSchema number() {
        return new NumberSchema(null, null, false);
    }

    /**
     * @return a schema for true or false
     */
    public static Schema bool() {
        return (value, path) ->
                isPresent(value) && value.isBoolean() ? List.of() : Problem.at(path, "must be true or false");
    }

    /**
     * @param allowed the values that are acceptable, as plain Java values
     * @return a schema for one of those values
     * @throws ValidationException when there is nothing to choose from
     */
    public static Schema oneOf(List<?> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            throw new ValidationException("a choice needs something to choose from");
        }
        List<JsonNode> choices = allowed.stream().map(Json::toNode).toList();
        String listed = allowed.stream().map(String::valueOf).collect(Collectors.joining(", "));
        return (value, path) -> isPresent(value) && choices.stream().anyMatch(choice -> sameValue(choice, value))
                ? List.of()
                : Problem.at(path, "must be one of " + listed);
    }

    /**
     * @param element the schema every entry must meet
     * @return a schema for a list of any length
     */
    public static ArraySchema arrayOf(Schema element) {
        return new ArraySchema(element, null, null);
    }

    /**
     * An object with named fields, checked in the order given. Fields not in
     * the schema are ignored, because a client sending a field this version
     * does not know about should not be refused for it.
     *
     * @param fields the schema for each field, by name
     * @return a schema for the object
     * @throws ValidationException when there are no fields
     */
    public static Schema object(Map<String, Schema> fields) {
        if (fields == null) {
            throw new ValidationException("an object schema needs its fields");
        }
        Map<String, Schema> ordered = new LinkedHashMap<>(fields);
        return (value, path) -> {
            if (!isPresent(value) || !value.isObject()) {
                return Problem.at(path, "must be an object");
            }
            List<Problem> problems = new ArrayList<>();
            ordered.forEach((name, schema) -> {
                String where = path.isEmpty() ? name : path + "." + name;
                problems.addAll(schema.problems(value.path(name), where));
            });
            return problems;
        };
    }

    /**
     * @param schema the schema the field must meet when it is there
     * @return a schema for a field that may be left out
     */
    public static Schema optional(Schema schema) {
        return (value, path) -> isMissing(value) ? List.of() : schema.problems(value, path);
    }

    /**
     * @param schema the schema the field must meet when it is there
     * @param fallback what a reader should use when it is left out
     * @return a schema for a field that may be left out, carrying its default
     */
    public static Defaulted withDefault(Schema schema, Object fallback) {
        return new Defaulted(schema, fallback);
    }

    /**
     * Check a value and throw when anything is wrong.
     *
     * @param schema the schema
     * @param value the value
     * @return the value, so that this can be used inline
     * @throws ValidationException naming the first problem and carrying them all
     */
    public static JsonNode check(Schema schema, JsonNode value) {
        return check(schema, value, "the request");
    }

    /**
     * Check a value and throw when anything is wrong.
     *
     * @param schema the schema
     * @param value the value
     * @param what what the value is, for the message
     * @return the value, so that this can be used inline
     * @throws ValidationException naming the first problem and carrying them all
     */
    public static JsonNode check(Schema schema, JsonNode value, String what) {
        List<Problem> problems = schema.problems(value, "");
        if (problems.isEmpty()) {
            return value;
        }
        Problem first = problems.get(0);
        throw ValidationException.withIssues(what + " is wrong: " + first.path() + " " + first.message(), problems);
    }

    /**
     * Check a value and report rather than throwing.
     *
     * @param schema the schema
     * @param value the value
     * @return whether the value is sound, and every problem with it
     */
    public static Verdict validate(Schema schema, JsonNode value) {
        List<Problem> problems = schema.problems(value, "");
        return new Verdict(problems.isEmpty(), problems);
    }

    static boolean isMissing(JsonNode value) {
        return value == null || value.isMissingNode();
    }

    static boolean isPresent(JsonNode value) {
        return !isMissing(value);
    }

    private static boolean sameValue(JsonNode choice, JsonNode value) {
        if (choice.isNumber() && value.isNumber()) {
            return choice.doubleValue() == value.doubleValue();
        }
        return choice.equals(value);
    }

    /**
     * The answer from {@link #validate}.
     *
     * @param ok true when nothing is wrong
     * @param problems everything that is wrong
     */
    public record Verdict(boolean ok, List<Problem> problems) {}

    /**
     * A field that may be left out, with a stated default when it is.
     *
     * @param schema the schema the field must meet when it is there
     * @param fallback what a reader should use when it is left out
     */
    public record Defaulted(Schema schema, Object fallback) implements Schema {
        @Override
        public List<Problem> problems(JsonNode value, String path) {
            return isMissing(value) ? List.of() : schema.problems(value, path);
        }
    }

    /** A string, with optional limits on its length and a pattern it must match. */
    public static final class StringSchema implements Schema {
        private final Integer min;
        private final Integer max;
        private final Pattern pattern;
        private final Pattern endAnchored;

        private StringSchema(Integer min, Integer max, Pattern pattern) {
            this.min = min;
            this.max = max;
            this.pattern = pattern;
            this.endAnchored = pattern == null ? null : endAnchored(pattern);
        }

        /**
         * @param characters the fewest characters allowed
         * @return this schema with a minimum length
         */
        public StringSchema min(int characters) {
            return new StringSchema(characters, max, pattern);
        }

        /**
         * @param characters the most characters allowed
         * @return this schema with a maximum length
         */
        public StringSchema max(int characters) {
            return new StringSchema(min, characters, pattern);
        }

        /**
         * @param required a pattern the whole string must match
         * @return this schema with a pattern
         */
        public StringSchema pattern(Pattern required) {
            return new StringSchema(min, max, required);
        }

        @Override
        public List<Problem> problems(JsonNode value, String path) {
            if (!isPresent(value) || !value.isTextual()) {
                return Problem.at(path, "must be a string");
            }
            String text = value.textValue();
            if (min != null && text.length() < min) {
                return Problem.at(path, "must be at least " + min + " characters");
            }
            if (max != null && text.length() > max) {
                return Problem.at(path, "must be at most " + max + " characters");
            }
            if (pattern != null && !endAnchored.matcher(text).find()) {
                return Problem.at(path, "must match /" + pattern.pattern() + "/");
            }
            return List.of();
        }
    }

    /**
     * The pattern as a JavaScript regular expression reads it. Without the
     * multiline flag a JavaScript {@code $} matches only at the very end of the
     * text, where Java's also matches just before a final line break, so
     * {@code "2025-05-18\n"} would pass a date pattern here and fail there.
     * Every bare {@code $} becomes {@code \z}; one that is escaped or inside a
     * character class is left alone.
     *
     * @param pattern the pattern, written as its JavaScript source
     * @return the same pattern anchored the JavaScript way
     */
    static Pattern endAnchored(Pattern pattern) {
        if ((pattern.flags() & Pattern.MULTILINE) != 0) {
            return pattern;
        }
        String source = pattern.pattern();
        StringBuilder anchored = new StringBuilder(source.length() + 4);
        boolean inClass = false;
        for (int index = 0; index < source.length(); index += 1) {
            char character = source.charAt(index);
            if (character == '\\' && index + 1 < source.length()) {
                anchored.append(character).append(source.charAt(index + 1));
                index += 1;
            } else if (inClass) {
                inClass = character != ']';
                anchored.append(character);
            } else if (character == '[') {
                inClass = true;
                anchored.append(character);
            } else if (character == '$') {
                anchored.append("\\z");
            } else {
                anchored.append(character);
            }
        }
        return Pattern.compile(anchored.toString(), pattern.flags());
    }

    /** A finite number, optionally whole and optionally bounded. */
    public static final class NumberSchema implements Schema {
        private final Double min;
        private final Double max;
        private final boolean integer;

        private NumberSchema(Double min, Double max, boolean integer) {
            this.min = min;
            this.max = max;
            this.integer = integer;
        }

        /**
         * @param lowest the smallest value allowed
         * @return this schema with a lower bound
         */
        public NumberSchema min(double lowest) {
            return new NumberSchema(lowest, max, integer);
        }

        /**
         * @param highest the largest value allowed
         * @return this schema with an upper bound
         */
        public NumberSchema max(double highest) {
            return new NumberSchema(min, highest, integer);
        }

        /**
         * @return this schema, allowing whole numbers only
         */
        public NumberSchema integer() {
            return new NumberSchema(min, max, true);
        }

        @Override
        public List<Problem> problems(JsonNode value, String path) {
            if (!isPresent(value) || !value.isNumber() || !Double.isFinite(value.doubleValue())) {
                return Problem.at(path, "must be a number");
            }
            double number = value.doubleValue();
            if (integer && number != Math.rint(number)) {
                return Problem.at(path, "must be a whole number");
            }
            if (min != null && number < min) {
                return Problem.at(path, "must be at least " + Json.number(min));
            }
            if (max != null && number > max) {
                return Problem.at(path, "must be at most " + Json.number(max));
            }
            return List.of();
        }
    }

    /** A list whose every entry meets a schema, optionally with limits on its length. */
    public static final class ArraySchema implements Schema {
        private final Schema element;
        private final Integer min;
        private final Integer max;

        private ArraySchema(Schema element, Integer min, Integer max) {
            this.element = element;
            this.min = min;
            this.max = max;
        }

        /**
         * @param entries the fewest entries allowed
         * @return this schema with a minimum length
         */
        public ArraySchema min(int entries) {
            return new ArraySchema(element, entries, max);
        }

        /**
         * @param entries the most entries allowed
         * @return this schema with a maximum length
         */
        public ArraySchema max(int entries) {
            return new ArraySchema(element, min, entries);
        }

        @Override
        public List<Problem> problems(JsonNode value, String path) {
            if (!isPresent(value) || !value.isArray()) {
                return Problem.at(path, "must be a list");
            }
            if (min != null && value.size() < min) {
                return Problem.at(path, "must have at least " + min + " entries");
            }
            if (max != null && value.size() > max) {
                return Problem.at(path, "must have at most " + max + " entries");
            }
            List<Problem> problems = new ArrayList<>();
            for (int index = 0; index < value.size(); index += 1) {
                problems.addAll(element.problems(value.get(index), path + "[" + index + "]"));
            }
            return problems;
        }
    }
}
