package io.github.rkaushikdev.garterhole.http.middleware;

import io.github.rkaushikdev.garterhole.http.Handler;
import io.github.rkaushikdev.garterhole.http.Request;
import io.github.rkaushikdev.garterhole.http.Response;
import io.github.rkaushikdev.garterhole.util.Clock;
import io.github.rkaushikdev.garterhole.util.IdSource;
import io.github.rkaushikdev.garterhole.util.Logger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Giving every request an identifier and writing a line when it finishes.
 *
 * <p>The identifier goes on the request, into every log line the handler
 * writes, and back to the caller in a header. That last part is what makes a
 * support conversation short: the caller quotes the identifier and the log line
 * is found at once, without anybody guessing at timestamps.
 *
 * <p>A caller may supply its own identifier, which is what lets one request
 * through several services be followed end to end. It is accepted only when it
 * looks like an identifier, so that a header from somewhere untrusted cannot
 * fill the log with whatever it likes.
 */
public final class RequestLog {

    /** The header a request identifier travels in. */
    public static final String REQUEST_ID_HEADER = "x-request-id";

    /** The most of a supplied identifier that is kept. */
    private static final int MAX_SUPPLIED_LENGTH = 64;

    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9_-]+$");

    private RequestLog() {}

    /**
     * Wrap a handler so that every request is identified and timed, logging nowhere.
     *
     * @param handler the handler
     * @param ids where fresh identifiers come from
     * @return the wrapped handler
     */
    public static Handler withRequestLog(Handler handler, IdSource ids) {
        return withRequestLog(handler, Logger.silent(), ids, Clock.system());
    }

    /**
     * Wrap a handler so that every request is identified, timed and logged.
     *
     * @param handler the handler
     * @param logger where the line for each request goes
     * @param ids where fresh identifiers come from
     * @param clock what the request is timed with
     * @return the wrapped handler
     */
    public static Handler withRequestLog(Handler handler, Logger logger, IdSource ids, Clock clock) {
        return request -> {
            String id = identifierFor(request, ids);
            Request identified = request.withId(id);
            long started = clock.now();
            Response response = handler.handle(identified);
            long elapsed = clock.now() - started;
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("requestId", id);
            fields.put("method", identified.method());
            fields.put("path", identified.path());
            fields.put("status", response.status());
            fields.put("ms", elapsed);
            logger.info("request", fields);
            return response.withHeader(REQUEST_ID_HEADER, id);
        };
    }

    /**
     * The identifier a request should carry: the one it brought when that is
     * usable, and a fresh one otherwise.
     *
     * @param request the request
     * @param ids where fresh identifiers come from
     * @return the identifier
     */
    public static String identifierFor(Request request, IdSource ids) {
        return request.header(REQUEST_ID_HEADER).filter(RequestLog::isUsable).orElseGet(() -> ids.next("req"));
    }

    /**
     * True when a supplied identifier is safe to put in a log line: short
     * enough, and made only of the characters an identifier is made of.
     *
     * @param supplied what the caller sent
     * @return true when it can be kept
     */
    public static boolean isUsable(String supplied) {
        if (supplied.isEmpty() || supplied.length() > MAX_SUPPLIED_LENGTH) {
            return false;
        }
        return SAFE.matcher(supplied).matches();
    }
}
