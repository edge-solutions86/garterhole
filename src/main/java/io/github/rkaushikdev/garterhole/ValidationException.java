package io.github.rkaushikdev.garterhole;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A document failed its own rules: a touch that does not come round, a tower
 * with two bells claiming the same number, a record with no ringers.
 *
 * <p>Besides the message, which describes the first problem, it can carry every
 * problem that was found, so that a form with three empty fields says so once.
 */
public class ValidationException extends GarterholeException {

    private static final long serialVersionUID = 1L;

    /** The code every validation failure carries. */
    public static final String CODE = "validation_failed";

    private final transient List<Object> issues;

    /**
     * @param message what is wrong
     */
    public ValidationException(String message) {
        this(message, Map.of(), List.of());
    }

    /**
     * @param message what is wrong
     * @param details facts about the failure that are safe to send to a caller
     */
    public ValidationException(String message, Map<String, ?> details) {
        this(message, details, List.of());
    }

    /**
     * @param message what is wrong
     * @param details facts about the failure that are safe to send to a caller
     * @param issues every problem that was found, each of which serialises
     */
    public ValidationException(String message, Map<String, ?> details, List<?> issues) {
        super(message, CODE, details, null);
        this.issues = List.copyOf(issues);
    }

    /**
     * @param message what is wrong
     * @param issues every problem that was found
     * @return the exception, carrying no details
     */
    public static ValidationException withIssues(String message, List<?> issues) {
        return new ValidationException(message, Map.of(), issues);
    }

    /**
     * @return every problem that was found, possibly none
     */
    public List<Object> issues() {
        return issues;
    }

    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>(super.toJson());
        json.put("issues", List.copyOf(issues));
        return json;
    }
}
