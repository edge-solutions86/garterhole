package io.github.rkaushikdev.garterhole.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.fasterxml.jackson.core.util.Instantiatable;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.io.Serial;
import java.math.BigDecimal;

/**
 * Reading and writing JSON, the way the rest of the library expects it.
 *
 * <p>Documents travel as JSON everywhere: in the journal, over HTTP and out of
 * the command line. Two things about that are worth fixing in one place. A
 * number is written the way a JavaScript runtime writes it, so a whole number
 * of milliseconds is {@code 250} rather than {@code 250.0} and the files this
 * writes match the files the earlier implementation wrote. And the indented
 * form puts a space after the colon and nothing before it, which is the form a
 * person reading a peal record expects.
 */
public final class Json {

    /** The one mapper, configured once. It is thread safe once configured. */
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .addModule(new SimpleModule("garterhole-numbers")
                    .addSerializer(Double.class, new DoubleSerializer())
                    .addSerializer(double.class, new DoubleSerializer())
                    .addSerializer(Float.class, new FloatSerializer())
                    .addSerializer(float.class, new FloatSerializer()))
            .build();

    private static final double EXPONENT_ABOVE = 1e21;
    private static final int LOWEST_PLAIN_EXPONENT = -6;

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
     * Write a value on one line.
     *
     * @param value a document, a map, a list or a plain value
     * @return the JSON text
     */
    public static String stringify(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException(
                    "the value cannot be written as JSON: " + failure.getOriginalMessage(), failure);
        }
    }

    /**
     * Write a value indented by two spaces, one field per line.
     *
     * @param value a document, a map, a list or a plain value
     * @return the JSON text
     */
    public static String pretty(Object value) {
        try {
            return MAPPER.writer(new TwoSpacePrinter()).writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException(
                    "the value cannot be written as JSON: " + failure.getOriginalMessage(), failure);
        }
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
        return "\"" + new String(JsonStringEncoder.getInstance().quoteAsString(text)) + "\"";
    }

    /**
     * Read JSON text.
     *
     * @param text the text
     * @return the document it holds
     * @throws IllegalArgumentException naming what the reader could not accept
     */
    public static JsonNode parse(String text) {
        try {
            JsonNode node = MAPPER.readTree(text);
            if (node == null || node.isMissingNode()) {
                throw new IllegalArgumentException("there is no JSON value in the text");
            }
            return node;
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException(failure.getOriginalMessage(), failure);
        }
    }

    /**
     * Turn a value into a document.
     *
     * @param value a map, a list, a record or a plain value
     * @return the document
     */
    public static JsonNode toNode(Object value) {
        return MAPPER.valueToTree(value);
    }

    /**
     * Write a number the way a JavaScript runtime does: the shortest digits
     * that read back as the same number, with no trailing {@code .0}, and in
     * exponent form only when the number is very large or very small.
     *
     * @param value a finite number
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
        if (value == Math.rint(value) && Math.abs(value) < EXPONENT_ABOVE) {
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

    /** Doubles are written the JavaScript way, and a value that is not finite becomes null. */
    private static final class DoubleSerializer extends StdSerializer<Double> {
        @Serial
        private static final long serialVersionUID = 1L;

        DoubleSerializer() {
            super(Double.class);
        }

        @Override
        public void serialize(Double value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            if (value.isNaN() || value.isInfinite()) {
                generator.writeNull();
            } else {
                generator.writeNumber(number(value));
            }
        }
    }

    /** Floats are widened and written the same way as doubles. */
    private static final class FloatSerializer extends StdSerializer<Float> {
        @Serial
        private static final long serialVersionUID = 1L;

        FloatSerializer() {
            super(Float.class);
        }

        @Override
        public void serialize(Float value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            if (value.isNaN() || value.isInfinite()) {
                generator.writeNull();
            } else {
                generator.writeNumber(number(Double.parseDouble(value.toString())));
            }
        }
    }

    /**
     * Indents by two spaces with a space after each colon, and writes an empty
     * object or list as {@code {}} or {@code []}.
     */
    private static final class TwoSpacePrinter implements PrettyPrinter, Instantiatable<TwoSpacePrinter> {
        private int depth;

        @Override
        public TwoSpacePrinter createInstance() {
            return new TwoSpacePrinter();
        }

        @Override
        public void writeRootValueSeparator(JsonGenerator generator) {
            // A single document is written, so there is nothing between roots.
        }

        @Override
        public void writeStartObject(JsonGenerator generator) throws IOException {
            generator.writeRaw('{');
            depth += 1;
        }

        @Override
        public void beforeObjectEntries(JsonGenerator generator) throws IOException {
            newLine(generator);
        }

        @Override
        public void writeObjectFieldValueSeparator(JsonGenerator generator) throws IOException {
            generator.writeRaw(": ");
        }

        @Override
        public void writeObjectEntrySeparator(JsonGenerator generator) throws IOException {
            generator.writeRaw(',');
            newLine(generator);
        }

        @Override
        public void writeEndObject(JsonGenerator generator, int entries) throws IOException {
            depth -= 1;
            if (entries > 0) {
                newLine(generator);
            }
            generator.writeRaw('}');
        }

        @Override
        public void writeStartArray(JsonGenerator generator) throws IOException {
            generator.writeRaw('[');
            depth += 1;
        }

        @Override
        public void beforeArrayValues(JsonGenerator generator) throws IOException {
            newLine(generator);
        }

        @Override
        public void writeArrayValueSeparator(JsonGenerator generator) throws IOException {
            generator.writeRaw(',');
            newLine(generator);
        }

        @Override
        public void writeEndArray(JsonGenerator generator, int values) throws IOException {
            depth -= 1;
            if (values > 0) {
                newLine(generator);
            }
            generator.writeRaw(']');
        }

        private void newLine(JsonGenerator generator) throws IOException {
            generator.writeRaw('\n');
            generator.writeRaw("  ".repeat(depth));
        }
    }
}
