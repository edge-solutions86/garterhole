package io.github.rkaushikdev.garterhole;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Text that should have been place notation, a row or a touch could not be
 * read. Carries where in the source the reader gave up, which is what makes a
 * useful message in a form.
 */
public class NotationException extends GarterholeException {

    private static final long serialVersionUID = 1L;

    /** The code every notation failure carries. */
    public static final String CODE = "notation_error";

    private final String source;
    private final Integer position;

    /**
     * @param message what could not be read
     */
    public NotationException(String message) {
        this(message, null, null, Map.of());
    }

    /**
     * @param message what could not be read
     * @param source the text being read, or {@code null}
     * @param position the one based position the reader gave up at, or {@code null}
     */
    public NotationException(String message, String source, Integer position) {
        this(message, source, position, Map.of());
    }

    /**
     * @param message what could not be read
     * @param source the text being read, or {@code null}
     * @param position the one based position the reader gave up at, or {@code null}
     * @param details further facts about the failure
     */
    public NotationException(String message, String source, Integer position, Map<String, ?> details) {
        super(message, CODE, withPlace(details, source, position), null);
        this.source = source;
        this.position = position;
    }

    /**
     * @return the text that was being read, when it is known
     */
    public Optional<String> source() {
        return Optional.ofNullable(source);
    }

    /**
     * @return the one based position the reader gave up at, when it is known
     */
    public OptionalInt position() {
        return position == null ? OptionalInt.empty() : OptionalInt.of(position);
    }

    private static Map<String, Object> withPlace(Map<String, ?> details, String source, Integer position) {
        Map<String, Object> merged = new LinkedHashMap<>(details);
        merged.put("source", source);
        merged.put("position", position);
        return merged;
    }
}
