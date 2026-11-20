package io.github.rkaushikdev.garterhole.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.util.Json;

/**
 * Bodies for the HTTP tests, written with single quotes so that they read like
 * the object literals they stand for, and requests that send them as JSON.
 */
public final class Documents {

    private Documents() {}

    /**
     * @param json JSON with single quotes in place of double ones
     * @return the document
     */
    public static JsonNode doc(String json) {
        return Json.parse(json.replace('\'', '"'));
    }

    /**
     * @param json an object, with single quotes in place of double ones
     * @return the object
     */
    public static ObjectNode object(String json) {
        return (ObjectNode) doc(json);
    }

    /**
     * A request saying it sends JSON, as every route test sends it.
     *
     * @param method the method
     * @param url the path and query
     * @param body the body, or {@code null} for none
     * @return the request
     */
    public static Request ask(String method, String url, JsonNode body) {
        return Request.builder().method(method).url(url).json(body).build();
    }

    /**
     * @param method the method
     * @param url the path and query
     * @return a request saying it sends JSON, with no body
     */
    public static Request ask(String method, String url) {
        return ask(method, url, null);
    }
}
