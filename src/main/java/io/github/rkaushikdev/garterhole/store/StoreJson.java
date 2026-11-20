package io.github.rkaushikdev.garterhole.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * JSON text exactly as the journal and the snapshot need it.
 *
 * <p>Every line of the journal carries a checksum of its JSON text, and the
 * snapshot carries one of its state, so the text has to be the same text the
 * JavaScript implementation wrote, byte for byte, or a journal written by it
 * would not load. That rules out an ordinary serialiser, which differs in small
 * ways: how a fraction or a large number is written, whether a control
 * character is escaped in upper or lower case, and the order of keys that look
 * like array indexes, which JavaScript always puts first.
 *
 * <p>Reading is strict in the same way {@code JSON.parse} is: trailing text
 * after the value is an error rather than something to ignore.
 */
final class StoreJson {

    /** The largest integer a JavaScript number holds exactly. */
    private static final long LARGEST_EXACT = 9_007_199_254_740_992L;

    /** The largest array index a JavaScript object orders numerically. */
    private static final long LARGEST_INDEX = 4_294_967_294L;

    private static final ObjectReader STRICT =
            Json.mapper().reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private StoreJson() {}

    /**
     * Write a value the way {@code JSON.stringify} does.
     *
     * @param value the value
     * @return the text, or nothing when the value has no JSON form, which is
     *     what {@code JSON.stringify} says by answering {@code undefined}
     * @throws IllegalArgumentException when the value contains itself
     */
    static Optional<String> write(JsonNode value) {
        if (isUndefined(value)) {
            return Optional.empty();
        }
        StringBuilder text = new StringBuilder();
        append(text, value, Collections.newSetFromMap(new IdentityHashMap<>()));
        return Optional.of(text.toString());
    }

    /**
     * Read JSON text the way {@code JSON.parse} does.
     *
     * @param text the text
     * @return the value
     * @throws IllegalArgumentException naming what could not be read
     */
    static JsonNode read(String text) {
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
     * A value written the way a JavaScript template literal writes it, which is
     * how the original put a stray field into an error message.
     *
     * @param value the value, possibly missing
     * @return the text
     */
    static String templateText(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isPojo()) {
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
                parts.add(element.isNull() || element.isMissingNode() ? "" : templateText(element));
            }
            return String.join(",", parts);
        }
        if (value.isObject()) {
            return "[object Object]";
        }
        return value.asText();
    }

    /**
     * True when the value stands for JavaScript's {@code undefined}: nothing at
     * all, a field that is not there, or a Java value that has no JSON form.
     */
    static boolean isUndefined(JsonNode value) {
        return value == null || value.isMissingNode() || value.isPojo();
    }

    private static void append(StringBuilder text, JsonNode value, Set<JsonNode> ancestors) {
        if (value.isContainerNode()) {
            if (!ancestors.add(value)) {
                throw new IllegalArgumentException("Converting circular structure to JSON");
            }
            if (value.isObject()) {
                appendObject(text, (ObjectNode) value, ancestors);
            } else {
                appendArray(text, (ArrayNode) value, ancestors);
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

    private static void appendObject(StringBuilder text, ObjectNode object, Set<JsonNode> ancestors) {
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
            appendString(text, name);
            text.append(':');
            append(text, field, ancestors);
        }
        text.append('}');
    }

    private static void appendArray(StringBuilder text, ArrayNode array, Set<JsonNode> ancestors) {
        text.append('[');
        for (int index = 0; index < array.size(); index += 1) {
            if (index > 0) {
                text.append(',');
            }
            JsonNode element = array.get(index);
            if (isUndefined(element)) {
                text.append("null");
            } else {
                append(text, element, ancestors);
            }
        }
        text.append(']');
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
        return Json.number(number.doubleValue());
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
