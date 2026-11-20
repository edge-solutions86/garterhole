package io.github.rkaushikdev.garterhole.http;

import static io.github.rkaushikdev.garterhole.http.Documents.doc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.garterhole.NotFoundException;
import io.github.rkaushikdev.garterhole.Service;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.config.Config;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.Logger;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ServerTest {

    /** A context that carries nothing but a name, as a handler might be given. */
    private record Named(String name) implements Context {
        @Override
        public Service service() {
            throw new IllegalStateException("this context has no service");
        }

        @Override
        public Router router() {
            throw new IllegalStateException("this context has no router");
        }

        @Override
        public Optional<Config> config() {
            return Optional.empty();
        }
    }

    private static Router router() {
        return new Router()
                .get("/health", (request, context) -> Response.json(doc("{'ok':true}")))
                .get(
                        "/towers/:id",
                        (request, context) -> Response.json(Json.object().put("id", request.param("id"))))
                .get(
                        "/context",
                        (request, context) -> Response.json(Json.object().put("name", ((Named) context).name())))
                .get("/broken", (request, context) -> {
                    throw new NotFoundException("tower", "x");
                })
                .get("/bug", (request, context) -> {
                    throw new IllegalStateException("the password is hunter2");
                })
                .get("/wrong", (request, context) -> null)
                .delete("/towers/:id", (request, context) -> Response.json(doc("{'removed':true}")));
    }

    private static Server server() {
        return new Server(Server.Options.of(router()));
    }

    private static InputStream body(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static HttpResponse<String> fetch(int port, String path) throws IOException, InterruptedException {
        HttpClient client =
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Nested
    class RoutingARequest {

        @Test
        void answersARouteItHas() {
            Response response = server().handle(Request.get("/health"));
            assertThat(response.status()).isEqualTo(200);
            assertThat(response.body()).isEqualTo(doc("{'ok':true}"));
        }

        @Test
        void passesTheCapturedParametersThrough() {
            Response response = server().handle(Request.get("/towers/st-mary"));
            assertThat(response.body()).isEqualTo(doc("{'id':'st-mary'}"));
        }

        @Test
        void passesTheContextToTheHandler() {
            Response response = new Server(Server.Options.of(router()).withContext(new Named("St Mary")))
                    .handle(Request.get("/context"));
            assertThat(response.body()).isEqualTo(doc("{'name':'St Mary'}"));
        }

        @Test
        void answers404ForAPathNobodyServes() {
            Response response = server().handle(Request.get("/nowhere"));
            assertThat(response.status()).isEqualTo(404);
            assertThat(response.body().path("error").path("message").asText())
                    .isEqualTo("nothing is served at /nowhere");
        }

        @Test
        void answers405WithTheMethodsThatWouldWork() {
            Response response = server().handle(Request.builder()
                    .method("POST")
                    .url("/towers/st-mary")
                    .build());
            assertThat(response.status()).isEqualTo(405);
            assertThat(response.headers().get("allow")).isEqualTo("DELETE, GET");
        }

        @Test
        void turnsAnErrorFromAHandlerIntoAnAnswer() {
            Response response = server().handle(Request.get("/broken"));
            assertThat(response.status()).isEqualTo(404);
            assertThat(response.body().path("error").path("code").asText()).isEqualTo("not_found");
        }

        @Test
        void hidesTheDetailsOfABug() {
            Response response = server().handle(Request.get("/bug"));
            assertThat(response.status()).isEqualTo(500);
            assertThat(Json.stringify(response.body())).doesNotContain("hunter2");
        }

        @Test
        void refusesAHandlerThatDoesNotReturnAResponse() {
            assertThat(server().handle(Request.get("/wrong")).status()).isEqualTo(500);
        }

        @Test
        void refusesOptionsItCannotUse() {
            assertThatThrownBy(() -> new Server(null)).hasMessageContaining("needs its options");
            assertThatThrownBy(() -> new Server(Server.Options.of(null))).hasMessageContaining("needs a Router");
        }
    }

    @Nested
    class TheMiddlewareAroundEveryRoute {

        @Test
        void identifiesAndLogsTheRequest() {
            Logger.Collecting collecting = Logger.collecting();
            new Server(Server.Options.of(router()).withLogger(collecting.logger())).handle(Request.get("/health"));
            JsonNode line = collecting.records().get(0);
            assertThat(line.path("message").asText()).isEqualTo("request");
            assertThat(line.path("path").asText()).isEqualTo("/health");
            assertThat(line.path("status").asInt()).isEqualTo(200);
        }

        @Test
        void sendsTheRateLimitBack() {
            Response response = server().handle(Request.get("/health"));
            assertThat(response.headers()).containsKey("x-rate-limit-remaining");
        }

        @Test
        void refusesACallerWhoAsksTooOften() {
            Server limited = new Server(Server.Options.of(router()).withRateLimit(1));
            limited.handle(Request.get("/health"));
            assertThat(limited.handle(Request.get("/health")).status()).isEqualTo(429);
        }
    }

    @Nested
    class RequestFrom {

        @Test
        void readsTheMethodThePathAndTheQuery() {
            Request request = Server.requestFrom("POST", "/towers?stage=8", Map.of(), body(""));
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/towers");
            assertThat(request.query().get("stage")).isEqualTo("8");
        }

        @Test
        void parsesAJsonBody() {
            Request request = Server.requestFrom(
                    "GET", "/", Map.of("content-type", "application/json"), body("{\"name\":\"St Mary\"}"));
            assertThat(request.body()).isEqualTo(doc("{'name':'St Mary'}"));
        }

        @Test
        void keepsATextBodyAsText() {
            Request request = Server.requestFrom("GET", "/", Map.of("content-type", "text/plain"), body("hello"));
            assertThat(request.body().textValue()).isEqualTo("hello");
        }

        @Test
        void leavesTheBodyOutWhenThereIsNone() {
            assertThat(Server.requestFrom("GET", "/", Map.of(), body("")).body().isMissingNode())
                    .isTrue();
        }

        @Test
        void complainsAboutAJsonBodyThatIsNotJson() {
            assertThatThrownBy(
                            () -> Server.requestFrom("GET", "/", Map.of("content-type", "application/json"), body("{")))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class ServingOverASocket {

        @Test
        void answersARealRequest() throws Exception {
            try (Server.Listening listening = server().listen(0)) {
                HttpResponse<String> answer = fetch(listening.port(), "/health");
                assertThat(answer.statusCode()).isEqualTo(200);
                assertThat(Json.parse(answer.body())).isEqualTo(doc("{'ok':true}"));
            }
        }

        @Test
        void answers404OverTheSocketAsWell() throws Exception {
            try (Server.Listening listening = server().listen(0)) {
                HttpResponse<String> answer = fetch(listening.port(), "/nowhere");
                assertThat(answer.statusCode()).isEqualTo(404);
                assertThat(answer.headers().firstValue("content-type"))
                        .hasValueSatisfying(type -> assertThat(type).contains("application/json"));
            }
        }

        @Test
        void sendsATextResponseBackAsText() throws Exception {
            Server plain = new Server(
                    Server.Options.of(new Router().get("/plain", (request, context) -> Response.text("hello"))));
            try (Server.Listening listening = plain.listen(0)) {
                assertThat(fetch(listening.port(), "/plain").body()).isEqualTo("hello");
            }
        }
    }
}
