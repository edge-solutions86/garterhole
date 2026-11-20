package io.github.rkaushikdev.garterhole.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reading and writing JSON exactly the way the rest of the library expects it.
 *
 * <p>Documents travel as JSON everywhere: in the journal, over HTTP, out of the
 * command line and into the log. The text written here is the text
 * {@code JSON.stringify} writes for the same value, byte for byte, because the
 * journal carries a checksum of each line and the files written by the earlier
 * JavaScript implementation have to go on loading. An ordinary serialiser
 * differs in small ways that matter for that: a whole number held as a double
 * written as {@code 250.0}, a control character escaped in upper case, and keys
 * that look like array indexes, which JavaScript always puts first.
 *
 * <p>Reading is strict in the same way {@code JSON.parse} is: text after the
 * value is an error rather than something to ignore.
 *
 * <p>Values are carried as Jackson trees. A missing node, or a node holding a
 * plain Java object with no JSON form, stands for JavaScript's
 * {@code undefined}: it is left out of an object and written as {@code null} in
 * a list.
 */
public final class Json {

    /** The one mapper, configured once. It is thread safe once configured. */
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .build();

    private static final ObjectReader STRICT = MAPPER.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    /** The largest integer a JavaScript number holds exactly. */
    private static final long LARGEST_EXACT = 9_007_199_254_740_992L;

    /** The largest array index a JavaScript object orders numerically. */
    private static final long LARGEST_INDEX = 4_294_967_294L;

    private static final int LOWEST_PLAIN_EXPONENT = -6;
    private static final String INDENT = "  ";

    private Json() {}

    /**
     * @return the shared mapper, for converting between documents and values
     */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * @return a new empty object node
     */
    public static ObjectNode object() {
        return JsonNodeFactory.instance.objectNode();
    }

    /**
     * @return a new empty array node
     */
    public static ArrayNode array() {
        return JsonNodeFactory.instance.arrayNode();
    }

    /**
     * Turn a value into a document.
     *
     * @param value a map, a list, a record, a plain value or a node
     * @return the document
     */
    public static JsonNode toNode(Object value) {
        return value instanceof JsonNode node ? node : MAPPER.valueToTree(value);
    }

    /**
     * Write a value on one line, as {@code JSON.stringify(value)} does.
     *
     * @param value a document, a map, a list, a record or a plain value
     * @return the JSON text
     * @throws IllegalArgumentException when the value has no JSON form or contains itself
     */
    public static String stringify(Object value) {
        return write(toNode(value)).orElseThrow(() -> new IllegalArgumentException("the value has no JSON form"));
    }

    /**
     * Write a value indented by two spaces, as {@code JSON.stringify(value, null, 2)} does.
     *
     * @param value a document, a map, a list, a record or a plain value
     * @return the JSON text
     * @throws IllegalArgumentException when the value has no JSON form or contains itself
     */
    public static String pretty(Object value) {
        JsonNode node = toNode(value);
        if (isUndefined(node)) {
            throw new IllegalArgumentException("the value has no JSON form");
        }
        StringBuilder text = new StringBuilder();
        append(text, node, INDENT, "", Collections.newSetFromMap(new IdentityHashMap<>()));
        return text.toString();
    }

    /**
     * Write a document on one line.
     *
     * @param value the document
     * @return the text, or nothing when the value has no JSON form, which is what
     *     {@code JSON.stringify} says by answering {@code undefined}
     * @throws IllegalArgumentException when the value contains itself
     */
    public static Optional<String> write(JsonNode value) {
        if (isUndefined(value)) {
            return Optional.empty();
        }
        StringBuilder text = new StringBuilder();
        append(text, value, "", "", Collections.newSetFromMap(new IdentityHashMap<>()));
        return Optional.of(text.toString());
    }

    /**
     * A string written as a JSON string literal, quotes and all. This is how a
     * value is shown in a message, so that a stray space is visible.
     *
     * @param text the string, or {@code null}
     * @return the literal, or {@code null} written bare when there is no string
     */
    public static String quote(String text) {
        if (text == null) {
            return "null";
        }
        StringBuilder written = new StringBuilder(text.length() + 2);
        appendString(written, text);
        return written.toString();
    }

    /**
     * Read JSON text the way {@code JSON.parse} does.
     *
     * @param text the text
     * @return the document it holds
     * @throws IllegalArgumentException naming what the reader could not accept
     */
    public static JsonNode parse(String text) {
        try {
            JsonNode node = STRICT.readTree(text);
            if (node == null || node.isMissingNode()) {
                throw new IllegalArgumentException("Unexpected end of JSON input");
            }
            return node;
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException(failure.getOriginalMessage(), failure);
        }
    }

    /**
     * True when the value stands for JavaScript's {@code undefined}: nothing at
     * all, a field that is not there, or a Java value that has no JSON form.
     *
     * @param value the value
     * @return true when there is no value to write
     */
    public static boolean isUndefined(JsonNode value) {
        return value == null || value.isMissingNode() || value.isPojo();
    }

    /**
     * A value written the way a JavaScript template literal writes it, which is
     * how a stray field is put into an error message.
     *
     * @param value the value, possibly missing
     * @return the text: a string as it is, {@code undefined} for nothing, a list
     *     joined with commas and an object as {@code [object Object]}
     */
    public static String templateText(JsonNode value) {
        if (isUndefined(value)) {
            return "undefined";
        }
        if (value.isTextual()) {
            return value.textValue();
        }
        if (value.isNumber()) {
            return numberText(value);
        }
        if (value.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode element : value) {
                parts.add(element.isNull() || isUndefined(element) ? "" : templateText(element));
            }
            return String.join(",", parts);
        }
        if (value.isObject()) {
            return "[object Object]";
        }
        return value.asText();
    }

    /**
     * Put a number into a document the way a JavaScript runtime would hold it: a
     * whole number goes in as a whole number, so the document compares equal to
     * one read back from its own text.
     *
     * @param node the document
     * @param name the field
     * @param value the number
     */
    public static void putNumber(ObjectNode node, String name, double value) {
        if (Double.isFinite(value) && value == Math.rint(value) && Math.abs(value) <= LARGEST_EXACT) {
            node.put(name, (long) value);
        } else {
            node.put(name, value);
        }
    }

    /**
     * Write a number the way a JavaScript runtime does: the shortest digits
     * that read back as the same number, with no trailing {@code .0}, and in
     * exponent form only when the number is very large or very small.
     *
     * @param value a number
     * @return the number as text
     */
    public static String number(double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "Infinity" : "-Infinity";
        }
        if (value == 0) {
            return "0";
        }
        if (value == Math.rint(value) && Math.abs(value) <= LARGEST_EXACT) {
            return Long.toString((long) value);
        }
        BigDecimal exact = new BigDecimal(Double.toString(Math.abs(value))).stripTrailingZeros();
        String digits = exact.unscaledValue().toString();
        int exponent = digits.length() - exact.scale();
        String sign = value < 0 ? "-" : "";
        if (exponent > 0 && exponent <= 21) {
            if (digits.length() <= exponent) {
                return sign + digits + "0".repeat(exponent - digits.length());
            }
            return sign + digits.substring(0, exponent) + "." + digits.substring(exponent);
        }
        if (exponent <= 0 && exponent > LOWEST_PLAIN_EXPONENT) {
            return sign + "0." + "0".repeat(-exponent) + digits;
        }
        String mantissa = digits.length() == 1 ? digits : digits.charAt(0) + "." + digits.substring(1);
        int power = exponent - 1;
        return sign + mantissa + "e" + (power < 0 ? "-" : "+") + Math.abs(power);
    }

    private static void append(
            StringBuilder text, JsonNode value, String indent, String depth, Set<JsonNode> ancestors) {
        if (value.isContainerNode()) {
            if (!ancestors.add(value)) {
                throw new IllegalArgumentException("Converting circular structure to JSON");
            }
            if (value.isObject()) {
                appendObject(text, (ObjectNode) value, indent, depth, ancestors);
            } else {
                appendArray(text, (ArrayNode) value, indent, depth, ancestors);
            }
            ancestors.remove(value);
        } else if (value.isTextual()) {
            appendString(text, value.textValue());
        } else if (value.isNumber()) {
            text.append(isFinite(value) ? numberText(value) : "null");
        } else if (value.isBoolean()) {
            text.append(value.booleanValue());
        } else if (value.isBinary()) {
            appendString(text, value.asText());
        } else {
            text.append("null");
        }
    }

    private static void appendObject(
            StringBuilder text, ObjectNode object, String indent, String depth, Set<JsonNode> ancestors) {
        String inner = depth + indent;
        text.append('{');
        boolean first = true;
        for (String name : propertyOrder(object)) {
            JsonNode field = object.get(name);
            if (isUndefined(field)) {
                continue;
            }
            if (!first) {
                text.append(',');
            }
            first = false;
            newLine(text, indent, inner);
            appendString(text, name);
            text.append(indent.isEmpty() ? ":" : ": ");
            append(text, field, indent, inner, ancestors);
        }
        if (!first) {
            newLine(text, indent, depth);
        }
        text.append('}');
    }

    private static void appendArray(
            StringBuilder text, ArrayNode array, String indent, String depth, Set<JsonNode> ancestors) {
        String inner = depth + indent;
        text.append('[');
        for (int index = 0; index < array.size(); index += 1) {
            if (index > 0) {
                text.append(',');
            }
            newLine(text, indent, inner);
            JsonNode element = array.get(index);
            if (isUndefined(element)) {
                text.append("null");
            } else {
                append(text, element, indent, inner, ancestors);
            }
        }
        if (!array.isEmpty()) {
            newLine(text, indent, depth);
        }
        text.append(']');
    }

    private static void newLine(StringBuilder text, String indent, String depth) {
        if (!indent.isEmpty()) {
            text.append('\n').append(depth);
        }
    }

    /**
     * The order JavaScript keeps an object's keys in: keys that are array
     * indexes first, ascending, then every other key in the order it was added.
     */
    private static List<String> propertyOrder(ObjectNode object) {
        List<String> indexes = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (Iterator<String> names = object.fieldNames(); names.hasNext(); ) {
            String name = names.next();
            (isArrayIndex(name) ? indexes : others).add(name);
        }
        if (indexes.isEmpty()) {
            return others;
        }
        indexes.sort((left, right) -> Long.compare(Long.parseLong(left), Long.parseLong(right)));
        indexes.addAll(others);
        return indexes;
    }

    private static boolean isArrayIndex(String name) {
        if (name.isEmpty() || name.length() > 10) {
            return false;
        }
        if (name.length() > 1 && name.charAt(0) == '0') {
            return false;
        }
        for (int index = 0; index < name.length(); index += 1) {
            char character = name.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
        }
        return Long.parseLong(name) <= LARGEST_INDEX;
    }

    private static boolean isFinite(JsonNode number) {
        return !number.isFloatingPointNumber() || Double.isFinite(number.doubleValue());
    }

    private static String numberText(JsonNode number) {
        if (number.isIntegralNumber() && number.canConvertToLong()) {
            long value = number.longValue();
            if (Math.abs(value) <= LARGEST_EXACT) {
                return Long.toString(value);
            }
        }
        return number(number.doubleValue());
    }

    /** A string literal escaped exactly as {@code JSON.stringify} escapes it. */
    private static void appendString(StringBuilder text, String value) {
        text.append('"');
        for (int index = 0; index < value.length(); index += 1) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> text.append("\\\"");
                case '\\' -> text.append("\\\\");
                case '\b' -> text.append("\\b");
                case '\f' -> text.append("\\f");
                case '\n' -> text.append("\\n");
                case '\r' -> text.append("\\r");
                case '\t' -> text.append("\\t");
                default -> {
                    if (character < 0x20 || isLoneSurrogate(value, index)) {
                        text.append(String.format("\\u%04x", (int) character));
                    } else {
                        text.append(character);
                    }
                }
            }
        }
        text.append('"');
    }

    private static boolean isLoneSurrogate(String value, int index) {
        char character = value.charAt(index);
        if (Character.isHighSurrogate(character)) {
            return index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1));
        }
        if (Character.isLowSurrogate(character)) {
            return index == 0 || !Character.isHighSurrogate(value.charAt(index - 1));
        }
        return false;
    }
}
