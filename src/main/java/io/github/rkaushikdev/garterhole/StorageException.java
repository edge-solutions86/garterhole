package io.github.rkaushikdev.garterhole;

import java.util.Map;

/**
 * The journal or a snapshot on disk is unusable.
 */
public class StorageException extends GarterholeException {

    private static final long serialVersionUID = 1L;

    /** The code every storage failure carries. */
    public static final String CODE = "storage_error";

    /**
     * @param message what is wrong with the store
     */
    public StorageException(String message) {
        this(message, Map.of(), null);
    }

    /**
     * @param message what is wrong with the store
     * @param cause the failure underneath, usually from the file system
     */
    public StorageException(String message, Throwable cause) {
        this(message, Map.of(), cause);
    }

    /**
     * @param message what is wrong with the store
     * @param details facts about the failure
     * @param cause the failure underneath, or {@code null}
     */
    public StorageException(String message, Map<String, ?> details, Throwable cause) {
        super(message, CODE, details, cause);
    }
}
