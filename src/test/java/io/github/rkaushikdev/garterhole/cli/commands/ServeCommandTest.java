package io.github.rkaushikdev.garterhole.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.cli.Args;
import io.github.rkaushikdev.garterhole.cli.Output;
import io.github.rkaushikdev.garterhole.cli.ParsedArgs;
import io.github.rkaushikdev.garterhole.cli.Program;
import io.github.rkaushikdev.garterhole.http.App;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.Logger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServeCommandTest {

    @TempDir
    Path directory;

    private static ParsedArgs args(String line) {
        List<String> argv =
                Arrays.stream(line.split(" ")).filter(word -> !word.isEmpty()).toList();
        return Args.parseArgs(argv, ServeCommand.SERVE_FLAGS);
    }

    @Nested
    class EnvironmentFor {

        @Test
        void laysTheFlagsOverTheEnvironment() {
            Map<String, String> environment = ServeCommand.environmentFor(
                    args("--port 9000 --host 0.0.0.0 --data /var/g"), Map.of("GARTERHOLE_LOG_LEVEL", "warn"));
            assertThat(environment)
                    .containsExactlyInAnyOrderEntriesOf(Map.of(
                            "GARTERHOLE_LOG_LEVEL", "warn",
                            "GARTERHOLE_PORT", "9000",
                            "GARTERHOLE_HOST", "0.0.0.0",
                            "GARTERHOLE_DATA", "/var/g"));
        }

        @Test
        void leavesTheEnvironmentAloneWhenNoFlagsAreGiven() {
            assertThat(ServeCommand.environmentFor(args(""), Map.of("GARTERHOLE_PORT", "1234")))
                    .isEqualTo(Map.of("GARTERHOLE_PORT", "1234"));
        }

        @Test
        void ignoresAFlagWrittenWithNoValue() {
            assertThat(ServeCommand.environmentFor(Args.parseArgs(List.of("--port")), Map.of()))
                    .isEmpty();
        }
    }

    @Nested
    class Serve {

        @Test
        void startsTheServiceAndSaysWhereItIsListening() {
            Output.Collected collected = Output.collecting();
            App.Running running = ServeCommand.serve(
                    args("--port 0 --data " + directory),
                    collected.output(),
                    Map.of("GARTERHOLE_LOG_LEVEL", "silent"),
                    ServeCommand.startingTheService());
            try {
                assertThat(running.port()).isPositive();
                assertThat(Json.parse(collected.out().get(0)).get("port").asInt())
                        .isEqualTo(running.port());
                assertThat(String.join("\n", collected.err())).contains("listening on");
            } finally {
                running.close();
            }
        }

        @Test
        void answersOverThePortItOpened() throws Exception {
            App.Running running = ServeCommand.serve(
                    args("--port 0 --data " + directory),
                    Output.collecting(false, true).output(),
                    Map.of("GARTERHOLE_LOG_LEVEL", "silent"),
                    ServeCommand.startingTheService());
            try {
                HttpClient client = HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .build();
                HttpResponse<String> answer = client.send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + running.port() + "/health"))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
                assertThat(answer.statusCode()).isEqualTo(200);
            } finally {
                running.close();
            }
        }

        @Test
        void takesAListenerOfItsOwnSoATestNeedNotOpenAPort() {
            AtomicReference<Map<String, String>> seen = new AtomicReference<>();
            App.Running running = ServeCommand.serve(
                    args("--data " + directory), Output.collecting().output(), Map.of(), environment -> {
                        seen.set(environment);
                        return new App.Running() {
                            @Override
                            public int port() {
                                return 4321;
                            }

                            @Override
                            public void close() {
                                // Nothing was opened, so there is nothing to close.
                            }
                        };
                    });
            assertThat(running.port()).isEqualTo(4321);
            assertThat(seen.get()).containsEntry("GARTERHOLE_DATA", directory.toString());
        }

        @Test
        void refusesAPortThatIsNotOneBeforeOpeningAnything() {
            assertThatThrownBy(() -> ServeCommand.serve(
                            args("--port 70000 --data " + directory),
                            Output.collecting().output(),
                            Map.of(),
                            environment -> {
                                throw new AssertionError("nothing should have been opened");
                            }))
                    .hasMessageContaining("must be a port number");
        }

        @Test
        void refusesToServeWithNowhereToWriteTo() {
            assertThatThrownBy(() -> ServeCommand.serve(args(""), null, Map.of(), ServeCommand.startingTheService()))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class ServeAsACommand {

        @Test
        void isListedInTheHelp() {
            try (App app = App.build(App.Options.of(Map.of("GARTERHOLE_DATA", directory.toString()))
                    .withLogger(Logger.silent()))) {
                Program.Collected result = Program.runCollecting(
                        List.of("--help"), Program.Options.of(Map.of()).withApp(app));
                assertThat(String.join("\n", result.out())).contains("serve");
            }
        }
    }
}
