package io.github.rkaushikdev.garterhole.http.routes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.core.RowBlock;
import io.github.rkaushikdev.garterhole.http.Context;
import io.github.rkaushikdev.garterhole.http.Documents;
import io.github.rkaushikdev.garterhole.http.Request;
import io.github.rkaushikdev.garterhole.http.Response;
import io.github.rkaushikdev.garterhole.http.Router;
import io.github.rkaushikdev.garterhole.http.Server;
import io.github.rkaushikdev.garterhole.method.Method;
import io.github.rkaushikdev.garterhole.method.PlainCourse;
import io.github.rkaushikdev.garterhole.striking.TimingModel;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.List;
import java.util.function.IntBinaryOperator;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StrikingRoutesTest {

    private Server server;

    @BeforeEach
    void build() {
        Router router = StrikingRoutes.add(new Router());
        server = new Server(Server.Options.of(router).withContext(Context.of(null, router)));
    }

    private Response ask(String url, JsonNode body) {
        return server.handle(Documents.ask("POST", url, body));
    }

    /** Every blow of a plain course of Plain Bob Minor, each moved by what the shift says. */
    private static ArrayNode strikes(IntBinaryOperator shift) {
        RowBlock block = PlainCourse.plainCourse(Method.of("Plain", 6, "x1x1x1,2"));
        TimingModel model = TimingModel.of(6, 250);
        ArrayNode made = Json.array();
        List<Row> rows = block.rows();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex += 1) {
            List<Integer> bells = rows.get(rowIndex).bells();
            for (int place = 0; place < bells.size(); place += 1) {
                int bell = bells.get(place);
                ObjectNode strike = made.addObject().put("bell", bell);
                Json.putNumber(strike, "at", model.expectedAt(rowIndex, place + 1) + shift.applyAsInt(bell, rowIndex));
            }
        }
        return made;
    }

    private static ArrayNode strikes() {
        return strikes((bell, row) -> 0);
    }

    private static ObjectNode recording(IntBinaryOperator shift) {
        ObjectNode recording = Json.object().put("stage", 6);
        recording.set("strikes", strikes(shift));
        return recording;
    }

    private static ObjectNode recording() {
        return recording((bell, row) -> 0);
    }

    @Nested
    class AnalysingARecording {

        @Test
        void answersWithTheWholeAnalysis() {
            Response response = ask("/striking", recording());
            assertThat(response.status()).isEqualTo(200);
            assertThat(response.body().path("format").asText()).isEqualTo("garterhole.striking");
            assertThat(response.body().path("rows").asInt()).isEqualTo(61);
        }

        @Test
        void reportsTheAccuracyAndEveryBell() {
            Response response = ask("/striking", recording());
            // toBeCloseTo(0, 1): within half of one tenth, exclusive.
            assertThat(Math.abs(response.body().path("accuracy").path("rms").asDouble()))
                    .isLessThan(0.05);
            assertThat(response.body().path("bells")).hasSize(6);
        }

        @Test
        void findsTheBellThatStruckBadly() {
            Response response = ask("/striking", recording((bell, row) -> bell == 4 ? 120 : 0));
            String faults = String.join(
                    " ",
                    StreamSupport.stream(response.body().path("faults").spliterator(), false)
                            .map(JsonNode::asText)
                            .toList());
            assertThat(faults).contains("bell 4");
        }

        @Test
        void takesAWindowSize() {
            assertThat(ask("/striking?window=10", recording())
                            .body()
                            .path("windows")
                            .size())
                    .isGreaterThan(3);
        }

        @Test
        void refusesAWindowItWillNotMeasure() {
            assertThat(ask("/striking?window=0", recording()).status()).isEqualTo(400);
        }

        @Test
        void refusesARecordingWithABellThatIsNotOnTheStage() {
            JsonNode body = Documents.doc("{'stage':6,'strikes':[{'bell':9,'at':0},{'bell':1,'at':1}]}");
            assertThat(ask("/striking", body).status()).isEqualTo(400);
        }

        @Test
        void refusesARecordingWithNothingInIt() {
            assertThat(ask("/striking", Documents.doc("{'stage':6,'strikes':[]}"))
                            .status())
                    .isEqualTo(400);
        }

        @Test
        void refusesABodyThatIsNotARecording() {
            assertThat(ask("/striking", Documents.doc("{'stage':6}")).status()).isEqualTo(400);
        }
    }

    @Nested
    class ARecordingThatStopsMakingSense {

        private ObjectNode broken() {
            ArrayNode strikes = Json.array();
            ArrayNode whole = strikes();
            for (int index = 0; index < 12; index += 1) {
                strikes.add(whole.get(index));
            }
            int[] bells = {1, 1, 3, 4, 5, 6};
            for (int index = 0; index < bells.length; index += 1) {
                strikes.addObject().put("bell", bells[index]).put("at", 90000 + index * 250);
            }
            ObjectNode recording = Json.object().put("stage", 6);
            recording.set("strikes", strikes);
            return recording;
        }

        @Test
        void analysesWhatItCanByDefault() {
            Response response = ask("/striking", broken());
            assertThat(response.status()).isEqualTo(200);
            assertThat(response.body().path("stoppedAt").asInt()).isEqualTo(2);
        }

        @Test
        void refusesTheWholeThingWhenToldNotToBeLenient() {
            assertThat(ask("/striking?lenient=false", broken()).status()).isEqualTo(400);
        }
    }

    @Nested
    class TheOtherShapes {

        @Test
        void writesAReportABandCanRead() {
            Response response = ask("/striking/report", recording());
            assertThat(response.headers().get("content-type")).contains("text/plain");
            assertThat(response.body().asText()).contains("rows rung");
        }

        @Test
        void writesTheBlowsAsASpreadsheet() {
            Response response = ask("/striking/blows.csv", recording());
            assertThat(response.headers().get("content-type")).contains("text/csv");
            assertThat(response.body().asText().split("\n", -1)[0]).isEqualTo("row,place,bell,at,expected,error");
        }

        @Test
        void writesTheBellsAsASpreadsheet() {
            assertThat(ask("/striking/bells.csv", recording()).body().asText().split("\n", -1))
                    .hasSize(7);
        }
    }

    @Nested
    class WindowRowsOf {

        @Test
        void defaultsToTwentyRows() {
            assertThat(StrikingRoutes.windowRowsOf(Request.get("/striking"))).isEqualTo(20);
        }

        @Test
        void takesASizeFromTheQuery() {
            assertThat(StrikingRoutes.windowRowsOf(Request.get("/striking?window=40")))
                    .isEqualTo(40);
        }

        @Test
        void refusesASizeItWillNotMeasure() {
            assertThatThrownBy(() -> StrikingRoutes.windowRowsOf(Request.get("/striking?window=0")))
                    .isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> StrikingRoutes.windowRowsOf(Request.get("/striking?window=lots")))
                    .hasMessageContaining("is not one this will measure");
        }
    }
}
