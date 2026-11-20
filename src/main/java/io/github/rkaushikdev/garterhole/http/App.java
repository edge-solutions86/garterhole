package io.github.rkaushikdev.garterhole.http;

import io.github.rkaushikdev.garterhole.GarterholeException;
import io.github.rkaushikdev.garterhole.Service;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.config.Config;
import io.github.rkaushikdev.garterhole.http.routes.HealthRoutes;
import io.github.rkaushikdev.garterhole.http.routes.MethodRoutes;
import io.github.rkaushikdev.garterhole.http.routes.PerformanceRoutes;
import io.github.rkaushikdev.garterhole.http.routes.StrikingRoutes;
import io.github.rkaushikdev.garterhole.http.routes.TowerRoutes;
import io.github.rkaushikdev.garterhole.store.Journal;
import io.github.rkaushikdev.garterhole.store.Lock;
import io.github.rkaushikdev.garterhole.store.Repository;
import io.github.rkaushikdev.garterhole.store.Snapshot;
import io.github.rkaushikdev.garterhole.util.Logger;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Putting the whole thing together.
 *
 * <p>This is the only place that knows about every layer at once, and it is
 * deliberately dull: take the lock, open the store, build the service, register
 * the routes, hand the router to a server. Anything clever that lives here is
 * something that should have lived in a layer.
 *
 * <p>The routes are registered in a stated order, and the health routes go
 * first so that a monitor is answered even if a later group of routes fails to
 * register for some reason.
 */
public final class App implements AutoCloseable {

    /** The groups of routes, in the order they are registered. */
    private static final List<Map.Entry<String, UnaryOperator<Router>>> ROUTE_GROUPS = List.of(
            Map.entry("health", HealthRoutes::add),
            Map.entry("methods", MethodRoutes::add),
            Map.entry("towers", TowerRoutes::add),
            Map.entry("performances", PerformanceRoutes::add),
            Map.entry("striking", StrikingRoutes::add));

    private final Server server;
    private final Service service;
    private final Repository repository;
    private final Router router;
    private final Config config;
    private final Logger logger;
    private final Lock lock;

    /**
     * How the application is built.
     *
     * @param environment the variables the settings are read from, when there are no settings
     * @param config the settings, or {@code null} to read them from the environment
     * @param logger where the log goes, or {@code null} for standard output at the configured level
     * @param lock whether to take the lock on the data directory
     */
    public record Options(Map<String, String> environment, Config config, Logger logger, boolean lock) {

        /**
         * @param environment the variables the settings are read from
         * @return options reading the settings from those variables, logging to
         *     standard output and taking the lock
         */
        public static Options of(Map<String, String> environment) {
            return new Options(environment, null, null, true);
        }

        /**
         * @param settings the settings
         * @return these options with the settings given rather than read
         */
        public Options withConfig(Config settings) {
            return new Options(environment, settings, logger, lock);
        }

        /**
         * @param given where the log goes
         * @return these options with that logger
         */
        public Options withLogger(Logger given) {
            return new Options(environment, config, given, lock);
        }

        /**
         * @return these options, leaving the lock alone
         */
        public Options withoutLock() {
            return new Options(environment, config, logger, false);
        }
    }

    /**
     * A running application, listening on a port.
     */
    public interface Running extends AutoCloseable {

        /**
         * @return the port it is listening on
         */
        int port();

        /** Stop listening, save the store and give the lock up. */
        @Override
        void close();
    }

    private App(
            Server server,
            Service service,
            Repository repository,
            Router router,
            Config config,
            Logger logger,
            Lock lock) {
        this.server = server;
        this.service = service;
        this.repository = repository;
        this.router = router;
        this.config = config;
        this.logger = logger;
        this.lock = lock;
    }

    /**
     * Build the application.
     *
     * @param options where the settings come from, the logger and whether to take the lock
     * @return the application, holding the lock unless asked not to
     * @throws io.github.rkaushikdev.garterhole.ConflictException when another process holds the lock
     * @throws ValidationException when a group of routes cannot be registered
     */
    public static App build(Options options) {
        Config config = options.config() != null
                ? options.config()
                : Config.load(options.environment() == null ? Map.of() : options.environment());
        Logger logger =
                options.logger() != null ? options.logger() : new Logger(config.logLevel(), System.out::println);
        Lock lock = options.lock() ? new Lock(config.lockPath()) : null;
        if (lock != null) {
            lock.acquire();
        }
        try {
            Repository repository =
                    new Repository(new Journal(config.journalPath()), new Snapshot(config.snapshotPath()));
            Service service = new Service(repository, logger);
            Router router = new Router();
            for (Map.Entry<String, UnaryOperator<Router>> group : ROUTE_GROUPS) {
                try {
                    group.getValue().apply(router);
                } catch (GarterholeException | IllegalArgumentException failure) {
                    throw new ValidationException(
                            "the " + group.getKey() + " routes could not be registered: " + failure.getMessage());
                }
            }
            Server server = new Server(Server.Options.of(router)
                    .withLogger(logger)
                    .withRateLimit(config.requestsPerMinute())
                    .withContext(Context.of(service, router, config)));
            logger.info("service built", Map.of("settings", config.describe()));
            return new App(server, service, repository, router, config, logger, lock);
        } catch (RuntimeException failure) {
            if (lock != null) {
                lock.release();
            }
            throw failure;
        }
    }

    /**
     * Build the application and start listening on the port and the address the
     * settings name.
     *
     * @param options as for {@link #build}
     * @return the running application
     */
    public static Running start(Options options) {
        App app = build(options);
        Server.Listening listening;
        try {
            listening = app.server.listen(app.config.port(), app.config.host());
        } catch (RuntimeException failure) {
            app.close();
            throw failure;
        }
        return new Running() {
            @Override
            public int port() {
                return listening.port();
            }

            @Override
            public void close() {
                listening.close();
                app.close();
            }
        };
    }

    /**
     * @return the names of the route groups, in the order they are registered,
     *     for the documentation and for a test that checks nothing has been left out
     */
    public static List<String> routeGroups() {
        return ROUTE_GROUPS.stream().map(Map.Entry::getKey).toList();
    }

    /**
     * @return a router holding every route the application serves, for the
     *     documentation, built without a store
     */
    public static Router everyRoute() {
        Router router = new Router();
        ROUTE_GROUPS.forEach(group -> group.getValue().apply(router));
        return router;
    }

    /**
     * @return the server, which answers requests without a socket
     */
    public Server server() {
        return server;
    }

    /**
     * @return the service the routes talk to
     */
    public Service service() {
        return service;
    }

    /**
     * @return the store
     */
    public Repository repository() {
        return repository;
    }

    /**
     * @return the router holding every route
     */
    public Router router() {
        return router;
    }

    /**
     * @return the settings the application was built with
     */
    public Config config() {
        return config;
    }

    /**
     * @return where the application logs
     */
    public Logger logger() {
        return logger;
    }

    /** Save the store and give the lock up. */
    @Override
    public void close() {
        repository.save();
        if (lock != null) {
            lock.release();
        }
    }
}
