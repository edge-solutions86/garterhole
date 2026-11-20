package io.github.rkaushikdev.garterhole;

import java.util.Map;

/**
 * Configuration is missing or contradictory, which is fatal at start up.
 */
public class ConfigException extends GarterholeException {

    private static final long serialVersionUID = 1L;

    /** The code every configuration failure carries. */
    public static final String CODE = "config_error";

    /**
     * @param message what is wrong with the settings
     */
    public ConfigException(String message) {
        this(message, Map.of());
    }

    /**
     * @param message what is wrong with the settings
     * @param details which setting, and what it said
     */
    public ConfigException(String message, Map<String, ?> details) {
        super(message, CODE, details);
    }
}
