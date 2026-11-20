package io.github.rkaushikdev.garterhole.validation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * A check on the shape of something that came in over the wire.
 *
 * <p>A schema is a function from a value and a path to a list of problems. That
 * is the whole idea; {@link Schemas} builds bigger schemas out of smaller ones.
 * The list rather than an exception matters: a form with three empty fields
 * should say so once, not three times in a row.
 *
 * <p>A field that was not sent at all arrives as a missing node, and a field
 * sent as {@code null} arrives as a null node, because the two mean different
 * things to an optional field.
 */
@FunctionalInterface
public interface Schema {

    /**
     * @param value the value, a missing node when nothing was sent
     * @param path where the value sits in the document, empty for the root
     * @return every problem found, none when the value is sound
     */
    List<Problem> problems(JsonNode value, String path);

    /**
     * @param value the value, a missing node when nothing was sent
     * @return every problem found, with paths starting from the root
     */
    default List<Problem> problems(JsonNode value) {
        return problems(value, "");
    }
}
