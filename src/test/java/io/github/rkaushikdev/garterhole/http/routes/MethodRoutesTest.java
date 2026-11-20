package io.github.rkaushikdev.garterhole.http.routes;

import static io.github.rkaushikdev.garterhole.http.Documents.object;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.Service;
import io.github.rkaushikdev.garterhole.http.Context;
import io.github.rkaushikdev.garterhole.http.Documents;
import io.github.rkaushikdev.garterhole.http.Response;
import io.github.rkaushikdev.garterhole.http.Router;
import io.github.rkaushikdev.garterhole.http.Server;
import io.github.rkaushikdev.garterhole.store.Journal;
import io.github.rkaushikdev.garterhole.store.Repository;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MethodRoutesTest {

    private static final String PLAIN_BOB_MAJOR = "{'name':'Plain','stage':8,'notation':'x18x18x18x18,12'}";
    private static final String CAMBRIDGE = "{'name':'Cambridge','stage':8,'notation':'x38x14x1258x36x14x58x16x78,12'}";

    @TempDir
    Path directory;

    private Server server;

    @BeforeEach
    void build() {
        Service service = new Service(new Repository(new Journal(directory.resolve("journal.jsonl"))));
        Router router = MethodRoutes.add(new Router());
        server = new Server(Server.Options.of(router).withContext(Context.of(service, router)));
    }

    private Response ask(String method, String url, JsonNode body) {
        return server.handle(Documents.ask(method, url, body));
    }

    private Response ask(String method, String url) {
        return server.handle(Documents.ask(method, url));
    }

    @Nested
    class AddingAMethod {

        @Test
        void filesItAndSaysWhereItWent() {
            Response response = ask("POST", "/methods", object(PLAIN_BOB_MAJOR));
            assertThat(response.status()).isEqualTo(201);
            assertThat(response.headers().get("location")).isEqualTo("/methods/Plain%20Bob%20Major");
            assertThat(response.body().path("title").asText()).isEqualTo("Plain Bob Major");
        }

        @Test
        void worksTheClassOutRatherThanTakingOne() {
            ObjectNode body = object(CAMBRIDGE).put("class", "Treble Bob");
            assertThat(ask("POST", "/methods", body).body().path("class").asText())
                    .isEqualTo("Surprise");
        }

        @Test
        void refusesABodyWithAFieldMissing() {
            Response response = ask("POST", "/methods", object("{'name':'Plain','stage':8}"));
            assertThat(response.status()).isEqualTo(400);
            assertThat(response.body().path("error").path("message").asText()).contains("notation");
        }

        @Test
        void refusesNotationThatDoesNotWorkAtTheStage() {
            Response response = ask("POST", "/methods", object("{'name':'Bad','stage':6,'notation':'x18x18'}"));
            assertThat(response.status()).isEqualTo(400);
            assertThat(response.body().path("error").path("message").asText()).contains("notation");
        }

        @Test
        void refusesAStageNobodyRings() {
            assertThat(ask("POST", "/methods", object(PLAIN_BOB_MAJOR).put("stage", 2))
                            .status())
                    .isEqualTo(400);
        }

        @Test
        void refusesTheSameMethodTwice() {
            ask("POST", "/methods", object(PLAIN_BOB_MAJOR));
            assertThat(ask("POST", "/methods", object(PLAIN_BOB_MAJOR)).status())
                    .isEqualTo(409);
        }
    }

    @Nested
    class ListingMethods {

        @BeforeEach
        void fileThree() {
            ask("POST", "/methods", object(PLAIN_BOB_MAJOR));
            ask("POST", "/methods", object(CAMBRIDGE));
            ask("POST", "/methods", object("{'name':'Plain','stage':6,'notation':'x1x1x1,2'}"));
        }

        @Test
        void listsEverythingWithATotal() {
            assertThat(ask("GET", "/methods").body().path("total").asInt()).isEqualTo(3);
        }

        @Test
        void filtersByStage() {
            assertThat(ask("GET", "/methods?stage=6").body().path("total").asInt())
                    .isEqualTo(1);
        }

        @Test
        void filtersByClass() {
            assertThat(ask("GET", "/methods?class=Surprise")
                            .body()
                            .path("total")
                            .asInt())
                    .isEqualTo(1);
        }

        @Test
        void givesNothingWhenNothingMatches() {
            assertThat(ask("GET", "/methods?stage=12").body().path("methods")).isEmpty();
        }
    }

    @Nested
    class ReadingOneMethod {

        @BeforeEach
        void fileCambridge() {
            ask("POST", "/methods", object(CAMBRIDGE));
        }

        @Test
        void describesItWithTheDerivedFacts() {
            Response response = ask("GET", "/methods/Cambridge%20Surprise%20Major");
            assertThat(response.status()).isEqualTo(200);
            assertThat(response.body().path("className").asText()).isEqualTo("Surprise");
            assertThat(response.body().path("leadLength").asInt()).isEqualTo(32);
        }

        @Test
        void answers404ForOneItDoesNotHold() {
            assertThat(ask("GET", "/methods/Bristol%20Surprise%20Major").status())
                    .isEqualTo(404);
        }

        @Test
        void refusesATitleThatIsNotOne() {
            assertThat(ask("GET", "/methods/nonsense").status()).isEqualTo(400);
        }

        @Test
        void removesOne() {
            assertThat(ask("DELETE", "/methods/Cambridge%20Surprise%20Major").status())
                    .isEqualTo(204);
            assertThat(ask("DELETE", "/methods/Cambridge%20Surprise%20Major").status())
                    .isEqualTo(404);
        }
    }

    @Nested
    class AnalysingATouch {

        @BeforeEach
        void filePlainBob() {
            ask("POST", "/methods", object(PLAIN_BOB_MAJOR));
        }

        @Test
        void ringsItAndReports() {
            Response response = ask("POST", "/methods/Plain%20Bob%20Major/analyse", object("{'touch':'3(H)'}"));
            assertThat(response.status()).isEqualTo(200);
            assertThat(response.body().path("length").asInt()).isEqualTo(336);
            assertThat(response.body().path("true").asBoolean()).isTrue();
        }

        @Test
        void refusesATouchItCannotRead() {
            assertThat(ask("POST", "/methods/Plain%20Bob%20Major/analyse", object("{'touch':'W)'}"))
                            .status())
                    .isEqualTo(400);
        }

        @Test
        void refusesARequestWithNoTouchInIt() {
            assertThat(ask("POST", "/methods/Plain%20Bob%20Major/analyse", object("{}"))
                            .status())
                    .isEqualTo(400);
        }

        @Test
        void answers404ForAMethodItDoesNotHold() {
            assertThat(ask("POST", "/methods/Nothing%20Major/analyse", object("{'touch':'3(H)'}"))
                            .status())
                    .isEqualTo(404);
        }
    }
}
