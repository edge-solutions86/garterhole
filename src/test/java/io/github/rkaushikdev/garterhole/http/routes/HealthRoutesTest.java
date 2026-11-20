package io.github.rkaushikdev.garterhole.http.routes;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.garterhole.Service;
import io.github.rkaushikdev.garterhole.Version;
import io.github.rkaushikdev.garterhole.config.Config;
import io.github.rkaushikdev.garterhole.http.Context;
import io.github.rkaushikdev.garterhole.http.Request;
import io.github.rkaushikdev.garterhole.http.Response;
import io.github.rkaushikdev.garterhole.http.Router;
import io.github.rkaushikdev.garterhole.http.Server;
import io.github.rkaushikdev.garterhole.method.Method;
import io.github.rkaushikdev.garterhole.store.Journal;
import io.github.rkaushikdev.garterhole.store.Repository;
import io.github.rkaushikdev.garterhole.util.FixedClock;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HealthRoutesTest {

    @TempDir
    Path directory;

    private Service service;
    private Server server;
    private FixedClock clock;

    @BeforeEach
    void build() {
        service = new Service(new Repository(new Journal(directory.resolve("journal.jsonl"))));
        clock = new FixedClock(1000);
        Router router = HealthRoutes.add(new Router(), clock);
        server = new Server(Server.Options.of(router).withContext(Context.of(service, router)));
    }

    private Response ask(String path) {
        return server.handle(Request.get(path));
    }

    @Nested
    class Health {

        @Test
        void saysTheServiceIsUp() {
            Response response = ask("/health");
            assertThat(response.status()).isEqualTo(200);
            assertThat(response.body().path("status").asText()).isEqualTo("up");
        }

        @Test
        void namesTheProductAndTheVersion() {
            Response response = ask("/health");
            assertThat(response.body().path("product").asText()).isEqualTo("garterhole");
            assertThat(response.body().path("version").asText()).isEqualTo(Version.VERSION);
        }

        @Test
        void reportsHowLongItHasBeenUp() {
            clock.advance(5000);
            assertThat(ask("/health").body().path("uptimeMs").asLong()).isEqualTo(5000);
        }

        @Test
        void doesNoWorkSoItAnswersWithNothingInTheStore() {
            assertThat(ask("/health").status()).isEqualTo(200);
        }
    }

    @Nested
    class Ready {

        @Test
        void saysTheServiceCanDoItsJob() {
            Response response = ask("/ready");
            assertThat(response.status()).isEqualTo(200);
            assertThat(response.body().path("status").asText()).isEqualTo("ready");
        }

        @Test
        void saysWhatTheStoreIsHolding() {
            service.addMethod(new Method.Definition("Plain", 8, "x18x18x18x18,12"));
            assertThat(ask("/ready").body().path("holding").path("methods").asInt())
                    .isEqualTo(1);
        }

        @Test
        void saysItIsNotReadyWhenTheStoreCannotAnswer() {
            Router router = HealthRoutes.add(new Router(), clock);
            Context broken = new Context() {
                @Override
                public Service service() {
                    throw new IllegalStateException("the disk has gone");
                }

                @Override
                public Router router() {
                    return router;
                }

                @Override
                public Optional<Config> config() {
                    return Optional.empty();
                }
            };
            Response response = new Server(Server.Options.of(router).withContext(broken)).handle(Request.get("/ready"));
            assertThat(response.status()).isEqualTo(503);
            assertThat(response.body().path("reason").asText()).isEqualTo("the disk has gone");
        }
    }

    @Nested
    class Routes {

        @Test
        void listsWhatTheServiceServes() {
            String listed = ask("/routes").body().asText();
            assertThat(listed).contains("GET /health");
            assertThat(listed).contains("GET /ready");
        }

        @Test
        void answersAsPlainText() {
            assertThat(ask("/routes").headers().get("content-type")).contains("text/plain");
        }
    }
}
