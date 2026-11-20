package io.github.rkaushikdev.garterhole.tower;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.ArrayList;
import java.util.List;

/**
 * Reading the fields of a tower or band document, where a field that is left
 * out and a field written as {@code null} both mean the same thing: not known.
 */
final class DocumentFields {

    private DocumentFields() {}

    /**
     * @param field a field of a document
     * @return true when it is left out or written as {@code null}
     */
    static boolean isAbsent(JsonNode field) {
        return Json.isUndefined(field) || field.isNull();
    }

    /**
     * A field read as text. A string is taken as it is, and any other value is
     * written the way the original turned a value into text.
     *
     * @param field a field of a document
     * @return the text, or {@code null} when the field is absent
     */
    static String text(JsonNode field) {
        if (isAbsent(field)) {
            return null;
        }
        return field.isTextual() ? field.textValue() : Json.templateText(field);
    }

    /**
     * @param field a field of a document that should be a list
     * @return its entries, none when it is absent
     * @throws ValidationException when it is there but is not a list
     */
    static List<JsonNode> elements(JsonNode field) {
        if (isAbsent(field)) {
            return List.of();
        }
        if (!field.isArray()) {
            throw new ValidationException(Json.templateText(field) + " is not a list");
        }
        List<JsonNode> entries = new ArrayList<>();
        field.forEach(entries::add);
        return entries;
    }
}
