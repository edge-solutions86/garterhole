package io.github.rkaushikdev.garterhole.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RouterTest {

    private static final RouteHandler HANDLER = (request, context) -> Response.text("answered");

    @Nested
    class AddingRoutes {

        @Test
        void takesAMethodAPatternAndAHandler() {
            Router router = new Router().get("/health", HANDLER);
            assertThat(router.list()).containsExactly(new Router.RouteInfo("GET", "/health"));
        }

        @Test
        void hasAShortcutForEachMethodItUses() {
            Router router = new Router().get("/a", HANDLER).post("/b", HANDLER).delete("/c", HANDLER);
            assertThat(router.list()).extracting(Router.RouteInfo::method).containsExactly("GET", "POST", "DELETE");
        }

        @Test
        void upperCasesTheMethod() {
            assertThat(new Router().add("get", "/a", HANDLER).list().get(0).method())
                    .isEqualTo("GET");
        }

        @Test
        void refusesAPatternWithNoLeadingSlash() {
            assertThatThrownBy(() -> new Router().get("health", HANDLER))
                    .hasMessageContaining("must start with a slash");
        }

        @Test
        void refusesARouteItCannotUse() {
            assertThatThrownBy(() -> new Router().get("", HANDLER)).hasMessageContaining("a route pattern");
            assertThatThrownBy(() -> new Router().get("/a", null)).hasMessageContaining("a route handler");
            assertThatThrownBy(() -> new Router().get("health", HANDLER)).isInstanceOf(ValidationException.class);
        }

        @Test
        void refusesARouteThatCouldNeverBeReached() {
            Router router = new Router().get("/towers/:id", HANDLER);
            assertThatThrownBy(() -> router.get("/towers/:name", HANDLER))
                    .hasMessageContaining("would never be reached");
        }

        @Test
        void allowsALiteralRouteTheParameterRouteWouldShadowInEitherOrder() {
            Router router = new Router().get("/towers/all", HANDLER);
            assertThatCode(() -> router.get("/towers/:id", HANDLER)).doesNotThrowAnyException();
        }

        @Test
        void allowsTheSamePatternUnderADifferentMethod() {
            Router router = new Router().get("/towers/:id", HANDLER);
            assertThatCode(() -> router.delete("/towers/:id", HANDLER)).doesNotThrowAnyException();
        }
    }

    @Nested
    class Matching {

        private Router router() {
            return new Router()
                    .get("/health", HANDLER)
                    .get("/towers/all", HANDLER)
                    .get("/towers/:id", HANDLER)
                    .get("/towers/:id/performances", HANDLER)
                    .post("/towers", HANDLER);
        }

        @Test
        void findsAPlainRoute() {
            assertThat(router().match("GET", "/health").orElseThrow().pattern()).isEqualTo("/health");
        }

        @Test
        void capturesAParameter() {
            Router.Match found = router().match("GET", "/towers/st-mary").orElseThrow();
            assertThat(found.params()).isEqualTo(Map.of("id", "st-mary"));
        }

        @Test
        void capturesSeveralSegmentsDeep() {
            Router.Match found =
                    router().match("GET", "/towers/st-mary/performances").orElseThrow();
            assertThat(found.pattern()).isEqualTo("/towers/:id/performances");
            assertThat(found.params().get("id")).isEqualTo("st-mary");
        }

        @Test
        void prefersTheRouteAddedFirst() {
            assertThat(router().match("GET", "/towers/all").orElseThrow().pattern())
                    .isEqualTo("/towers/all");
        }

        @Test
        void decodesWhatIsInAParameter() {
            assertThat(router().match("GET", "/towers/st%20mary")
                            .orElseThrow()
                            .params()
                            .get("id"))
                    .isEqualTo("st mary");
        }

        @Test
        void ignoresATrailingSlash() {
            assertThat(router().match("GET", "/health/")).isPresent();
        }

        @Test
        void givesNothingWhenNothingMatches() {
            assertThat(router().match("GET", "/nothing")).isEmpty();
            assertThat(router().match("GET", "/towers/st-mary/bells")).isEmpty();
        }

        @Test
        void givesNothingWhenTheMethodIsWrong() {
            assertThat(router().match("POST", "/health")).isEmpty();
        }
    }

    @Nested
    class MethodsFor {

        private Router router() {
            return new Router()
                    .get("/towers/:id", HANDLER)
                    .delete("/towers/:id", HANDLER)
                    .get("/health", HANDLER);
        }

        @Test
        void listsTheMethodsAPathAnswersTo() {
            assertThat(router().methodsFor("/towers/st-mary")).containsExactly("DELETE", "GET");
        }

        @Test
        void listsOneMethodWhereThereIsOne() {
            assertThat(router().methodsFor("/health")).containsExactly("GET");
        }

        @Test
        void listsNothingForAPathNobodyServes() {
            assertThat(router().methodsFor("/nothing")).isEmpty();
        }
    }
}
