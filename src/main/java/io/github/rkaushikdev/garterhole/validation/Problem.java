package io.github.rkaushikdev.garterhole.validation;

import java.util.List;

/**
 * One thing wrong with a document, and where it is.
 *
 * @param path where in the document, for example {@code band[2].name}, or {@code (root)}
 * @param message what is wrong there, for example {@code must be a string}
 */
public record Problem(String path, String message) {

    /** The path given to a problem with the whole value. */
    public static final String ROOT = "(root)";

    /**
     * A single problem, naming the root when the path is empty.
     *
     * @param path where in the document
     * @param message what is wrong there
     * @return a list holding the one problem
     */
    public static List<Problem> at(String path, String message) {
        return List.of(new Problem(path.isEmpty() ? ROOT : path, message));
    }
}
