package io.github.rkaushikdev.garterhole.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.util.Json;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

/**
 * The parts of serving that only happen with a socket in the way: a request
 * that cannot be read, a port somebody else holds, and the list of routes the
 * surface document is written from. The original had no tests of these, since
 * its smoke script covered the socket side.
 */
class ServingEdgesTest {

    private static Server server() {
        return new Server(
                Server.Options.of(new Router().post("/echo", (request, context) -> Response.json(request.body()))));
    }

    @Test
    void answers400WhenTheRequestCannotBeRead() throws Exception {
        try (Server.Listening listening = server().listen(0)) {
            HttpClient client =
                    HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            String tooLong = "\"" + "x".repeat(Request.MAX_BODY_BYTES) + "\"";
            HttpResponse<String> answer = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + listening.port() + "/echo"))
                            .header("content-type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(tooLong))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(answer.statusCode()).isEqualTo(400);
            assertThat(Json.parse(answer.body()).path("error").path("message").asText())
                    .isEqualTo("the request could not be read");
        }
    }

    @Test
    void refusesAPortSomebodyElseHolds() {
        try (Server.Listening first = server().listen(0)) {
            assertThatThrownBy(() -> server().listen(first.port()))
                    .isInstanceOf(UncheckedIOException.class)
                    .hasMessageContaining("cannot listen on");
        }
    }

    @Test
    void listsEveryRouteTheApplicationServesInTheOrderTheyAreRegistered() {
        assertThat(App.everyRoute().list())
                .extracting(route -> route.method() + " " + route.pattern())
                .containsExactly(
                        "GET /health",
                        "GET /ready",
                        "GET /routes",
                        "GET /methods",
                        "POST /methods",
                        "GET /methods/:title",
                        "DELETE /methods/:title",
                        "POST /methods/:title/analyse",
                        "GET /towers",
                        "POST /towers",
                        "GET /towers/:id",
                        "GET /towers/:id/bells",
                        "DELETE /towers/:id",
                        "GET /performances",
                        "POST /performances",
                        "GET /performances/:id",
                        "POST /performances/check",
                        "GET /report",
                        "GET /statistics",
                        "POST /striking",
                        "POST /striking/report",
                        "POST /striking/blows.csv",
                        "POST /striking/bells.csv");
    }
}
