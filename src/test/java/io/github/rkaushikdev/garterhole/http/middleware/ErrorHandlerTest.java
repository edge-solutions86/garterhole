package io.github.rkaushikdev.garterhole.http.middleware;

import static io.github.rkaushikdev.garterhole.http.middleware.ErrorHandler.catchingErrors;
import static io.github.rkaushikdev.garterhole.http.middleware.ErrorHandler.methodNotAllowed;
import static io.github.rkaushikdev.garterhole.http.middleware.ErrorHandler.notFound;
import static io.github.rkaushikdev.garterhole.http.middleware.ErrorHandler.responseForError;
import static io.github.rkaushikdev.garterhole.http.middleware.ErrorHandler.statusFor;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.garterhole.ConfigException;
import io.github.rkaushikdev.garterhole.ConflictException;
import io.github.rkaushikdev.garterhole.NotFoundException;
import io.github.rkaushikdev.garterhole.NotationException;
import io.github.rkaushikdev.garterhole.StorageException;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.http.Handler;
import io.github.rkaushikdev.garterhole.http.Request;
import io.github.rkaushikdev.garterhole.http.Response;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.Logger;
import io.github.rkaushikdev.garterhole.validation.Problem;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ErrorHandlerTest {

    @Nested
    class StatusFor {

        @Test
        void mapsTheTaxonomyOntoStatusCodes() {
            assertThat(statusFor(new NotFoundException("tower", "x"))).isEqualTo(404);
            assertThat(statusFor(new ConflictException("already there"))).isEqualTo(409);
            assertThat(statusFor(new ValidationException("wrong"))).isEqualTo(400);
            assertThat(statusFor(new NotationException("bad symbol"))).isEqualTo(400);
        }

        @Test
        void treatsAStorageOrConfigFailureAsOurs() {
            assertThat(statusFor(new StorageException("disk"))).isEqualTo(500);
            assertThat(statusFor(new ConfigException("port"))).isEqualTo(500);
        }

        @Test
        void treatsAGuardFailureAsTheCallerSendingNonsense() {
            assertThat(statusFor(new IllegalArgumentException("bell 9 is outside 1..8")))
                    .isEqualTo(400);
            assertThat(statusFor(new IllegalArgumentException("must be a Row"))).isEqualTo(400);
        }

        @Test
        void treatsAnythingElseAsABug() {
            assertThat(statusFor(new IllegalStateException("boom"))).isEqualTo(500);
            assertThat(statusFor("boom")).isEqualTo(500);
        }
    }

    @Nested
    class ResponseForError {

        @Test
        void sendsTheMessageBackForARejectedRequest() {
            Response response = responseForError(new NotFoundException("tower", "x"));
            assertThat(response.status()).isEqualTo(404);
            assertThat(response.body().path("error"))
                    .isEqualTo(Json.toNode(Map.of("code", "not_found", "message", "no tower with id \"x\"")));
        }

        @Test
        void carriesTheIssuesOfAValidationFailure() {
            ValidationException error = ValidationException.withIssues(
                    "the request is wrong", List.of(new Problem("name", "must be a string")));
            assertThat(responseForError(error).body().path("error").path("issues"))
                    .hasSize(1);
        }

        @Test
        void hidesTheDetailsOfABug() {
            Response response = responseForError(new IllegalStateException("the database password is hunter2"));
            assertThat(response.status()).isEqualTo(500);
            assertThat(response.body().path("error").path("message").asText())
                    .isEqualTo("something went wrong inside the service");
        }

        @Test
        void logsABugWithTheErrorAttached() {
            Logger.Collecting collecting = Logger.collecting();
            responseForError(new IllegalStateException("boom"), collecting.logger(), "req_1");
            JsonNode line = collecting.records().get(0);
            assertThat(line.path("level").asText()).isEqualTo("error");
            assertThat(line.path("error").asText()).isEqualTo("boom");
            assertThat(line.path("requestId").asText()).isEqualTo("req_1");
        }

        @Test
        void logsARefusalQuietly() {
            Logger.Collecting collecting = Logger.collecting();
            responseForError(new ValidationException("wrong"), collecting.logger(), "req_1");
            assertThat(collecting.records().get(0).path("level").asText()).isEqualTo("debug");
        }

        @Test
        void takesSomethingThatIsNotAnErrorOfTheLibrary() {
            assertThat(responseForError(new IllegalArgumentException("outside 1..8"))
                            .body()
                            .path("error")
                            .path("code")
                            .asText())
                    .isEqualTo("bad_request");
        }
    }

    @Nested
    class CatchingErrors {

        @Test
        void letsAGoodAnswerThrough() {
            Response answered = Response.text("answered");
            Handler wrapped = catchingErrors(request -> answered);
            assertThat(wrapped.handle(Request.get("/"))).isSameAs(answered);
        }

        @Test
        void turnsAThrowIntoAResponse() {
            Handler wrapped = catchingErrors(request -> {
                throw new NotFoundException("tower", "x");
            });
            Response response = wrapped.handle(Request.builder().id("req_1").build());
            assertThat(response.status()).isEqualTo(404);
        }

        @Test
        void turnsAFailureDeeperDownIntoAResponse() {
            Handler inner = request -> {
                throw new ValidationException("wrong");
            };
            Handler wrapped = catchingErrors(request -> inner.handle(request));
            assertThat(wrapped.handle(Request.get("/")).status()).isEqualTo(400);
        }

        @Test
        void passesTheRequestIdentifierToTheLog() {
            Logger.Collecting collecting = Logger.collecting();
            Handler wrapped = catchingErrors(
                    request -> {
                        throw new IllegalStateException("boom");
                    },
                    collecting.logger());
            wrapped.handle(Request.builder().id("req_9").build());
            assertThat(collecting.records().get(0).path("requestId").asText()).isEqualTo("req_9");
        }
    }

    @Nested
    class TheRoutingAnswers {

        @Test
        void saysNothingIsServedAtAPath() {
            Response response = notFound("/nowhere");
            assertThat(response.status()).isEqualTo(404);
            assertThat(response.body().path("error").path("message").asText())
                    .isEqualTo("nothing is served at /nowhere");
        }

        @Test
        void listsTheMethodsAPathDoesAnswerTo() {
            Response response = methodNotAllowed("POST", List.of("GET", "DELETE"));
            assertThat(response.status()).isEqualTo(405);
            assertThat(response.headers().get("allow")).isEqualTo("GET, DELETE");
        }
    }
}
