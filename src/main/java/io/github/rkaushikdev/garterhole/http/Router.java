package io.github.rkaushikdev.garterhole.http;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Guards;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Matching a request to the code that answers it.
 *
 * <p>Patterns look like {@code /towers/:id/performances}, where a segment
 * starting with a colon captures whatever is there. There are no regular
 * expressions, no optional segments and no wildcards in the middle, because the
 * routes this service has do not need them and every one of those features is a
 * way for two routes to overlap without anybody noticing.
 *
 * <p>Routes are matched in the order they were added, and a route that would
 * never be reached because an earlier one always matches first is refused
 * rather than quietly ignored.
 */
public final class Router {

    private final List<Route> routes = new ArrayList<>();

    /**
     * One route, as the service lists them about itself.
     *
     * @param method the request method, in upper case
     * @param pattern the pattern, for example {@code /towers/:id}
     */
    public record RouteInfo(String method, String pattern) {}

    /**
     * The route a request found.
     *
     * @param handler the code that answers it
     * @param params the parameters the pattern captured, decoded
     * @param pattern the pattern that matched
     */
    public record Match(RouteHandler handler, Map<String, String> params, String pattern) {}

    private record Route(String method, String pattern, List<String> segments, RouteHandler handler) {}

    /**
     * Add a route.
     *
     * @param method the request method, in any case
     * @param pattern the pattern, starting with a slash
     * @param handler the code that answers it
     * @return this router
     * @throws ValidationException when the pattern has no leading slash or could never be reached
     * @throws IllegalArgumentException when a part is missing
     */
    public Router add(String method, String pattern, RouteHandler handler) {
        Guards.nonEmptyString(method, "a request method");
        Guards.nonEmptyString(pattern, "a route pattern");
        Guards.function(handler, "a route handler");
        if (!pattern.startsWith("/")) {
            throw new ValidationException("the route " + Json.quote(pattern) + " must start with a slash");
        }
        List<String> segments = splitPath(pattern);
        String upper = method.toUpperCase(Locale.ROOT);
        for (Route existing : routes) {
            if (existing.method().equals(upper) && sameShape(existing.segments(), segments)) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("pattern", pattern);
                details.put("existing", existing.pattern());
                throw new ValidationException(
                        "the route " + upper + " " + pattern + " would never be reached, because " + existing.pattern()
                                + " matches first",
                        details);
            }
        }
        routes.add(new Route(upper, pattern, segments, handler));
        return this;
    }

    /**
     * @param pattern the pattern
     * @param handler the code that answers it
     * @return this router
     */
    public Router get(String pattern, RouteHandler handler) {
        return add("GET", pattern, handler);
    }

    /**
     * @param pattern the pattern
     * @param handler the code that answers it
     * @return this router
     */
    public Router post(String pattern, RouteHandler handler) {
        return add("POST", pattern, handler);
    }

    /**
     * @param pattern the pattern
     * @param handler the code that answers it
     * @return this router
     */
    public Router delete(String pattern, RouteHandler handler) {
        return add("DELETE", pattern, handler);
    }

    /**
     * Find the route for a request.
     *
     * @param method the request method, in any case
     * @param path the path
     * @return the route and what it captured, when one matches
     */
    public Optional<Match> match(String method, String path) {
        List<String> wanted = splitPath(path);
        String upper = String.valueOf(method).toUpperCase(Locale.ROOT);
        for (Route route : routes) {
            if (!route.method().equals(upper)) {
                continue;
            }
            Optional<Map<String, String>> params = matchSegments(route.segments(), wanted);
            if (params.isPresent()) {
                return Optional.of(new Match(route.handler(), params.get(), route.pattern()));
            }
        }
        return Optional.empty();
    }

    /**
     * The methods a path would answer to, which is what a 405 has to list.
     *
     * @param path the path
     * @return the methods, in alphabetical order
     */
    public List<String> methodsFor(String path) {
        List<String> wanted = splitPath(path);
        TreeSet<String> methods = new TreeSet<>();
        for (Route route : routes) {
            if (matchSegments(route.segments(), wanted).isPresent()) {
                methods.add(route.method());
            }
        }
        return List.copyOf(methods);
    }

    /**
     * @return every route, in the order it was added, for the listing the service publishes about itself
     */
    public List<RouteInfo> list() {
        return routes.stream()
                .map(route -> new RouteInfo(route.method(), route.pattern()))
                .toList();
    }

    private static List<String> splitPath(String path) {
        return Arrays.stream(String.valueOf(path).split("/", -1))
                .filter(segment -> !segment.isEmpty())
                .toList();
    }

    private static Optional<Map<String, String>> matchSegments(List<String> pattern, List<String> path) {
        if (pattern.size() != path.size()) {
            return Optional.empty();
        }
        Map<String, String> params = new LinkedHashMap<>();
        for (int index = 0; index < pattern.size(); index += 1) {
            String segment = pattern.get(index);
            if (segment.startsWith(":")) {
                params.put(segment.substring(1), UriComponents.decode(path.get(index)));
            } else if (!segment.equals(path.get(index))) {
                return Optional.empty();
            }
        }
        return Optional.of(params);
    }

    /**
     * Two patterns have the same shape when every segment either matches
     * literally or is a parameter in both, which means one can never be reached
     * past the other.
     */
    private static boolean sameShape(List<String> left, List<String> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index += 1) {
            String segment = left.get(index);
            String other = right.get(index);
            if (segment.startsWith(":")) {
                continue;
            }
            if (other.startsWith(":") || !segment.equals(other)) {
                return false;
            }
        }
        return true;
    }
}
