package io.github.rkaushikdev.garterhole;

import java.util.Map;

/**
 * A write lost a race, or would have created a duplicate.
 */
public class ConflictException extends GarterholeException {

    private static final long serialVersionUID = 1L;

    /** The code every conflict carries. */
    public static final String CODE = "conflict";

    /**
     * @param message what the write collided with
     */
    public ConflictException(String message) {
        this(message, Map.of());
    }

    /**
     * @param message what the write collided with
     * @param details facts about the collision that are safe to send to a caller
     */
    public ConflictException(String message, Map<String, ?> details) {
        super(message, CODE, details);
    }
}
