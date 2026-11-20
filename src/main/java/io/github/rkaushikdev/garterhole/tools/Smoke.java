package io.github.rkaushikdev.garterhole.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.rkaushikdev.garterhole.StorageException;
import io.github.rkaushikdev.garterhole.http.App;
import io.github.rkaushikdev.garterhole.util.Json;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Start the service, ask it a few things, and stop it. Run with
 * {@code ./gradlew smoke}.
 *
 * <p>The unit tests exercise the handler without a socket, which is the right
 * trade for two thousand of them. This asks over a real port instead, because
 * the last time something broke in a way the tests could not see, it was the
 * wiring between the runtime and the handler rather than anything in a module.
 *
 * <p>It runs against a temporary data directory and leaves nothing behind.
 */
public final class Smoke {

    /** What a running service is asked, and what counts as a good answer. */
    public static final List<Check> CHECKS = List.of(
            new Check(
                    "health", "/health", body -> "up".equals(body.path("status").asText())),
            new Check("ready", "/ready", body -> "ready"
                    .equals(body.path("status").asText())),
            new Check("routes", "/routes", body -> Json.templateText(body).contains("GET /health")),
            new Check(
                    "methods",
                    "/methods",
                    body -> body.path("total").isNumber() && body.path("total").asInt() == 0));

    private Smoke() {}

    /**
     * One question for a running service.
     *
     * @param name what it is called in the output
     * @param path what is asked for
     * @param expect what counts as a good answer, given the body: the parsed
     *     document when it is JSON and the text otherwise
     */
    public record Check(String name, String path, Predicate<JsonNode> expect) {}

    /**
     * What one question got.
     *
     * @param name the question
     * @param passed true when the answer was 200 and good
     */
    public record Result(String name, boolean passed) {}

    /**
     * Ask a running service one question.
     *
     * @param port where it is listening
     * @param check the question
     * @return whether the answer was good
     */
    public static Result ask(int port, Check check) {
        HttpClient client =
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + check.path()))
                .GET()
                .build();
        try {
            HttpResponse<String> answer = client.send(request, HttpResponse.BodyHandlers.ofString());
            String type = answer.headers().firstValue("content-type").orElse("");
            JsonNode body = type.contains("json") ? Json.parse(answer.body()) : TextNode.valueOf(answer.body());
            return new Result(
                    check.name(), answer.statusCode() == 200 && check.expect().test(body));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new Result(check.name(), false);
        }
    }

    /**
     * Start a service, run every check against it, and stop it again.
     *
     * @return what every question got
     */
    public static List<Result> smoke() {
        Path directory = temporaryDirectory();
        try {
            App.Running running = App.start(App.Options.of(Map.of(
                    "GARTERHOLE_DATA", directory.toString(),
                    "GARTERHOLE_PORT", "0",
                    "GARTERHOLE_LOG_LEVEL", "silent")));
            try {
                List<Result> results = new ArrayList<>();
                for (Check check : CHECKS) {
                    results.add(ask(running.port(), check));
                }
                return results;
            } finally {
                running.close();
            }
        } finally {
            remove(directory);
        }
    }

    /**
     * Run the checks, print one line each, and exit one when any failed.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        List<Result> results = smoke();
        results.forEach(result -> System.out.println((result.passed() ? "ok  " : "FAIL") + "  " + result.name()));
        long failures = results.stream().filter(result -> !result.passed()).count();
        if (failures > 0) {
            System.err.println(failures + " smoke checks failed");
            System.exit(1);
        }
    }

    private static Path temporaryDirectory() {
        try {
            return Files.createTempDirectory("garterhole-smoke-");
        } catch (IOException failure) {
            throw new StorageException("cannot make a directory to smoke test in", failure);
        }
    }

    private static void remove(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException failure) {
            throw new StorageException("cannot remove " + directory + ": " + failure.getMessage(), failure);
        }
    }
}
