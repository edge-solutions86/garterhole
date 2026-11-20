package io.github.rkaushikdev.garterhole.http;

import static io.github.rkaushikdev.garterhole.http.Documents.object;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.util.Logger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppTest {

    private static final String PLAIN_BOB_MAJOR = "{'name':'Plain','stage':8,'notation':'x18x18x18x18,12'}";

    @TempDir
    Path directory;

    private App.Options options() {
        return App.Options.of(Map.of("GARTERHOLE_DATA", directory.toString(), "GARTERHOLE_LOG_LEVEL", "silent"))
                .withLogger(Logger.silent());
    }

    private App build() {
        return App.build(options());
    }

    @Nested
    class BuildingTheApplication {

        @Test
        void opensTheStoreWhereTheSettingsSay() {
            try (App app = build()) {
                assertThat(app.config().journalPath()).contains(directory.toString());
                assertThat(app.repository()).isNotNull();
            }
        }

        @Test
        void registersEveryGroupOfRoutes() {
            try (App app = build()) {
                assertThat(app.router().list())
                        .extracting(Router.RouteInfo::pattern)
                        .contains("/health", "/methods", "/towers", "/performances", "/striking");
            }
        }

        @Test
        void namesTheGroupsItRegisters() {
            assertThat(App.routeGroups()).containsExactly("health", "methods", "towers", "performances", "striking");
        }

        @Test
        void takesTheLockWhileItIsRunning() {
            try (App app = build()) {
                assertThat(Files.exists(Path.of(app.config().lockPath()))).isTrue();
            }
        }

        @Test
        void refusesToStartTwiceOnOneDataDirectory() {
            try (App first = build()) {
                assertThat(first).isNotNull();
                assertThatThrownBy(AppTest.this::build).hasMessageContaining("is held by process");
            }
        }

        @Test
        void givesTheLockUpWhenItCloses() {
            App app = build();
            app.close();
            assertThat(Files.exists(Path.of(app.config().lockPath()))).isFalse();
        }

        @Test
        void canBeBuiltWithoutTakingTheLock() {
            try (App app = App.build(options().withoutLock())) {
                assertThat(Files.exists(Path.of(app.config().lockPath()))).isFalse();
            }
        }
    }

    @Nested
    class TheApplicationAnswering {

        @Test
        void answersHealth() {
            try (App app = build()) {
                assertThat(app.server().handle(Request.get("/health")).status()).isEqualTo(200);
            }
        }

        @Test
        void filesAMethodAndReadsItBack() {
            try (App app = build()) {
                Response filed = app.server().handle(Documents.ask("POST", "/methods", object(PLAIN_BOB_MAJOR)));
                assertThat(filed.status()).isEqualTo(201);
                Response read = app.server().handle(Request.get("/methods"));
                assertThat(read.body().path("total").asInt()).isEqualTo(1);
            }
        }

        @Test
        void keepsWhatWasFiledAcrossARestart() {
            App first = build();
            first.server().handle(Documents.ask("POST", "/methods", object(PLAIN_BOB_MAJOR)));
            first.close();
            try (App second = build()) {
                assertThat(second.service().counts().methods()).isEqualTo(1);
            }
        }
    }

    @Nested
    class Start {

        @Test
        void listensAndStops() throws Exception {
            App.Options listening = App.Options.of(
                            Map.of("GARTERHOLE_DATA", directory.toString(), "GARTERHOLE_PORT", "0"))
                    .withLogger(Logger.silent());
            try (App.Running running = App.start(listening)) {
                HttpClient client = HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .build();
                HttpResponse<String> answer = client.send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + running.port() + "/health"))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
                assertThat(answer.statusCode()).isEqualTo(200);
            }
            assertThat(Files.exists(directory.resolve("lock"))).isFalse();
        }
    }
}
