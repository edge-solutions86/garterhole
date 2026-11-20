package io.github.rkaushikdev.garterhole.util;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Argument guards. Every module in the library validates what it is handed,
 * because most of the input arrives from a person typing a touch into a form
 * and a message that names the offending value saves a support email.
 *
 * <p>The guards throw the built in exception types on purpose. A guard failure
 * means the caller passed nonsense, which is a programming mistake rather than
 * a rejected document, so it is an {@link IllegalArgumentException} rather
 * than anything from the library's own taxonomy. The overloads that take an
 * {@code Object} are for values that arrived without a type, such as a field
 * of a document.
 */
public final class Guards {

    private Guards() {}

    /**
     * @param value anything
     * @param label how the value is described in the message
     * @return the value as an int
     * @throws IllegalArgumentException when the value is not a whole number
     */
    public static int integer(Object value, String label) {
        if (!isWholeNumber(value)) {
            throw new IllegalArgumentException(label + " " + describe(value) + " is not a whole number");
        }
        return ((Number) value).intValue();
    }

    /**
     * @param value the number
     * @param min inclusive
     * @param max inclusive
     * @param label how the value is described in the message
     * @return the value
     * @throws IllegalArgumentException when the value is outside the range
     */
    public static int integerInRange(int value, int min, int max, String label) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(outside(Integer.toString(value), min, max, label));
        }
        return value;
    }

    /**
     * @param value anything
     * @param min inclusive
     * @param max inclusive
     * @param label how the value is described in the message
     * @return the value as an int
     * @throws IllegalArgumentException when the value is not a whole number in the range
     */
    public static int integerInRange(Object value, int min, int max, String label) {
        if (!isWholeNumber(value)) {
            throw new IllegalArgumentException(outside(describe(value), min, max, label));
        }
        double number = ((Number) value).doubleValue();
        if (number < min || number > max) {
            throw new IllegalArgumentException(outside(describe(value), min, max, label));
        }
        return (int) number;
    }

    /**
     * @param value anything
     * @param label how the value is described in the message
     * @return the value as a string
     * @throws IllegalArgumentException when the value is not a string with something in it
     */
    public static String nonEmptyString(Object value, String label) {
        if (!(value instanceof String text) || text.isEmpty()) {
            throw new IllegalArgumentException(label + " must be a non empty string");
        }
        return text;
    }

    /**
     * @param <T> the type of the value
     * @param value the value
     * @param allowed every value that is acceptable
     * @param label how the value is described in the message
     * @return the value
     * @throws IllegalArgumentException when the value is not one of those allowed
     */
    public static <T> T oneOf(T value, Collection<? extends T> allowed, String label) {
        if (!allowed.contains(value)) {
            String choices = allowed.stream().map(String::valueOf).collect(Collectors.joining(", "));
            throw new IllegalArgumentException(label + " " + describe(value) + " must be one of " + choices);
        }
        return value;
    }

    /**
     * @param value anything
     * @param label how the value is described in the message
     * @return the value as a list
     * @throws IllegalArgumentException when the value is not a list
     */
    public static List<?> array(Object value, String label) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(label + " must be an array");
        }
        return list;
    }

    /**
     * @param value anything
     * @param label how the value is described in the message
     * @return the value as a list
     * @throws IllegalArgumentException when the value is not a list with something in it
     */
    public static List<?> nonEmptyArray(Object value, String label) {
        List<?> list = array(value, label);
        if (list.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be empty");
        }
        return list;
    }

    /**
     * A function, which in Java can only be missing rather than of the wrong kind.
     *
     * @param <F> the functional type
     * @param value the function
     * @param label how the value is described in the message
     * @return the function
     * @throws IllegalArgumentException when there is no function
     */
    public static <F> F function(F value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " must be a function");
        }
        return value;
    }

    /**
     * A plain map of names to values, which is what every options bag and every
     * document field expects. A list or any other object is refused so that a
     * mistyped argument fails at the boundary instead of half way through a loop.
     *
     * @param value anything
     * @param label how the value is described in the message
     * @return the value as a map
     * @throws IllegalArgumentException when the value is not a map
     */
    public static Map<?, ?> plainObject(Object value, String label) {
        if (value == null || value instanceof List<?> || value.getClass().isArray()) {
            throw new IllegalArgumentException(label + " must be an object");
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(label + " must be a plain object");
        }
        return map;
    }

    /**
     * Render a value for an error message without letting a huge object flood
     * the log. Strings are quoted so that a stray space is visible.
     *
     * @param value anything
     * @return a short description
     */
    public static String describe(Object value) {
        if (value instanceof String text) {
            return Json.quote(text);
        }
        if (value instanceof Double || value instanceof Float) {
            return Json.number(((Number) value).doubleValue());
        }
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof List<?> list) {
            return "an array of " + list.size();
        }
        if (value instanceof Map<?, ?>) {
            return "object";
        }
        if (isFunction(value)) {
            return "function";
        }
        return "object";
    }

    private static boolean isWholeNumber(Object value) {
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return true;
        }
        if (value instanceof Double || value instanceof Float) {
            double number = ((Number) value).doubleValue();
            return Double.isFinite(number) && number == Math.rint(number);
        }
        return false;
    }

    private static boolean isFunction(Object value) {
        return value instanceof java.util.function.Function<?, ?>
                || value instanceof java.util.function.Supplier<?>
                || value instanceof java.util.function.Consumer<?>
                || value instanceof java.util.function.Predicate<?>
                || value instanceof Runnable;
    }

    private static String outside(String described, int min, int max, String label) {
        return label + " " + described + " is outside " + min + ".." + max;
    }
}
