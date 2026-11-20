package io.github.rkaushikdev.garterhole.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LoggerTest {

    @Nested
    class WritingLines {

        @Test
        void writesOneJsonObjectPerLine() {
            Logger.Collecting collecting = Logger.collecting();
            collecting.logger().info("rang a quarter", Map.of("changes", 1260));
            assertThat(collecting.records())
                    .containsExactly(
                            Json.parse(
                                    "{\"at\":\"1970-01-01T00:00:00.000Z\",\"level\":\"info\",\"message\":\"rang a quarter\",\"changes\":1260}"));
        }

        @Test
        void saysWhetherTheLineWasWritten() {
            Logger logger = Logger.collecting("warn", () -> Instant.EPOCH).logger();
            assertThat(logger.warn("careful")).isTrue();
            assertThat(logger.info("ordinary")).isFalse();
        }

        @Test
        void hasAShortcutForEachLevel() {
            Logger.Collecting collecting = Logger.collecting();
            collecting.logger().debug("a");
            collecting.logger().info("b");
            collecting.logger().warn("c");
            assertThat(collecting.records())
                    .extracting(line -> line.get("level").asText())
                    .containsExactly("debug", "info", "warn");
        }
    }

    @Nested
    class Levels {

        @Test
        void writesEverythingAtOrAboveTheLevel() {
            Logger logger = new Logger("warn", line -> {});
            assertThat(logger.writes("warn")).isTrue();
            assertThat(logger.writes("error")).isTrue();
            assertThat(logger.writes("info")).isFalse();
        }

        @Test
        void writesNothingWhenSilent() {
            Logger logger = Logger.silent();
            assertThat(logger.writes("error")).isFalse();
            assertThat(logger.error("boom")).isFalse();
        }

        @Test
        void refusesALevelNobodyUses() {
            assertThatThrownBy(() -> new Logger("chatty", line -> {})).hasMessageContaining("is not a log level");
            assertThatThrownBy(() -> new Logger().writes("chatty")).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class Fields {

        @Test
        void carriesTheFieldsItWasBuiltWith() {
            Logger.Collecting collecting = Logger.collecting();
            Logger tower = collecting.logger().with(Map.of("tower", "St Mary"));
            tower.info("opened");
            assertThat(collecting.records().get(0).get("tower").asText()).isEqualTo("St Mary");
        }

        @Test
        void addsToTheFieldsRatherThanReplacingThem() {
            Logger.Collecting collecting = Logger.collecting();
            collecting
                    .logger()
                    .with(Map.of("tower", "St Mary"))
                    .with(Map.of("request", "abc"))
                    .info("handled");
            JsonNode line = collecting.records().get(0);
            assertThat(line.get("tower").asText()).isEqualTo("St Mary");
            assertThat(line.get("request").asText()).isEqualTo("abc");
        }

        @Test
        void letsALineOverrideACarriedField() {
            Logger.Collecting collecting = Logger.collecting();
            collecting.logger().with(Map.of("tower", "St Mary")).info("moved", Map.of("tower", "All Saints"));
            assertThat(collecting.records().get(0).get("tower").asText()).isEqualTo("All Saints");
        }

        @Test
        void keepsTheLevelAndTheSinkWhenItAddsFields() {
            Logger.Collecting collecting = Logger.collecting("warn", () -> Instant.EPOCH);
            Logger child = collecting.logger().with(Map.of("a", 1));
            assertThat(child.info("quiet")).isFalse();
            assertThat(child.warn("loud")).isTrue();
            assertThat(collecting.records()).hasSize(1);
        }
    }

    @Nested
    class Errors {

        @Test
        void unpacksAnExceptionIntoFields() {
            Logger.Collecting collecting = Logger.collecting();
            collecting.logger().error("could not read", new IllegalArgumentException("bell 9 is outside 1..8"));
            JsonNode line = collecting.records().get(0);
            assertThat(line.get("level").asText()).isEqualTo("error");
            assertThat(line.get("message").asText()).isEqualTo("could not read");
            assertThat(line.get("error").asText()).isEqualTo("bell 9 is outside 1..8");
            assertThat(line.get("errorName").asText()).isEqualTo("IllegalArgumentException");
        }

        @Test
        void keepsTheCodeOfALibraryError() {
            Logger.Collecting collecting = Logger.collecting();
            collecting.logger().error("refused", new ValidationException("nope"));
            assertThat(collecting.records().get(0).get("code").asText()).isEqualTo("validation_failed");
        }

        @Test
        void takesSomethingThatIsNotAnException() {
            Logger.Collecting collecting = Logger.collecting();
            collecting.logger().error("odd", "a string");
            assertThat(collecting.records().get(0).get("error").asText()).isEqualTo("a string");
        }

        @Test
        void takesNoErrorAtAll() {
            Logger.Collecting collecting = Logger.collecting();
            collecting.logger().error("just a message");
            assertThat(collecting.records().get(0).has("error")).isFalse();
        }
    }
}
