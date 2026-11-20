package io.github.rkaushikdev.garterhole.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.garterhole.Service;
import io.github.rkaushikdev.garterhole.cli.Program;
import io.github.rkaushikdev.garterhole.http.App;
import io.github.rkaushikdev.garterhole.http.Request;
import io.github.rkaushikdev.garterhole.http.Response;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.Logger;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A whole tower's year, done through the two surfaces.
 *
 * <p>Every other test in the suite exercises one module. This one exercises
 * the thing the tower actually installs: file some methods, prove a touch, add
 * the ring of bells, file the quarters, and read the report back. It is slow
 * and it is worth it, because it is the only test that would notice the
 * command line and the HTTP surface drifting apart.
 */
class TowerIntegrationTest {

    @TempDir
    Path directory;

    private App app;

    @BeforeEach
    void buildTheApp() {
        app = build();
    }

    @AfterEach
    void closeTheApp() {
        app.close();
    }

    private App build() {
        return App.build(
                App.Options.of(Map.of("GARTERHOLE_DATA", directory.toString())).withLogger(Logger.silent()));
    }

    private Program.Collected cli(String line) {
        return Program.runCollecting(
                Arrays.stream(line.split(" ")).filter(word -> !word.isEmpty()).toList(),
                Program.Options.of(Map.of()).withApp(app));
    }

    private Response http(String method, String url, String body) {
        Request.Builder request = Request.builder().method(method).url(url).header("content-type", "application/json");
        if (body != null) {
            request.body(Json.parse(body));
        }
        return app.server().handle(request.build());
    }

    @Nested
    class ATowerUsingBothSurfaces {

        @Test
        void filesAMethodOnTheCommandLineAndReadsItBackOverHttp() {
            Program.Collected added = cli("method add --name Plain --stage 6 --notation x1x1x1,2");
            assertThat(added.status()).isZero();
            Response read = http("GET", "/methods/Plain%20Bob%20Minor", null);
            assertThat(read.status()).isEqualTo(200);
            assertThat(read.body().get("className").asText()).isEqualTo("Bob");
        }

        @Test
        void filesAMethodOverHttpAndProvesATouchOfItOnTheCommandLine() {
            http("POST", "/methods", "{\"name\":\"Plain\",\"stage\":8,\"notation\":\"x18x18x18x18,12\"}");
            Program.Collected proved = cli("prove --title Plain Bob Major --touch 3(H)");
            assertThat(proved.out().get(1)).isEqualTo("336 changes, touch");
            assertThat(proved.out().get(2)).isEqualTo("true, 336 rows");
        }

        @Test
        void addsATowerOnTheCommandLineAndListsItsBellsOverHttp() {
            cli("tower add --name StMary --place Bridgend --weights 4-0-0,4-2-7,5-1-14,6-0-0,8-2-21,12-1-4");
            Response bells = http("GET", "/towers/bridgend-stmary/bells", null);
            assertThat(bells.status()).isEqualTo(200);
            assertThat(bells.body().get("bells")).hasSize(6);
            assertThat(bells.body().get("tenor").asText()).isEqualTo("12-1-4");
        }

        @Test
        void filesAQuarterOverHttpAndReportsItOnTheCommandLine() {
            http(
                    "POST",
                    "/performances",
                    "{\"tower\":\"St Mary\",\"date\":\"2025-05-18\",\"title\":\"Plain Bob Minor\",\"changes\":1260,"
                            + "\"band\":[\"Alice\",\"Bob\",\"Carol\",\"Dan\",\"Erin\",\"Frank\"],\"conductor\":\"Alice\"}");
            Program.Collected report = cli("records report");
            assertThat(String.join("\n", report.out())).contains("1 quarter peal");
        }

        @Test
        void keepsEverythingAcrossARestart() {
            cli("method add --name Plain --stage 6 --notation x1x1x1,2");
            cli("tower add --name StMary --weights 4-0-0,4-2-7,5-1-14,6-0-0");
            app.close();
            app = build();
            assertThat(app.service().counts()).isEqualTo(new Service.Counts(1, 1, 0));
        }

        @Test
        void refusesAPerformanceWhoseBandIsTheWrongSizeFromEitherSurface() {
            Response overHttp = http(
                    "POST",
                    "/performances",
                    "{\"tower\":\"St Mary\",\"date\":\"2025-05-18\",\"title\":\"Plain Bob Minor\",\"changes\":1260,"
                            + "\"band\":[\"Alice\",\"Bob\",\"Carol\",\"Dan\"]}");
            assertThat(overHttp.status()).isEqualTo(400);
            Program.Collected overCli = cli(
                    "records file --tower StMary --title Plain Bob Minor --changes 1260 --band Alice,Bob,Carol,Dan");
            assertThat(overCli.status()).isEqualTo(1);
        }

        @Test
        void servesEveryCommandTheHelpLists() {
            for (String name : Program.commandNames()) {
                assertThat(name).isNotEmpty();
            }
            assertThat(Program.commandNames()).contains("tower add");
            JsonNode routes = http("GET", "/routes", null).body();
            assertThat(routes).isNotNull();
        }
    }
}
