package io.github.rkaushikdev.garterhole.http.middleware;

import io.github.rkaushikdev.garterhole.ConfigException;
import io.github.rkaushikdev.garterhole.ConflictException;
import io.github.rkaushikdev.garterhole.GarterholeException;
import io.github.rkaushikdev.garterhole.NotFoundException;
import io.github.rkaushikdev.garterhole.NotationException;
import io.github.rkaushikdev.garterhole.StorageException;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.http.Handler;
import io.github.rkaushikdev.garterhole.http.Response;
import io.github.rkaushikdev.garterhole.util.Logger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turning a thrown error into an answer.
 *
 * <p>The mapping is by type rather than by message, which is the whole reason
 * the error taxonomy exists. A handler that throws {@link NotFoundException}
 * gets a 404 without having to know it is being served over HTTP, and a new
 * handler that throws one of the existing types is handled correctly the day it
 * is written.
 *
 * <p>Anything not in the taxonomy is a bug rather than a rejected request, so it
 * becomes a 500 and the message is not sent back. The caller cannot do anything
 * with the details of a bug, and the details are often the sort of thing that
 * should not leave the machine. The one exception is a guard failure, an
 * {@link IllegalArgumentException}, which means the caller sent nonsense and is
 * answered 400.
 */
public final class ErrorHandler {

    private static final int INTERNAL_ERROR = 500;

    /** Which status each kind of error becomes, checked in this order. */
    private static final List<Map.Entry<Class<? extends GarterholeException>, Integer>> STATUSES = List.of(
            Map.entry(NotFoundException.class, 404),
            Map.entry(ConflictException.class, 409),
            Map.entry(NotationException.class, 400),
            Map.entry(ValidationException.class, 400),
            Map.entry(ConfigException.class, INTERNAL_ERROR),
            Map.entry(StorageException.class, INTERNAL_ERROR));

    private ErrorHandler() {}

    /**
     * @param error anything that was thrown, or anything at all
     * @return the status it should be answered with
     */
    public static int statusFor(Object error) {
        for (Map.Entry<Class<? extends GarterholeException>, Integer> entry : STATUSES) {
            if (entry.getKey().isInstance(error)) {
                return entry.getValue();
            }
        }
        if (error instanceof IllegalArgumentException) {
            return 400;
        }
        return INTERNAL_ERROR;
    }

    /**
     * Turn an error into a response, writing nothing to any log.
     *
     * @param error what was thrown
     * @return the response
     */
    public static Response responseForError(Throwable error) {
        return responseForError(error, Logger.silent(), null);
    }

    /**
     * Turn an error into a response. A bug is logged as an error with the
     * failure attached; a refused request is logged quietly, at debug.
     *
     * @param error what was thrown
     * @param logger where to say so
     * @param requestId the identifier of the request, or {@code null}
     * @return the response
     */
    public static Response responseForError(Throwable error, Logger logger, String requestId) {
        int status = statusFor(error);
        if (status >= INTERNAL_ERROR) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("requestId", requestId);
            logger.error("a request failed", error, fields);
            return Response.problem(status, "internal_error", "something went wrong inside the service");
        }
        String code = error instanceof GarterholeException ours ? ours.code() : "bad_request";
        String message = error.getMessage();
        List<Object> issues = error instanceof ValidationException refused ? refused.issues() : List.of();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("requestId", requestId);
        fields.put("code", code);
        fields.put("message", message);
        logger.debug("a request was refused", fields);
        return Response.problem(status, code, message, issues);
    }

    /**
     * Wrap a handler so that anything it throws becomes a response.
     *
     * @param handler the handler
     * @return the wrapped handler
     */
    public static Handler catchingErrors(Handler handler) {
        return catchingErrors(handler, Logger.silent());
    }

    /**
     * Wrap a handler so that anything it throws becomes a response, logged
     * against the request that caused it.
     *
     * @param handler the handler
     * @param logger where failures are written
     * @return the wrapped handler
     */
    public static Handler catchingErrors(Handler handler, Logger logger) {
        return request -> {
            try {
                return handler.handle(request);
            } catch (RuntimeException failure) {
                return responseForError(failure, logger, request.id().orElse(null));
            }
        };
    }

    /**
     * @param path the path that was asked for
     * @return the answer for a path nobody serves
     */
    public static Response notFound(String path) {
        return Response.problem(404, "not_found", "nothing is served at " + path);
    }

    /**
     * @param method the method that was used
     * @param allowed the methods the path does answer to
     * @return the answer for a path that is served, but not with this method
     */
    public static Response methodNotAllowed(String method, List<String> allowed) {
        return Response.problem(405, "method_not_allowed", method + " is not allowed here")
                .withHeader("allow", String.join(", ", allowed));
    }
}
