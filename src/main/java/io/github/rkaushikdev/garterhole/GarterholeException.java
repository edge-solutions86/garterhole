package io.github.rkaushikdev.garterhole;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The root of the error taxonomy. Everything the library throws on purpose is
 * one of these, which lets the HTTP layer map a failure onto a status code
 * without inspecting messages, and lets the command line decide whether to print
 * a stack trace.
 *
 * <p>Guard failures from {@link io.github.rkaushikdev.garterhole.util.Guards}
 * stay as the built in {@link IllegalArgumentException} and
 * {@link NullPointerException}. Those mean the caller passed nonsense, which is
 * a programming mistake rather than a rejected document.
 *
 * <p>Every exception carries a stable machine readable {@link #code()} and a bag
 * of details that is safe to serialise. Details whose value is {@code null} are
 * left out, the way an undefined field is left out of a JSON document.
 */
public class GarterholeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The code a plain {@code GarterholeException} carries when none is given. */
    public static final String DEFAULT_CODE = "garterhole_error";

    private final String code;
    private final transient Map<String, Object> details;

    /**
     * @param message what went wrong, written for the person reading it
     */
    public GarterholeException(String message) {
        this(message, DEFAULT_CODE, Map.of(), null);
    }

    /**
     * @param message what went wrong
     * @param cause the failure underneath this one
     */
    public GarterholeException(String message, Throwable cause) {
        this(message, DEFAULT_CODE, Map.of(), cause);
    }

    /**
     * @param message what went wrong
     * @param code the stable machine readable code
     * @param details facts about the failure that are safe to send to a caller
     */
    public GarterholeException(String message, String code, Map<String, ?> details) {
        this(message, code, details, null);
    }

    /**
     * @param message what went wrong
     * @param code the stable machine readable code
     * @param details facts about the failure that are safe to send to a caller
     * @param cause the failure underneath this one, or {@code null}
     */
    protected GarterholeException(String message, String code, Map<String, ?> details, Throwable cause) {
        super(message, cause);
        this.code = code == null ? DEFAULT_CODE : code;
        this.details = frozenCopy(details);
    }

    /**
     * @return the stable machine readable code, for example {@code not_found}
     */
    public String code() {
        return code;
    }

    /**
     * @return the details, which cannot be changed by whoever handles the error
     */
    public Map<String, Object> details() {
        return details;
    }

    /**
     * @return the name of the kind of failure, which is the simple class name
     */
    public String name() {
        return getClass().getSimpleName();
    }

    /**
     * The error as a document an HTTP layer can send.
     *
     * @return the name, code, message and details, in that order
     */
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("name", name());
        json.put("code", code);
        json.put("message", getMessage());
        json.put("details", new LinkedHashMap<>(details));
        return json;
    }

    /**
     * @param error anything at all
     * @return true when the error came from this library on purpose
     */
    public static boolean isGarterholeException(Object error) {
        return error instanceof GarterholeException;
    }

    private static Map<String, Object> frozenCopy(Map<String, ?> details) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (details != null) {
            details.forEach((key, value) -> {
                if (value != null) {
                    copy.put(key, value);
                }
            });
        }
        return Collections.unmodifiableMap(copy);
    }
}
