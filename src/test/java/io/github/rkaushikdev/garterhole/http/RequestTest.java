package io.github.rkaushikdev.garterhole.http;

import static io.github.rkaushikdev.garterhole.http.Documents.doc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RequestTest {

    /** A stream that hands over the chunks it was given, one read at a time. */
    private static InputStream stream(String... chunks) {
        List<InputStream> parts = java.util.Arrays.stream(chunks)
                .map(chunk -> (InputStream) new ByteArrayInputStream(chunk.getBytes(StandardCharsets.UTF_8)))
                .toList();
        return new SequenceInputStream(Collections.enumeration(parts));
    }

    /** A stream that fails as soon as it is read. */
    private static InputStream failing(String message) {
        return new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException(message);
            }
        };
    }

    @Nested
    class Requests {

        @Test
        void readsThePathAndTheQueryApart() {
            Request request = Request.builder()
                    .method("get")
                    .url("/towers?stage=8&cover=yes")
                    .build();
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.path()).isEqualTo("/towers");
            assertThat(request.query()).containsExactly(Map.entry("stage", "8"), Map.entry("cover", "yes"));
        }

        @Test
        void lowerCasesTheHeaderNames() {
            Request request = Request.builder()
                    .url("/")
                    .header("Content-Type", "application/json")
                    .build();
            assertThat(request.header("content-type")).hasValue("application/json");
            assertThat(request.header("CONTENT-TYPE")).hasValue("application/json");
        }

        @Test
        void joinsAHeaderSentMoreThanOnce() {
            Request request = Request.builder()
                    .url("/")
                    .header("accept", List.of("a", "b"))
                    .build();
            assertThat(request.header("accept")).hasValue("a, b");
        }

        @Test
        void givesNothingForAHeaderThatWasNotSent() {
            assertThat(Request.get("/").header("x-nothing")).isEmpty();
        }

        @Test
        void refusesPartsItCannotUse() {
            assertThatThrownBy(() -> new Request(null)).hasMessageContaining("a request needs its parts");
        }

        @Test
        void defaultsTheMethodAndThePath() {
            Request request = Request.builder().build();
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.path()).isEqualTo("/");
        }
    }

    @Nested
    class Parameters {

        private Request request() {
            return Request.builder()
                    .url("/towers/st-mary")
                    .params(Map.of("id", "st-mary"))
                    .build();
        }

        @Test
        void readsACapturedParameter() {
            assertThat(request().param("id")).isEqualTo("st-mary");
        }

        @Test
        void complainsAboutOneTheRouteDoesNotCapture() {
            assertThatThrownBy(() -> request().param("name")).hasMessageContaining("no parameter called \"name\"");
        }

        @Test
        void readsAQueryValueWithAFallback() {
            Request withQuery = Request.get("/towers?stage=8");
            assertThat(withQuery.queryValue("stage")).hasValue("8");
            assertThat(withQuery.queryValue("cover", "no")).isEqualTo("no");
        }

        @Test
        void treatsAnEmptyQueryValueAsMissing() {
            assertThat(Request.get("/towers?stage=").queryValue("stage", "6")).isEqualTo("6");
        }
    }

    @Nested
    class RebuildingARequest {

        private Request original() {
            return Request.builder()
                    .method("POST")
                    .url("/towers?stage=8")
                    .header("content-type", "application/json")
                    .body(doc("{'name':'St Mary'}"))
                    .build();
        }

        @Test
        void keepsEverythingWhenParametersAreAdded() {
            Request withParams = original().withParams(Map.of("id", "st-mary"));
            assertThat(withParams.param("id")).isEqualTo("st-mary");
            assertThat(withParams.body()).isEqualTo(doc("{'name':'St Mary'}"));
            assertThat(withParams.query()).isEqualTo(Map.of("stage", "8"));
            assertThat(withParams.method()).isEqualTo("POST");
        }

        @Test
        void keepsEverythingWhenAnIdentifierIsAdded() {
            Request withId = original().withId("req_1");
            assertThat(withId.id()).hasValue("req_1");
            assertThat(withId.header("content-type")).hasValue("application/json");
        }
    }

    @Nested
    class SendsJson {

        @Test
        void isTrueForAJsonContentType() {
            assertThat(Request.builder()
                            .url("/")
                            .header("content-type", "application/json")
                            .build()
                            .sendsJson())
                    .isTrue();
        }

        @Test
        void ignoresTheParametersAfterTheType() {
            Request request = Request.builder()
                    .url("/")
                    .header("content-type", "application/json; charset=utf-8")
                    .build();
            assertThat(request.sendsJson()).isTrue();
        }

        @Test
        void isFalseForAnythingElseOrNothing() {
            assertThat(Request.builder()
                            .url("/")
                            .header("content-type", "text/plain")
                            .build()
                            .sendsJson())
                    .isFalse();
            assertThat(Request.get("/").sendsJson()).isFalse();
        }
    }

    @Nested
    class ReadBody {

        @Test
        void joinsTheChunks() {
            assertThat(Request.readBody(stream("{\"a\":", "1}"))).isEqualTo("{\"a\":1}");
        }

        @Test
        void givesAnEmptyStringForNoBody() {
            assertThat(Request.readBody(stream())).isEmpty();
        }

        @Test
        void refusesABodyOverTheLimit() {
            assertThatThrownBy(() -> Request.readBody(stream("abcdef"), 3)).hasMessageContaining("longer than 3 bytes");
        }

        @Test
        void passesAStreamFailureOn() {
            assertThatThrownBy(() -> Request.readBody(failing("gone"))).hasMessage("gone");
        }

        @Test
        void hasADefaultLimit() {
            assertThat(Request.MAX_BODY_BYTES).isGreaterThan(1000);
        }
    }

    @Nested
    class ParseJsonBody {

        @Test
        void parsesABody() {
            assertThat(Request.parseJsonBody("{\"a\":1}")).isEqualTo(doc("{'a':1}"));
        }

        @Test
        void givesNothingForAnEmptyBody() {
            assertThat(Request.parseJsonBody("").isMissingNode()).isTrue();
        }

        @Test
        void complainsAboutABodyThatIsNotJson() {
            assertThatThrownBy(() -> Request.parseJsonBody("{")).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> Request.parseJsonBody("{")).hasMessageContaining("is not JSON");
        }
    }
}
