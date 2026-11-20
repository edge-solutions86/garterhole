package io.github.rkaushikdev.garterhole.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * Documents for the store tests, written with single quotes so that they read
 * like the object literals they stand for.
 */
final class Documents {

    private Documents() {}

    /**
     * @param json JSON with single quotes in place of double ones
     * @return the document
     */
    static JsonNode doc(String json) {
        return Json.parse(json.replace('\'', '"'));
    }

    /**
     * @param json each document, as for {@link #doc}
     * @return the documents, in order
     */
    static List<JsonNode> docs(String... json) {
        return Arrays.stream(json).map(Documents::doc).toList();
    }

    /**
     * @return a value with no JSON form, which is what a function is to {@code JSON.stringify}
     */
    static JsonNode function() {
        Supplier<Integer> function = () -> 1;
        return JsonNodeFactory.instance.pojoNode(function);
    }
}
