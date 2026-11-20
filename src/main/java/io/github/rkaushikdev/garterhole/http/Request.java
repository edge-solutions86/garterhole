package io.github.rkaushikdev.garterhole.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.Text;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * What arrived.
 *
 * <p>The request the runtime hands over is a stream with a few headers on it.
 * This wraps it in something the route handlers can read without knowing that:
 * the path, the query, the captured parameters and the body already parsed.
 * Header names are held in lower case, so a header can be asked for whatever
 * case it was sent in.
 *
 * <p>The body is read with a size limit. Without one, a single request can make
 * the service run out of memory, and a tower running this on a machine in the
 * vestry has no monitoring to notice.
 *
 * <p>A request is immutable; adding the captured parameters or an identifier
 * gives a new one.
 */
public final class Request {

    /** The most a request body may be, in bytes. */
    public static final int MAX_BODY_BYTES = 1024 * 1024;

    private final String method;
    private final String path;
    private final Map<String, String> query;
    private final Map<String, String> headers;
    private final Map<String, String> params;
    private final JsonNode body;
    private final String id;

    /**
     * Everything a request is built from. Any part may be {@code null}, which
     * means the usual default: {@code GET}, the path {@code /}, no headers, no
     * parameters, no body and no identifier.
     *
     * @param method the request method, in any case
     * @param url the path and query, for example {@code /towers?stage=8}
     * @param headers the headers by name, each a string or a list of strings
     * @param params the parameters a route captured
     * @param body the body already parsed, or a missing node for none
     * @param id the identifier the request carries
     */
    public record Parts(
            String method, String url, Map<String, ?> headers, Map<String, String> params, JsonNode body, String id) {}

    /**
     * @param parts what the request is made of
     * @throws ValidationException when there are no parts
     */
    public Request(Parts parts) {
        if (parts == null) {
            throw new ValidationException("a request needs its parts");
        }
        String url = parts.url() == null ? "/" : parts.url();
        int fragment = url.indexOf('#');
        String withoutFragment = fragment == -1 ? url : url.substring(0, fragment);
        int questionMark = withoutFragment.indexOf('?');
        String rawPath = questionMark == -1 ? withoutFragment : withoutFragment.substring(0, questionMark);
        this.method = (parts.method() == null ? "GET" : parts.method()).toUpperCase(Locale.ROOT);
        this.path = rawPath.startsWith("/") ? rawPath : "/" + rawPath;
        this.query = Collections.unmodifiableMap(
                questionMark == -1 ? new LinkedHashMap<>() : queryOf(withoutFragment.substring(questionMark + 1)));
        this.headers =
                Collections.unmodifiableMap(normaliseHeaders(parts.headers() == null ? Map.of() : parts.headers()));
        this.params =
                Collections.unmodifiableMap(new LinkedHashMap<>(parts.params() == null ? Map.of() : parts.params()));
        this.body = parts.body() == null ? MissingNode.getInstance() : parts.body();
        this.id = parts.id();
    }

    private Request(Request original, Map<String, String> params, String id) {
        this.method = original.method;
        this.path = original.path;
        this.query = original.query;
        this.headers = original.headers;
        this.params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
        this.body = original.body;
        this.id = id;
    }

    /**
     * @return a builder for a request, starting from a {@code GET} of {@code /}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @param url the path and query
     * @return a {@code GET} of that URL with nothing else about it
     */
    public static Request get(String url) {
        return builder().url(url).build();
    }

    /**
     * @return the method, in upper case
     */
    public String method() {
        return method;
    }

    /**
     * @return the path, without the query
     */
    public String path() {
        return path;
    }

    /**
     * @return the query parameters, decoded, in the order they were sent
     */
    public Map<String, String> query() {
        return query;
    }

    /**
     * @return the headers, by lower case name
     */
    public Map<String, String> headers() {
        return headers;
    }

    /**
     * @return the parameters the route captured
     */
    public Map<String, String> params() {
        return params;
    }

    /**
     * @return the body: a document for JSON, a text node for anything else, a
     *     missing node when there was none
     */
    public JsonNode body() {
        return body;
    }

    /**
     * @return the identifier the request carries, once the request log has given it one
     */
    public Optional<String> id() {
        return Optional.ofNullable(id);
    }

    /**
     * @param name a header name, in any case
     * @return the header, when it was sent
     */
    public Optional<String> header(String name) {
        return Optional.ofNullable(headers.get(String.valueOf(name).toLowerCase(Locale.ROOT)));
    }

    /**
     * @param name a parameter the route captures
     * @return its value
     * @throws ValidationException naming the parameter when the route does not capture it
     */
    public String param(String name) {
        String found = params.get(name);
        if (found == null) {
            throw new ValidationException("this route has no parameter called " + Json.quote(name));
        }
        return found;
    }

    /**
     * @param name a query parameter
     * @return its value, when it was sent and is not empty
     */
    public Optional<String> queryValue(String name) {
        String found = query.get(name);
        return found == null || found.isEmpty() ? Optional.empty() : Optional.of(found);
    }

    /**
     * @param name a query parameter
     * @param fallback what to use when it was not sent or is empty
     * @return its value, or the fallback
     */
    public String queryValue(String name, String fallback) {
        return queryValue(name).orElse(fallback);
    }

    /**
     * @return true when the caller said it was sending JSON
     */
    public boolean sendsJson() {
        String type = header("content-type").orElse("");
        return Text.trim(type.split(";", -1)[0]).equals("application/json");
    }

    /**
     * @param captured the parameters the router captured
     * @return the request with those parameters
     */
    public Request withParams(Map<String, String> captured) {
        return new Request(this, captured, id);
    }

    /**
     * @param identifier the identifier
     * @return the request carrying that identifier
     */
    public Request withId(String identifier) {
        return new Request(this, params, identifier);
    }

    /**
     * Read a body off a stream, stopping if it is too big.
     *
     * @param stream the body
     * @return the body as text
     * @throws ValidationException when it is longer than {@link #MAX_BODY_BYTES}
     * @throws UncheckedIOException when the stream fails
     */
    public static String readBody(InputStream stream) {
        return readBody(stream, MAX_BODY_BYTES);
    }

    /**
     * Read a body off a stream, stopping if it is too big.
     *
     * @param stream the body
     * @param limit the most bytes it may have
     * @return the body as text
     * @throws ValidationException when it is longer than the limit
     * @throws UncheckedIOException when the stream fails
     */
    public static String readBody(InputStream stream, int limit) {
        ByteArrayOutputStream chunks = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        try {
            int read = stream.read(chunk);
            while (read != -1) {
                if (chunks.size() + read > limit) {
                    throw new ValidationException("the request body is longer than " + limit + " bytes");
                }
                chunks.write(chunk, 0, read);
                read = stream.read(chunk);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure.getMessage(), failure);
        }
        return chunks.toString(StandardCharsets.UTF_8);
    }

    /**
     * Parse a body that says it is JSON.
     *
     * @param text the body
     * @return the document, or a missing node for an empty body
     * @throws ValidationException when the body is not JSON
     */
    public static JsonNode parseJsonBody(String text) {
        if (text.isEmpty()) {
            return MissingNode.getInstance();
        }
        try {
            return Json.parse(text);
        } catch (IllegalArgumentException failure) {
            throw new ValidationException("the request body is not JSON: " + failure.getMessage());
        }
    }

    private static Map<String, String> queryOf(String search) {
        Map<String, String> query = new LinkedHashMap<>();
        for (String pair : search.split("&", -1)) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String name = equals == -1 ? pair : pair.substring(0, equals);
            String value = equals == -1 ? "" : pair.substring(equals + 1);
            query.put(UriComponents.decodeQuery(name), UriComponents.decodeQuery(value));
        }
        return query;
    }

    private static Map<String, String> normaliseHeaders(Map<String, ?> given) {
        Map<String, String> lowered = new LinkedHashMap<>();
        given.forEach((name, value) -> {
            String text = value instanceof Collection<?> values
                    ? values.stream().map(String::valueOf).collect(Collectors.joining(", "))
                    : String.valueOf(value);
            lowered.put(name.toLowerCase(Locale.ROOT), text);
        });
        return lowered;
    }

    /** Builds a request one part at a time. */
    public static final class Builder {
        private String method;
        private String url;
        private final Map<String, Object> headers = new LinkedHashMap<>();
        private final Map<String, String> params = new LinkedHashMap<>();
        private JsonNode body;
        private String id;

        private Builder() {}

        /**
         * @param name the request method, in any case
         * @return this builder
         */
        public Builder method(String name) {
            this.method = name;
            return this;
        }

        /**
         * @param path the path and query
         * @return this builder
         */
        public Builder url(String path) {
            this.url = path;
            return this;
        }

        /**
         * @param name a header name
         * @param value its value, a string or a list of strings
         * @return this builder
         */
        public Builder header(String name, Object value) {
            headers.put(name, value);
            return this;
        }

        /**
         * @param given headers by name
         * @return this builder
         */
        public Builder headers(Map<String, ?> given) {
            headers.putAll(given);
            return this;
        }

        /**
         * Say the body is JSON, which is what every route that takes a body expects.
         *
         * @param document the body
         * @return this builder
         */
        public Builder json(JsonNode document) {
            headers.put("content-type", "application/json");
            this.body = document;
            return this;
        }

        /**
         * @param parsed the body, already parsed
         * @return this builder
         */
        public Builder body(JsonNode parsed) {
            this.body = parsed;
            return this;
        }

        /**
         * @param captured parameters a route captured
         * @return this builder
         */
        public Builder params(Map<String, String> captured) {
            params.putAll(captured);
            return this;
        }

        /**
         * @param identifier the identifier the request carries
         * @return this builder
         */
        public Builder id(String identifier) {
            this.id = identifier;
            return this;
        }

        /**
         * @return the request
         */
        public Request build() {
            return new Request(new Parts(method, url, headers, params, body, id));
        }
    }
}
