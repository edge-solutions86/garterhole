package io.github.rkaushikdev.garterhole.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.http.middleware.ErrorHandler;
import io.github.rkaushikdev.garterhole.http.middleware.RateLimiter;
import io.github.rkaushikdev.garterhole.http.middleware.RequestLog;
import io.github.rkaushikdev.garterhole.util.Clock;
import io.github.rkaushikdev.garterhole.util.IdSource;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.Logger;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The HTTP surface.
 *
 * <p>Two things happen here and they are kept apart on purpose.
 * {@link #handle} takes a request and gives a response, and knows nothing
 * about sockets; {@link #listen} wires that to the JDK's HTTP server. Almost
 * every test of this package exercises the first, which is why so few of them
 * open a port.
 *
 * <p>The middleware is applied in one place and in a stated order, so that a
 * reader can see that the rate limit runs before the handler and the request
 * log wraps everything including the error handling.
 *
 * <p>A handler that gives back no response is a bug in this service rather
 * than something the caller sent, so it is answered 500 rather than 400.
 *
 * <p>Over a socket, requests are answered one at a time on the HTTP server's
 * own thread, as the original answered them on its one event loop, so nothing
 * behind the routes has to be safe for concurrent use.
 */
public final class Server {

    private static final String DEFAULT_HOST = "127.0.0.1";

    private final Router router;
    private final Logger logger;
    private final Context context;
    private final Handler pipeline;

    /**
     * What a server is built from.
     *
     * @param router the routes
     * @param logger where the request log and failures go, or {@code null} for nowhere
     * @param rateLimit how many requests a caller may make in a minute, or {@code null} for the default
     * @param context what every route handler is given, or {@code null} for nothing
     */
    public record Options(Router router, Logger logger, Integer rateLimit, Context context) {

        /**
         * @param router the routes
         * @return options for a server over those routes, with everything else the default
         */
        public static Options of(Router router) {
            return new Options(router, null, null, null);
        }

        /**
         * @param given where the request log and failures go
         * @return these options with that logger
         */
        public Options withLogger(Logger given) {
            return new Options(router, given, rateLimit, context);
        }

        /**
         * @param perMinute how many requests a caller may make in a minute
         * @return these options with that limit
         */
        public Options withRateLimit(int perMinute) {
            return new Options(router, logger, perMinute, context);
        }

        /**
         * @param given what every route handler is given
         * @return these options with that context
         */
        public Options withContext(Context given) {
            return new Options(router, logger, rateLimit, given);
        }
    }

    /**
     * A server that is listening.
     */
    public interface Listening extends AutoCloseable {

        /**
         * @return the port it is listening on
         */
        int port();

        /** Stop listening. */
        @Override
        void close();
    }

    /**
     * @param options the router, and optionally the logger, the rate limit and the context
     * @throws ValidationException when there are no options or no router
     */
    public Server(Options options) {
        if (options == null) {
            throw new ValidationException("a server needs its options");
        }
        if (options.router() == null) {
            throw new ValidationException("a server needs a Router");
        }
        this.router = options.router();
        this.logger = options.logger() == null ? Logger.silent() : options.logger();
        this.context = options.context() == null ? Context.of(null, router) : options.context();
        int limit = options.rateLimit() == null ? RateLimiter.DEFAULT_LIMIT : options.rateLimit();
        this.pipeline = RequestLog.withRequestLog(
                RateLimiter.withRateLimit(
                        ErrorHandler.catchingErrors(this::route, logger), new RateLimiter(limit, Clock.system())),
                logger,
                IdSource.defaultIds(),
                Clock.system());
    }

    /**
     * Answer a request: identify and log it, apply the rate limit, find the
     * route and turn anything it throws into an answer.
     *
     * @param request what arrived
     * @return what goes back
     */
    public Response handle(Request request) {
        return pipeline.handle(request);
    }

    /**
     * @return the routes this server answers
     */
    public Router router() {
        return router;
    }

    /**
     * Find the handler for a request and run it.
     *
     * @param request what arrived
     * @return the handler's answer, or a 404 or a 405 when no route matches
     */
    Response route(Request request) {
        Optional<Router.Match> found = router.match(request.method(), request.path());
        if (found.isEmpty()) {
            List<String> allowed = router.methodsFor(request.path());
            if (!allowed.isEmpty()) {
                return ErrorHandler.methodNotAllowed(request.method(), allowed);
            }
            return ErrorHandler.notFound(request.path());
        }
        Router.Match match = found.get();
        Response answer = match.handler().handle(request.withParams(match.params()), context);
        if (answer == null) {
            throw new IllegalStateException("the handler for " + match.pattern() + " did not return a response");
        }
        return answer;
    }

    /**
     * Start listening on the loopback address.
     *
     * @param port the port, zero to let the machine choose
     * @return the running server
     * @throws UncheckedIOException when the port cannot be opened
     */
    public Listening listen(int port) {
        return listen(port, DEFAULT_HOST);
    }

    /**
     * Start listening.
     *
     * @param port the port, zero to let the machine choose
     * @param host the address to listen on
     * @return the running server
     * @throws UncheckedIOException when the port cannot be opened
     */
    public Listening listen(int port, String host) {
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(host == null ? DEFAULT_HOST : host, port), 0);
        } catch (IOException failure) {
            throw new UncheckedIOException(
                    "cannot listen on " + host + ":" + port + ": " + failure.getMessage(), failure);
        }
        server.createContext("/", this::serve);
        server.start();
        InetSocketAddress address = server.getAddress();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("port", address.getPort());
        fields.put("host", address.getAddress().getHostAddress());
        logger.info("listening", fields);
        return new Listening() {
            @Override
            public int port() {
                return address.getPort();
            }

            @Override
            public void close() {
                server.stop(0);
            }
        };
    }

    /**
     * Answer one request from the runtime. A request that cannot even be read
     * is answered 400 without reaching the routes.
     */
    private void serve(HttpExchange exchange) throws IOException {
        Response response;
        try {
            String uri = exchange.getRequestURI().getRawPath();
            String query = exchange.getRequestURI().getRawQuery();
            Request request = requestFrom(
                    exchange.getRequestMethod(),
                    query == null ? uri : uri + "?" + query,
                    exchange.getRequestHeaders(),
                    exchange.getRequestBody());
            response = handle(request);
        } catch (RuntimeException failure) {
            logger.error("a request could not be read", failure);
            ObjectNode error = Json.object().put("code", "bad_request").put("message", "the request could not be read");
            ObjectNode body = Json.object();
            body.set("error", error);
            response = Response.json(body, 400);
        }
        Response.Rendered rendered = response.render();
        rendered.headers().forEach((name, value) -> {
            if (!name.equalsIgnoreCase("content-length")) {
                exchange.getResponseHeaders().set(name, value);
            }
        });
        byte[] bytes = rendered.text().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(rendered.status(), bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
        exchange.close();
    }

    /**
     * Build a request from what the runtime handed over. A body that says it is
     * JSON is parsed; any other body is kept as text, and an empty one is left out.
     *
     * @param method the request method
     * @param url the path and query
     * @param headers the headers, each a string or a list of strings
     * @param body the body
     * @return the request
     * @throws ValidationException when the body is too long, or says it is JSON and is not
     */
    public static Request requestFrom(String method, String url, Map<String, ?> headers, InputStream body) {
        String text = Request.readBody(body);
        Request headed = new Request(new Request.Parts(method, url, headers, null, null, null));
        String type = headed.header("content-type").orElse("");
        JsonNode parsed = type.startsWith("application/json")
                ? Request.parseJsonBody(text)
                : text.isEmpty() ? MissingNode.getInstance() : TextNode.valueOf(text);
        return Request.builder()
                .method(headed.method())
                .url(url)
                .headers(headed.headers())
                .body(parsed)
                .build();
    }
}
