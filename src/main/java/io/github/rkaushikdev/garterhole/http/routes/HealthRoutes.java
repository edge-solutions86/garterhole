package io.github.rkaushikdev.garterhole.http.routes;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.Version;
import io.github.rkaushikdev.garterhole.http.Response;
import io.github.rkaushikdev.garterhole.http.Router;
import io.github.rkaushikdev.garterhole.util.Clock;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.stream.Collectors;

/**
 * The routes a monitor asks for.
 *
 * <p>There are two and they answer different questions. {@code /health} says
 * the process is up and answering, and is what a load balancer polls; it does
 * no work, so it cannot fail for a reason the caller can do anything about.
 * {@code /ready} says the service can actually do its job, which means the
 * store answered, and is what a deployment waits on before sending traffic.
 *
 * <p>Keeping them apart matters. A service whose store has gone is not healthy
 * in any useful sense, but restarting it will not help, and a monitor that
 * cannot tell the difference will restart it in a loop.
 */
public final class HealthRoutes {

    private HealthRoutes() {}

    /**
     * Add the routes to a router, timing the uptime on the real clock.
     *
     * @param router the router
     * @return the router
     */
    public static Router add(Router router) {
        return add(router, Clock.system());
    }

    /**
     * Add the routes to a router.
     *
     * @param router the router
     * @param clock what the uptime is measured with
     * @return the router
     */
    public static Router add(Router router, Clock clock) {
        long startedAt = clock.now();

        router.get(
                "/health",
                (request, context) -> Response.json(Json.object()
                        .put("status", "up")
                        .put("product", Version.PRODUCT)
                        .put("version", Version.VERSION)
                        .put("uptimeMs", clock.now() - startedAt)));

        router.get("/ready", (request, context) -> {
            try {
                ObjectNode ready = Json.object().put("status", "ready");
                ready.set("holding", Json.toNode(context.service().counts()));
                return Response.json(ready);
            } catch (RuntimeException failure) {
                return Response.json(Json.object().put("status", "not ready").put("reason", failure.getMessage()), 503);
            }
        });

        router.get(
                "/routes",
                (request, context) -> Response.text(context.router().list().stream()
                        .map(route -> route.method() + " " + route.pattern())
                        .collect(Collectors.joining("\n"))));

        return router;
    }
}
