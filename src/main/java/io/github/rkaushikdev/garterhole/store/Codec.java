package io.github.rkaushikdev.garterhole.store;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.garterhole.StorageException;
import io.github.rkaushikdev.garterhole.ValidationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How a record is written to the journal.
 *
 * <p>One JSON object per line, with a checksum in front of it. The checksum is
 * what makes a half written line detectable: a process killed while appending
 * leaves a line that parses as JSON often enough to be dangerous, and a
 * mismatched checksum turns that into an error instead of a silently truncated
 * record.
 *
 * <p>The checksum is a Fletcher style sum rather than a cryptographic hash. It
 * is not defending against an attacker, only against a power cut, and it has to
 * be cheap enough to run on every line of a journal at start up. It is summed
 * over the UTF-16 code units of the JSON text, exactly as the original summed
 * them, so a journal written by either implementation reads in the other.
 */
public final class Codec {

    /** The separator between the checksum and the payload. */
    private static final char SEPARATOR = ' ';

    private static final int CHECKSUM_LENGTH = 8;
    private static final long HALF = 0xffff;

    private Codec() {}

    /**
     * The checksum of a string.
     *
     * @param text the text
     * @return eight lower case hexadecimal characters
     */
    public static String checksum(String text) {
        long low = HALF;
        long high = HALF;
        for (int index = 0; index < text.length(); index += 1) {
            low += text.charAt(index);
            high += low;
            if (index % 16 == 15) {
                low = (low & HALF) + (low >>> 16);
                high = (high & HALF) + (high >>> 16);
            }
        }
        low = (low & HALF) + (low >>> 16);
        high = (high & HALF) + (high >>> 16);
        long value = ((high & HALF) << 16) | (low & HALF);
        String hex = Long.toHexString(value);
        return "0".repeat(CHECKSUM_LENGTH - hex.length()) + hex;
    }

    /**
     * Write a record as a line, with no newline on the end.
     *
     * @param record the record
     * @return the checksum, a space and the JSON text
     * @throws ValidationException when there is no record or it has no JSON form
     */
    public static String encode(JsonNode record) {
        if (record == null || record.isMissingNode()) {
            throw new ValidationException("there is nothing to write");
        }
        String payload;
        try {
            payload = StoreJson.write(record)
                    .orElseThrow(() -> new ValidationException("this record cannot be written as JSON"));
        } catch (IllegalArgumentException failure) {
            throw new ValidationException("this record cannot be written as JSON: " + failure.getMessage());
        }
        if (payload.indexOf('\n') >= 0) {
            throw new ValidationException("a record cannot contain a newline once it is JSON");
        }
        return checksum(payload) + SEPARATOR + payload;
    }

    /**
     * Read a line back, reporting it as line zero when it goes wrong.
     *
     * @param line the line, without its newline
     * @return the record
     */
    public static JsonNode decode(String line) {
        return decode(line, 0);
    }

    /**
     * Read a line back.
     *
     * @param line the line, without its newline
     * @param lineNumber where the line was, for the error message
     * @return the record
     * @throws StorageException when the checksum is missing or wrong, or the payload is not JSON
     * @throws ValidationException when there is no line
     */
    public static JsonNode decode(String line, int lineNumber) {
        if (line == null) {
            throw new ValidationException("a journal line must be a string");
        }
        int at = line.indexOf(SEPARATOR);
        if (at != CHECKSUM_LENGTH) {
            throw new StorageException(
                    "line " + lineNumber + " does not start with a checksum", Map.of("lineNumber", lineNumber), null);
        }
        String found = line.substring(0, at);
        String payload = line.substring(at + 1);
        String wanted = checksum(payload);
        if (!found.equals(wanted)) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("lineNumber", lineNumber);
            details.put("found", found);
            details.put("wanted", wanted);
            throw new StorageException(
                    "line " + lineNumber + " is damaged: checksum " + found + " should be " + wanted, details, null);
        }
        try {
            return StoreJson.read(payload);
        } catch (IllegalArgumentException failure) {
            throw new StorageException(
                    "line " + lineNumber + " is not JSON: " + failure.getMessage(),
                    Map.of("lineNumber", lineNumber),
                    failure);
        }
    }

    /**
     * Read a whole file of lines, skipping blank ones. A trailing partial line
     * is reported rather than skipped, because losing the most recent record
     * silently is the one failure this format exists to prevent.
     *
     * @param text the contents of a journal
     * @return the records, in order
     * @throws ValidationException when there is no text
     */
    public static List<JsonNode> decodeAll(String text) {
        if (text == null) {
            throw new ValidationException("a journal must be read from a string");
        }
        List<JsonNode> records = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        for (int index = 0; index < lines.length; index += 1) {
            if (!lines[index].isEmpty()) {
                records.add(decode(lines[index], index + 1));
            }
        }
        return records;
    }

    /**
     * Write a run of records as the body of a journal file, with a newline
     * after every line including the last.
     *
     * @param records the records
     * @return the text, empty when there are no records
     */
    public static String encodeAll(Iterable<? extends JsonNode> records) {
        StringBuilder text = new StringBuilder();
        for (JsonNode record : records) {
            text.append(encode(record)).append('\n');
        }
        return text.toString();
    }
}
