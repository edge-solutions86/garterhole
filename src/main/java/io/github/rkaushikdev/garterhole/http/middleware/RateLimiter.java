package io.github.rkaushikdev.garterhole.http.middleware;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.http.Handler;
import io.github.rkaushikdev.garterhole.http.Request;
import io.github.rkaushikdev.garterhole.http.Response;
import io.github.rkaushikdev.garterhole.util.Clock;
import io.github.rkaushikdev.garterhole.util.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeping one caller from taking the whole service.
 *
 * <p>The window slides: each caller has a list of the moments they made a
 * request, anything older than the window is dropped, and the request is
 * refused when what is left is already at the limit. That costs a little more
 * memory than counting into fixed buckets, and it does not have the flaw that
 * fixed buckets have, where twice the limit gets through by straddling a
 * boundary.
 *
 * <p>The clock is handed in, so the tests can move time rather than waiting.
 */
public final class RateLimiter {

    /** How many requests a caller may make in a window when nobody says. */
    public static final int DEFAULT_LIMIT = 120;

    private final int limit;
    private final long window;
    private final Clock clock;
    private final Map<String, List<Long>> seen = new HashMap<>();

    /** A limiter allowing the default number of requests a minute, on the real clock. */
    public RateLimiter() {
        this(DEFAULT_LIMIT, Clock.MINUTE, Clock.system());
    }

    /**
     * @param limit how many requests a caller may make in a minute
     * @param clock the clock
     */
    public RateLimiter(int limit, Clock clock) {
        this(limit, Clock.MINUTE, clock);
    }

    /**
     * @param limit how many requests a caller may make in a window
     * @param window the window, in milliseconds
     * @param clock the clock
     * @throws ValidationException when the limit or the window would let nobody in
     */
    public RateLimiter(int limit, long window, Clock clock) {
        if (limit < 1) {
            throw new ValidationException("a limit of " + limit + " requests would let nobody in");
        }
        if (window <= 0) {
            throw new ValidationException("a window of " + window + " is not a length of time");
        }
        this.limit = limit;
        this.window = window;
        this.clock = clock == null ? Clock.system() : clock;
    }

    /**
     * What the limiter made of a request.
     *
     * @param allowed true when the request may go ahead
     * @param remaining how many more the caller may make in the window
     * @param retryAfter how many seconds to wait before trying again, when refused
     */
    public record Verdict(boolean allowed, int remaining, long retryAfter) {}

    /**
     * @return how many requests a caller may make in a window
     */
    public int limit() {
        return limit;
    }

    /**
     * @return the window, in milliseconds
     */
    public long window() {
        return window;
    }

    /**
     * Offer a request from a caller.
     *
     * @param caller who the request is from
     * @return whether it may go ahead
     */
    public synchronized Verdict offer(String caller) {
        long now = clock.now();
        List<Long> moments = new ArrayList<>();
        for (long at : seen.getOrDefault(caller, List.of())) {
            if (at > now - window) {
                moments.add(at);
            }
        }
        if (moments.size() >= limit) {
            seen.put(caller, moments);
            long oldest = moments.get(0);
            long retryAfter = Math.max(1, (long) Math.ceil((oldest + window - now) / 1000.0));
            return new Verdict(false, 0, retryAfter);
        }
        moments.add(now);
        seen.put(caller, moments);
        return new Verdict(true, limit - moments.size(), 0);
    }

    /**
     * Forget callers who have not been seen for a whole window, so that the map
     * does not grow without limit on a service that is left running.
     *
     * @return how many were forgotten
     */
    public synchronized int sweep() {
        long now = clock.now();
        int before = seen.size();
        seen.values().removeIf(moments -> moments.stream().allMatch(at -> at <= now - window));
        return before - seen.size();
    }

    /**
     * @return how many callers are being tracked
     */
    public synchronized int size() {
        return seen.size();
    }

    /**
     * Who a request is from, as far as the limiter is concerned: the first
     * forwarded address, then a caller header, then nobody in particular.
     *
     * @param request the request
     * @return the caller
     */
    public static String callerOf(Request request) {
        String forwarded = request.header("x-forwarded-for").orElse("");
        if (!forwarded.isEmpty()) {
            return Text.trim(forwarded.split(",", -1)[0]);
        }
        return request.header("x-caller").orElse("anonymous");
    }

    /**
     * Wrap a handler so that a caller making too many requests is refused,
     * allowing a number of requests a minute on the real clock.
     *
     * @param handler the handler
     * @param limit how many requests a caller may make in a minute
     * @param clock the clock
     * @return the wrapped handler
     */
    public static Handler withRateLimit(Handler handler, int limit, Clock clock) {
        return withRateLimit(handler, new RateLimiter(limit, clock));
    }

    /**
     * Wrap a handler so that a caller making too many requests is refused. A
     * limiter can be shared by several handlers.
     *
     * @param handler the handler
     * @param limiter the limiter
     * @return the wrapped handler
     */
    public static Handler withRateLimit(Handler handler, RateLimiter limiter) {
        return request -> {
            Verdict verdict = limiter.offer(callerOf(request));
            if (!verdict.allowed()) {
                return Response.problem(429, "too_many_requests", "that is more requests than this service allows")
                        .withHeader("retry-after", Long.toString(verdict.retryAfter()))
                        .withHeader("x-rate-limit-remaining", "0");
            }
            return handler.handle(request).withHeader("x-rate-limit-remaining", Integer.toString(verdict.remaining()));
        };
    }
}
