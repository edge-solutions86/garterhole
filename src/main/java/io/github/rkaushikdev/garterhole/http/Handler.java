package io.github.rkaushikdev.garterhole.http;

/**
 * Something that answers a request, with everything it needs already bound in.
 *
 * <p>The server is one of these, and so is every layer of middleware: each
 * wraps the handler inside it and answers in its place, which is how the rate
 * limit runs before the routes and the request log wraps everything.
 */
@FunctionalInterface
public interface Handler {

    /**
     * @param request what arrived
     * @return what goes back
     */
    Response handle(Request request);
}
