package io.github.rkaushikdev.garterhole.http.routes;

import io.github.rkaushikdev.garterhole.validation.Schema;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The fields of a request body schema, kept in the order they are written, so
 * that the problems with a body are reported in the order its fields are read.
 */
final class Fields {

    private Fields() {}

    /**
     * @param entries each field's name and schema
     * @return the fields, in order
     */
    @SafeVarargs
    static Map<String, Schema> of(Map.Entry<String, ? extends Schema>... entries) {
        Map<String, Schema> fields = new LinkedHashMap<>();
        for (Map.Entry<String, ? extends Schema> entry : entries) {
            fields.put(entry.getKey(), entry.getValue());
        }
        return fields;
    }
}
