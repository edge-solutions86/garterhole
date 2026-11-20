package io.github.rkaushikdev.garterhole.http.middleware;

import static io.github.rkaushikdev.garterhole.http.middleware.RequestLog.REQUEST_ID_HEADER;
import static io.github.rkaushikdev.garterhole.http.middleware.RequestLog.identifierFor;
import static io.github.rkaushikdev.garterhole.http.middleware.RequestLog.isUsable;
import static io.github.rkaushikdev.garterhole.http.middleware.RequestLog.withRequestLog;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.garterhole.http.Handler;
import io.github.rkaushikdev.garterhole.http.Request;
import io.github.rkaushikdev.garterhole.http.Response;
import io.github.rkaushikdev.garterhole.util.Clock;
import io.github.rkaushikdev.garterhole.util.FixedClock;
import io.github.rkaushikdev.garterhole.util.IdSource;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.Logger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RequestLogTest {

    private static IdSource ids() {
        return new IdSource(() -> 1_700_000_000_000L, () -> 0);
    }

    private static final Handler HANDLER = request -> Response.json(
            Json.object().put("path", request.path()).put("id", request.id().orElse(null)));

    @Nested
    class IsUsable {

        @Test
        void acceptsAnIdentifierMadeOfSafeCharacters() {
            assertThat(isUsable("req_abc123")).isTrue();
            assertThat(isUsable("trace-1")).isTrue();
        }

        @Test
        void rejectsAnEmptyOneOrOneThatIsFarTooLong() {
            assertThat(isUsable("")).isFalse();
            assertThat(isUsable("a".repeat(100))).isFalse();
        }

        @Test
        void rejectsAnythingThatWouldMakeAMessOfALogLine() {
            assertThat(isUsable("req 1")).isFalse();
            assertThat(isUsable("req\n1")).isFalse();
            assertThat(isUsable("{\"a\":1}")).isFalse();
        }
    }

    @Nested
    class IdentifierFor {

        @Test
        void keepsAnIdentifierTheCallerSent() {
            Request request = Request.builder()
                    .url("/")
                    .header(REQUEST_ID_HEADER, "trace-9")
                    .build();
            assertThat(identifierFor(request, ids())).isEqualTo("trace-9");
        }

        @Test
        void makesOneWhenNoneWasSent() {
            assertThat(identifierFor(Request.get("/"), ids())).startsWith("req_");
        }

        @Test
        void makesOneWhenWhatWasSentIsUnusable() {
            Request request = Request.builder()
                    .url("/")
                    .header(REQUEST_ID_HEADER, "not a trace")
                    .build();
            assertThat(identifierFor(request, ids())).startsWith("req_");
        }
    }

    @Nested
    class WithRequestLog {

        @Test
        void givesTheHandlerARequestWithAnIdentifier() {
            Response response = withRequestLog(HANDLER, ids()).handle(Request.get("/health"));
            assertThat(response.body().path("id").asText()).startsWith("req_");
        }

        @Test
        void sendsTheIdentifierBackInAHeader() {
            Response response = withRequestLog(HANDLER, ids()).handle(Request.get("/health"));
            assertThat(response.headers().get(REQUEST_ID_HEADER))
                    .isEqualTo(response.body().path("id").asText());
        }

        @Test
        void writesOneLinePerRequest() {
            Logger.Collecting collecting = Logger.collecting();
            Handler wrapped = withRequestLog(HANDLER, collecting.logger(), ids(), Clock.system());
            wrapped.handle(
                    Request.builder().method("POST").url("/towers?stage=8").build());
            assertThat(collecting.records()).hasSize(1);
            JsonNode line = collecting.records().get(0);
            assertThat(line.path("message").asText()).isEqualTo("request");
            assertThat(line.path("method").asText()).isEqualTo("POST");
            assertThat(line.path("path").asText()).isEqualTo("/towers");
            assertThat(line.path("status").asInt()).isEqualTo(200);
        }

        @Test
        void timesTheRequestWithTheClockItWasGiven() {
            Logger.Collecting collecting = Logger.collecting();
            FixedClock clock = new FixedClock(0);
            Handler slow = request -> {
                clock.advance(37);
                return Response.json(Json.object().put("path", request.path()));
            };
            withRequestLog(slow, collecting.logger(), ids(), clock).handle(Request.get("/"));
            assertThat(collecting.records().get(0).path("ms").asInt()).isEqualTo(37);
        }

        @Test
        void logsTheStatusTheHandlerAnsweredWith() {
            Logger.Collecting collecting = Logger.collecting();
            Handler failing = request -> Response.json(Json.object().put("error", "no"), 404);
            withRequestLog(failing, collecting.logger(), ids(), Clock.system()).handle(Request.get("/nothing"));
            assertThat(collecting.records().get(0).path("status").asInt()).isEqualTo(404);
        }

        @Test
        void writesNothingWhenNoLoggerWasGiven() {
            assertThat(withRequestLog(HANDLER, ids()).handle(Request.get("/"))).isNotNull();
        }
    }
}
