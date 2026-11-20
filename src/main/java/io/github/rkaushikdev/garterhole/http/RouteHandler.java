package io.github.rkaushikdev.garterhole.http;

/**
 * The code behind one route. It is handed the request, with the parameters the
 * route captured already filled in, and the context the server was built with.
 */
@FunctionalInterface
public interface RouteHandler {

    /**
     * @param request what arrived, with the route's parameters
     * @param context the service and the router
     * @return what goes back
     */
    Response handle(Request request, Context context);
}
