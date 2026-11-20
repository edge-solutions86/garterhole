package io.github.rkaushikdev.garterhole.http;

import static io.github.rkaushikdev.garterhole.http.Documents.doc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.Version;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.validation.Problem;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ResponseTest {

    @Nested
    class Responses {

        @Test
        void defaultsToTwoHundred() {
            assertThat(new Response().status()).isEqualTo(200);
        }

        @Test
        void isFrozen() {
            Response response = Response.text("hello");
            assertThat(Modifier.isFinal(Response.class.getModifiers())).isTrue();
            assertThatThrownBy(() -> response.headers().put("x-tower", "St Mary"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void refusesAStatusThatIsNotOne() {
            assertThatThrownBy(() -> new Response(99, Map.of(), null)).hasMessageContaining("is not a status code");
            assertThatThrownBy(() -> new Response(600, Map.of(), null)).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> new Response(-200, Map.of(), null)).isInstanceOf(ValidationException.class);
        }

        @Test
        void givesANewResponseWhenAHeaderIsAdded() {
            Response first = new Response();
            Response second = first.withHeader("x-tower", "St Mary");
            assertThat(second.headers().get("x-tower")).isEqualTo("St Mary");
            assertThat(first.headers().get("x-tower")).isNull();
        }

        @Test
        void givesANewResponseWhenTheStatusChanges() {
            Response first = Response.json(doc("{'a':1}"));
            assertThat(first.withStatus(201).status()).isEqualTo(201);
            assertThat(first.status()).isEqualTo(200);
        }
    }

    @Nested
    class Rendering {

        @Test
        void writesJsonOutAndMeasuresIt() {
            Response.Rendered rendered = Response.json(doc("{'a':1}")).render();
            assertThat(rendered.text()).isEqualTo("{\"a\":1}");
            assertThat(rendered.headers().get("content-length")).isEqualTo("7");
            assertThat(rendered.headers().get("content-type")).isEqualTo("application/json; charset=utf-8");
        }

        @Test
        void saysWhichVersionAnswered() {
            assertThat(Response.json(doc("{}")).render().headers().get("x-served-by"))
                    .contains(Version.VERSION);
        }

        @Test
        void measuresBytesRatherThanCharacters() {
            Response.Rendered rendered = Response.text("café").render();
            assertThat(rendered.headers().get("content-length")).isEqualTo("5");
        }

        @Test
        void rendersAnEmptyBodyAsNothing() {
            Response.Rendered rendered = Response.noContent().render();
            assertThat(rendered.text()).isEmpty();
            assertThat(rendered.status()).isEqualTo(204);
        }

        @Test
        void leavesAStringBodyAlone() {
            assertThat(Response.text("hello").render().text()).isEqualTo("hello");
        }

        @Test
        void letsAHeaderTheCallerSetWin() {
            Response.Rendered rendered = Response.json(
                            doc("{}"), 200, Map.of("content-type", "application/problem+json"))
                    .render();
            assertThat(rendered.headers().get("content-type")).isEqualTo("application/problem+json");
        }
    }

    @Nested
    class TheShortcuts {

        @Test
        void makesAJsonResponse() {
            Response response = Response.json(doc("{'a':1}"), 202);
            assertThat(response.status()).isEqualTo(202);
            assertThat(response.body()).isEqualTo(doc("{'a':1}"));
        }

        @Test
        void makesATextResponse() {
            Response response = Response.text("hello");
            assertThat(response.headers().get("content-type")).isEqualTo("text/plain; charset=utf-8");
            assertThat(response.body().asText()).isEqualTo("hello");
        }

        @Test
        void makesACreatedResponseWithALocation() {
            Response response = Response.created("/towers/st-mary", doc("{'id':'st-mary'}"));
            assertThat(response.status()).isEqualTo(201);
            assertThat(response.headers().get("location")).isEqualTo("/towers/st-mary");
        }

        @Test
        void makesAnEmptyResponse() {
            assertThat(Response.noContent().body().isMissingNode()).isTrue();
        }
    }

    @Nested
    class Problems {

        @Test
        void putsEveryErrorInTheSameShape() {
            Response response = Response.problem(404, "not_found", "no tower with id \"x\"");
            assertThat(response.status()).isEqualTo(404);
            ObjectNode error = Json.object().put("code", "not_found").put("message", "no tower with id \"x\"");
            ObjectNode expected = Json.object();
            expected.set("error", error);
            assertThat(response.body()).isEqualTo(expected);
        }

        @Test
        void carriesTheIssuesWhenThereAreSome() {
            Response response = Response.problem(
                    400, "validation_failed", "the request is wrong", List.of(new Problem("name", "must be a string")));
            assertThat(response.body().path("error").path("issues")).hasSize(1);
        }

        @Test
        void leavesTheIssuesOutWhenThereAreNone() {
            assertThat(Response.problem(400, "validation_failed", "nope")
                            .body()
                            .path("error")
                            .has("issues"))
                    .isFalse();
        }
    }
}
