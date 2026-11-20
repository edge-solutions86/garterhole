package io.github.rkaushikdev.garterhole.http;

import io.github.rkaushikdev.garterhole.Service;
import io.github.rkaushikdev.garterhole.config.Config;
import java.util.Optional;

/**
 * What every route handler is given besides the request: the service that does
 * the work, the router that knows every route, and the settings the service
 * started with.
 *
 * <p>It is an interface rather than a fixed record so that a handler depends on
 * what it reads rather than on how the application put it together.
 */
public interface Context {

    /**
     * @return the service the routes talk to
     */
    Service service();

    /**
     * @return the router holding every route
     */
    Router router();

    /**
     * @return the settings, when the context was built with them
     */
    Optional<Config> config();

    /**
     * @param service the service the routes talk to
     * @param router the router holding every route
     * @return a context with no settings
     */
    static Context of(Service service, Router router) {
        return new Fixed(service, router, null);
    }

    /**
     * @param service the service the routes talk to
     * @param router the router holding every route
     * @param config the settings
     * @return the context
     */
    static Context of(Service service, Router router, Config config) {
        return new Fixed(service, router, config);
    }

    /** The context the application builds, holding what it was given. */
    record Fixed(Service service, Router router, Config settings) implements Context {
        @Override
        public Optional<Config> config() {
            return Optional.ofNullable(settings);
        }
    }
}
