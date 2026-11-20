package io.github.rkaushikdev.garterhole.validation;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.Text;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Turning text into the types the domain wants.
 *
 * <p>Everything that arrives in a query string, a command line flag or an
 * environment variable is text, and most of it means a number or a flag. Doing
 * that conversion by hand at every call site is where the mistakes live: an
 * empty string read as zero, or {@code "false"} read as true, and both of those
 * have caused real outages in other people's code.
 *
 * <p>So each conversion here is explicit about what it accepts and refuses
 * everything else rather than guessing. Each takes the value it was handed,
 * typed or not, and the name to use for it in a complaint.
 */
public final class Coerce {

    /** The words that mean true. */
    private static final List<String> TRUE_WORDS = List.of("true", "yes", "on", "1");

    /** The words that mean false. */
    private static final List<String> FALSE_WORDS = List.of("false", "no", "off", "0");

    private static final String DEFAULT_WHAT = "the value";

    /** A decimal number the way a person writes one, with an optional exponent. */
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");

    private static final Pattern HEX = Pattern.compile("0[xX][0-9a-fA-F]+");
    private static final Pattern OCTAL = Pattern.compile("0[oO][0-7]+");
    private static final Pattern BINARY = Pattern.compile("0[bB][01]+");
    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private Coerce() {}

    /**
     * One conversion, as {@link #convert} applies it.
     */
    @FunctionalInterface
    public interface Conversion {
        /**
         * @param value what arrived
         * @param what the name to use for it in a complaint
         * @return the converted value
         */
        Object apply(Object value, String what);
    }

    /**
     * @param value a number, or text that holds one
     * @return the number
     * @throws ValidationException when there is no finite number there
     */
    public static double toNumber(Object value) {
        return toNumber(value, DEFAULT_WHAT);
    }

    /**
     * @param value a number, or text that holds one
     * @param what the name to use for the value in a complaint
     * @return the number
     * @throws ValidationException when there is no finite number there
     */
    public static double toNumber(Object value, String what) {
        if (value instanceof Number number && Double.isFinite(number.doubleValue())) {
            return number.doubleValue();
        }
        if (!(value instanceof String text) || Text.trim(text).isEmpty()) {
            throw new ValidationException(what + " must be a number");
        }
        double parsed = readNumber(text);
        if (!Double.isFinite(parsed)) {
            throw new ValidationException(what + " must be a number, not " + Json.quote(text));
        }
        return parsed;
    }

    /**
     * @param value a whole number, or text that holds one
     * @return the number
     * @throws ValidationException when there is no whole number there
     */
    public static long toInteger(Object value) {
        return toInteger(value, DEFAULT_WHAT);
    }

    /**
     * @param value a whole number, or text that holds one
     * @param what the name to use for the value in a complaint
     * @return the number
     * @throws ValidationException when there is no whole number there
     */
    public static long toInteger(Object value, String what) {
        double parsed = toNumber(value, what);
        if (parsed != Math.rint(parsed)) {
            throw new ValidationException(what + " must be a whole number, not " + Json.number(parsed));
        }
        return (long) parsed;
    }

    /**
     * @param value a boolean, or one of the words that means one
     * @return the boolean
     * @throws ValidationException when the value is anything else
     */
    public static boolean toBoolean(Object value) {
        return toBoolean(value, DEFAULT_WHAT);
    }

    /**
     * @param value a boolean, or one of the words that means one
     * @param what the name to use for the value in a complaint
     * @return the boolean
     * @throws ValidationException when the value is anything else
     */
    public static boolean toBoolean(Object value, String what) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof String text) {
            String word = Text.trim(text).toLowerCase(Locale.ROOT);
            if (TRUE_WORDS.contains(word)) {
                return true;
            }
            if (FALSE_WORDS.contains(word)) {
                return false;
            }
        }
        String words = Stream.concat(TRUE_WORDS.stream(), FALSE_WORDS.stream()).collect(Collectors.joining(", "));
        throw new ValidationException(what + " must be one of " + words);
    }

    /**
     * @param value a date, or text that writes one the way a form sends it
     * @return the date
     * @throws ValidationException when the value is not a real date
     */
    public static LocalDate toDate(Object value) {
        return toDate(value, DEFAULT_WHAT);
    }

    /**
     * A date from the way a form or a query string writes one,
     * {@code 2025-05-18}.
     *
     * @param value a date, or text that writes one
     * @param what the name to use for the value in a complaint
     * @return the date
     * @throws ValidationException when the value is not a real date
     */
    public static LocalDate toDate(Object value, String what) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (!(value instanceof String text) || !ISO_DATE.matcher(text).matches()) {
            throw new ValidationException(what + " must be a date like 2025-05-18");
        }
        return parseDate(text).orElseThrow(() -> new ValidationException(what + " is not a real date"));
    }

    /**
     * @param text text in the form {@code 2025-05-18}
     * @return the date, when the text names a day that exists
     */
    public static java.util.Optional<LocalDate> parseDate(String text) {
        try {
            return java.util.Optional.of(LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE));
        } catch (DateTimeParseException notARealDay) {
            return java.util.Optional.empty();
        }
    }

    /**
     * @param value a list, or a comma separated string
     * @return the entries as strings
     * @throws ValidationException when the value is neither
     */
    public static List<String> toList(Object value) {
        return toList(value, DEFAULT_WHAT);
    }

    /**
     * A list from a comma separated string, with the blanks dropped.
     *
     * @param value a list, or a comma separated string
     * @param what the name to use for the value in a complaint
     * @return the entries as strings
     * @throws ValidationException when the value is neither
     */
    public static List<String> toList(Object value, String what) {
        if (value instanceof List<?> list) {
            return list.stream().map(Coerce::asText).toList();
        }
        if (!(value instanceof String text)) {
            throw new ValidationException(what + " must be a list or a comma separated string");
        }
        return Arrays.stream(text.split(","))
                .map(String::strip)
                .filter(entry -> !entry.isEmpty())
                .toList();
    }

    /**
     * Convert a whole bag of query parameters at once, leaving out anything
     * that was not sent. An empty value counts as not sent.
     *
     * @param query the parameters
     * @param conversions how each parameter is read, by name
     * @return the converted parameters
     * @throws ValidationException when there is no bag, or a parameter cannot be read
     */
    public static Map<String, Object> convert(Map<String, ?> query, Map<String, Conversion> conversions) {
        if (query == null) {
            throw new ValidationException("there is nothing to convert");
        }
        Map<String, Object> converted = new LinkedHashMap<>();
        conversions.forEach((name, conversion) -> {
            Object value = query.get(name);
            if (value == null || "".equals(value)) {
                return;
            }
            converted.put(name, conversion.apply(value, name));
        });
        return converted;
    }

    /**
     * Read text the way JavaScript's {@code Number(text)} reads it: white space
     * at either end is ignored, nothing at all is nought, a decimal may have a
     * sign and an exponent, {@code 0x}, {@code 0o} and {@code 0b} introduce hex,
     * octal and binary, and {@code Infinity} is infinite. Anything else is not a
     * number.
     *
     * @param raw the text
     * @return the number, or NaN when the text is not one
     */
    public static double readNumber(String raw) {
        String text = Text.trim(raw);
        if (text.isEmpty()) {
            return 0;
        }
        switch (text) {
            case "Infinity", "+Infinity" -> {
                return Double.POSITIVE_INFINITY;
            }
            case "-Infinity" -> {
                return Double.NEGATIVE_INFINITY;
            }
            default -> {
                // Not one of the spelled out numbers; read on below.
            }
        }
        if (DECIMAL.matcher(text).matches()) {
            return Double.parseDouble(text);
        }
        if (HEX.matcher(text).matches()) {
            return new java.math.BigInteger(text.substring(2), 16).doubleValue();
        }
        if (OCTAL.matcher(text).matches()) {
            return new java.math.BigInteger(text.substring(2), 8).doubleValue();
        }
        if (BINARY.matcher(text).matches()) {
            return new java.math.BigInteger(text.substring(2), 2).doubleValue();
        }
        return Double.NaN;
    }

    private static String asText(Object entry) {
        if (entry instanceof Double || entry instanceof Float) {
            return Json.number(((Number) entry).doubleValue());
        }
        return String.valueOf(entry);
    }
}
