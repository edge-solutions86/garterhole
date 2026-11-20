package io.github.rkaushikdev.garterhole.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.Version;
import io.github.rkaushikdev.garterhole.util.Json;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What goes back.
 *
 * <p>A handler returns one of these rather than writing to a socket, which is
 * what lets the routes be tested without a server running and what lets the
 * middleware add headers after the handler has finished.
 *
 * <p>A response is immutable. Adding a header gives a new one, so a middleware
 * cannot change a response another middleware is holding. The body is held as
 * a JSON tree: a document for a JSON answer, a text node for text, and a
 * missing node when there is no body at all.
 */
public final class Response {

    private static final String JSON_TYPE = "application/json; charset=utf-8";
    private static final String TEXT_TYPE = "text/plain; charset=utf-8";
    private static final int LOWEST_STATUS = 100;
    private static final int HIGHEST_STATUS = 599;

    private final int status;
    private final Map<String, String> headers;
    private final JsonNode body;

    /** An empty two hundred. */
    public Response() {
        this(200, Map.of(), MissingNode.getInstance());
    }

    /**
     * @param status the status code
     * @param headers the headers, by name, in the order they are written
     * @param body the body, or a missing node for none
     * @throws ValidationException when the status is not a status code
     */
    public Response(int status, Map<String, String> headers, JsonNode body) {
        if (status < LOWEST_STATUS || status > HIGHEST_STATUS) {
            throw new ValidationException(status + " is not a status code");
        }
        this.status = status;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        this.body = body == null ? MissingNode.getInstance() : body;
    }

    /**
     * What a response comes to on the wire.
     *
     * @param status the status code
     * @param headers every header written, including the length and the server label
     * @param text the body as text
     */
    public record Rendered(int status, Map<String, String> headers, String text) {}

    /**
     * @return the status code
     */
    public int status() {
        return status;
    }

    /**
     * @return the headers, by name, which cannot be changed
     */
    public Map<String, String> headers() {
        return headers;
    }

    /**
     * @return the body, a missing node when there is none
     */
    public JsonNode body() {
        return body;
    }

    /**
     * @param name a header name
     * @param value its value
     * @return the same response with the header added
     */
    public Response withHeader(String name, String value) {
        Map<String, String> more = new LinkedHashMap<>(headers);
        more.put(name, value);
        return new Response(status, more, body);
    }

    /**
     * @param newStatus a status code
     * @return the same response with a different status
     */
    public Response withStatus(int newStatus) {
        return new Response(newStatus, headers, body);
    }

    /**
     * The body as text, and the headers that describe it. The length is counted
     * in bytes, and a header the handler set wins over the ones added here.
     *
     * @return the response as it goes on the wire
     */
    public Rendered render() {
        String text = Json.isUndefined(body) ? "" : renderBody();
        Map<String, String> written = new LinkedHashMap<>();
        written.put("content-length", Integer.toString(text.getBytes(StandardCharsets.UTF_8).length));
        written.put("x-served-by", Version.buildLabel());
        written.putAll(headers);
        return new Rendered(status, Collections.unmodifiableMap(written), text);
    }

    /**
     * @param body the body, a document or any value that converts to one
     * @return a two hundred with a JSON body
     */
    public static Response json(Object body) {
        return json(body, 200, Map.of());
    }

    /**
     * @param body the body, a document or any value that converts to one
     * @param status the status code
     * @return a response with a JSON body
     */
    public static Response json(Object body, int status) {
        return json(body, status, Map.of());
    }

    /**
     * @param body the body, a document or any value that converts to one
     * @param status the status code
     * @param extra headers, which win over the content type set here
     * @return a response with a JSON body
     */
    public static Response json(Object body, int status, Map<String, String> extra) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("content-type", JSON_TYPE);
        headers.putAll(extra);
        return new Response(status, headers, body == null ? Json.mapper().nullNode() : Json.toNode(body));
    }

    /**
     * @param body the text
     * @return a two hundred with a plain text body
     */
    public static Response text(String body) {
        return text(body, Map.of());
    }

    /**
     * @param body the text
     * @param extra headers, which win over the content type set here
     * @return a two hundred with a text body
     */
    public static Response text(String body, Map<String, String> extra) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("content-type", TEXT_TYPE);
        headers.putAll(extra);
        return new Response(200, headers, TextNode.valueOf(String.valueOf(body)));
    }

    /**
     * @return a response with nothing in it, for a delete that worked
     */
    public static Response noContent() {
        return new Response(204, Map.of(), MissingNode.getInstance());
    }

    /**
     * @param location where the new thing can be read
     * @param body what was made
     * @return a two hundred and one saying where something was made
     */
    public static Response created(String location, Object body) {
        return json(body, 201, Map.of("location", location));
    }

    /**
     * An error response in the shape every error takes, so that a client can
     * read one without knowing which handler produced it.
     *
     * @param status the status code
     * @param code the stable code of the error
     * @param message what went wrong
     * @return the response
     */
    public static Response problem(int status, String code, String message) {
        return problem(status, code, message, List.of());
    }

    /**
     * An error response in the shape every error takes, carrying the problems
     * found when there are any.
     *
     * @param status the status code
     * @param code the stable code of the error
     * @param message what went wrong
     * @param issues every problem found; none leaves the field out
     * @return the response
     */
    public static Response problem(int status, String code, String message, List<?> issues) {
        ObjectNode error = Json.object().put("code", code).put("message", message);
        if (!issues.isEmpty()) {
            error.set("issues", Json.toNode(issues));
        }
        ObjectNode body = Json.object();
        body.set("error", error);
        return json(body, status);
    }

    private String renderBody() {
        if (body.isTextual()) {
            return body.textValue();
        }
        if (headers.getOrDefault("content-type", "").startsWith("application/json")) {
            return Json.stringify(body);
        }
        return Json.templateText(body);
    }
}
