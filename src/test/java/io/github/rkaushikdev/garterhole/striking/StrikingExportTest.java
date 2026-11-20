package io.github.rkaushikdev.garterhole.striking;

import static io.github.rkaushikdev.garterhole.striking.Ringing.courseStrikes;
import static io.github.rkaushikdev.garterhole.striking.StrikingExport.asDocument;
import static io.github.rkaushikdev.garterhole.striking.StrikingExport.asJson;
import static io.github.rkaushikdev.garterhole.striking.StrikingExport.bellsAsCsv;
import static io.github.rkaushikdev.garterhole.striking.StrikingExport.blowsAsCsv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StrikingExportTest {

    private static StrikingSession session(Ringing.Shift shift) {
        return StrikingSession.analyse(6, courseStrikes(shift));
    }

    private static StrikingSession session() {
        return session(Ringing.Shift.NONE);
    }

    private static List<String> lines(String text) {
        return Arrays.asList(text.split("\n", -1));
    }

    @Nested
    class BlowsAsCsv {

        @Test
        void opensWithAHeaderRow() {
            assertThat(lines(blowsAsCsv(session())).get(0)).isEqualTo("row,place,bell,at,expected,error");
        }

        @Test
        void writesOneRowPerBlow() {
            assertThat(lines(blowsAsCsv(session()))).hasSize(61 * 6 + 1);
        }

        @Test
        void roundsTheTimesSoASpreadsheetIsReadable() {
            String line = lines(blowsAsCsv(session())).get(1);
            assertThat(line.split(",")).allMatch(cell -> cell.matches("-?\\d+"));
        }

        @Test
        void refusesSomethingThatIsNotASession() {
            assertThatThrownBy(() -> blowsAsCsv(null)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class BellsAsCsv {

        @Test
        void writesOneRowPerBell() {
            List<String> lines = lines(bellsAsCsv(session()));
            assertThat(lines).hasSize(7);
            assertThat(lines.get(0)).isEqualTo("bell,blows,rms,meanError");
        }

        @Test
        void showsTheBellThatStruckBadly() {
            List<String> lines = lines(bellsAsCsv(session((bell, row) -> bell == 4 ? 90 : 0)));
            String fourth = lines.stream()
                    .filter(line -> line.startsWith("4,"))
                    .findFirst()
                    .orElseThrow();
            assertThat(Double.parseDouble(fourth.split(",")[2])).isGreaterThan(0);
        }
    }

    @Nested
    class AsDocument {

        @Test
        void stampsTheFormatAndTheVersion() {
            ObjectNode document = asDocument(session());
            assertThat(document.path("format").asText()).isEqualTo("garterhole.striking");
            assertThat(document.path("version").asInt()).isEqualTo(1);
        }

        @Test
        void reportsTheModelItFitted() {
            ObjectNode document = asDocument(session());
            assertThat(document.path("model").path("interval").asDouble()).isCloseTo(250, byLessThan(0.05));
            assertThat(document.path("model").path("pealMinutes").asDouble()).isGreaterThan(0);
        }

        @Test
        void reportsTheAccuracyAndEveryBell() {
            ObjectNode document = asDocument(session());
            assertThat(document.path("accuracy").path("blows").asInt()).isEqualTo(366);
            assertThat(document.path("bells")).hasSize(6);
        }

        @Test
        void reportsTheWindows() {
            ObjectNode document = asDocument(session(), 20);
            assertThat(document.path("windows").size()).isGreaterThan(1);
            assertThat(document.path("windows").get(0).has("from")).isTrue();
        }

        @Test
        void reportsTheFaultsAsSentences() {
            ObjectNode document = asDocument(session((bell, row) -> bell == 4 ? 120 : 0));
            assertThat(document.path("faults").get(0).asText()).contains("bell 4");
        }

        @Test
        void reportsNothingStoppingACleanRecording() {
            assertThat(asDocument(session()).path("stoppedAt").isNull()).isTrue();
        }
    }

    @Nested
    class AsJson {

        @Test
        void writesSomethingThatReadsBack() {
            JsonNode parsed = Json.parse(asJson(session()));
            assertThat(parsed.path("rows").asInt()).isEqualTo(61);
            assertThat(parsed.path("stage").asInt()).isEqualTo(6);
        }

        @Test
        void refusesSomethingThatIsNotASession() {
            assertThatThrownBy(() -> asJson(null)).isInstanceOf(ValidationException.class);
        }
    }
}
