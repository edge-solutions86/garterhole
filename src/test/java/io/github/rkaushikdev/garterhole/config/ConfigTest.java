package io.github.rkaushikdev.garterhole.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ConfigException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ConfigTest {

    @Nested
    class Defaults {

        @Test
        void startsWithSettingsThatWork() {
            Config config = Config.load();
            assertThat(config.port()).isEqualTo(8080);
            assertThat(config.host()).isEqualTo("127.0.0.1");
            assertThat(config.logLevel()).isEqualTo("info");
            assertThat(config.readOnly()).isFalse();
        }

        @Test
        void buildsThePathsFromTheDataDirectory() {
            Config config = Config.load(Map.of("GARTERHOLE_DATA", "/var/garterhole"));
            assertThat(config.journalPath()).contains("journal.jsonl");
            assertThat(config.snapshotPath()).contains("snapshot.json");
            assertThat(config.lockPath()).contains("lock");
        }

        @Test
        void treatsAnEmptyVariableAsUnset() {
            assertThat(Config.load(Map.of("GARTERHOLE_PORT", "")).port()).isEqualTo(8080);
        }
    }

    @Nested
    class ReadingTheEnvironment {

        @Test
        void readsAPort() {
            assertThat(Config.load(Map.of("GARTERHOLE_PORT", "9000")).port()).isEqualTo(9000);
        }

        @Test
        void readsAFlag() {
            assertThat(Config.load(Map.of("GARTERHOLE_READ_ONLY", "yes")).readOnly())
                    .isTrue();
            assertThat(Config.load(Map.of("GARTERHOLE_READ_ONLY", "off")).readOnly())
                    .isFalse();
        }

        @Test
        void readsALogLevelWhateverCaseItIsWrittenIn() {
            assertThat(Config.load(Map.of("GARTERHOLE_LOG_LEVEL", "WARN")).logLevel())
                    .isEqualTo("warn");
        }

        @Test
        void readsARateLimit() {
            assertThat(Config.load(Map.of("GARTERHOLE_RATE_LIMIT", "30")).requestsPerMinute())
                    .isEqualTo(30);
        }
    }

    @Nested
    class RefusingBadSettings {

        @Test
        void namesTheVariableThatIsWrong() {
            assertThatThrownBy(() -> Config.load(Map.of("GARTERHOLE_PORT", "eight thousand")))
                    .hasMessageContaining("GARTERHOLE_PORT is wrong");
        }

        @Test
        void refusesAPortOutsideTheRange() {
            assertThatThrownBy(() -> Config.load(Map.of("GARTERHOLE_PORT", "-1")))
                    .hasMessageContaining("must be a port number, not -1");
            assertThatThrownBy(() -> Config.load(Map.of("GARTERHOLE_PORT", "70000")))
                    .isInstanceOf(ConfigException.class);
        }

        @Test
        void allowsPortZeroWhichMeansLetTheMachineChoose() {
            assertThat(Config.load(Map.of("GARTERHOLE_PORT", "0")).port()).isZero();
        }

        @Test
        void refusesARateLimitThatAllowsNothing() {
            assertThatThrownBy(() -> Config.load(Map.of("GARTERHOLE_RATE_LIMIT", "0")))
                    .hasMessageContaining("at least one request");
        }

        @Test
        void refusesALogLevelNobodyUses() {
            assertThatThrownBy(() -> Config.load(Map.of("GARTERHOLE_LOG_LEVEL", "chatty")))
                    .hasMessageContaining("must be one of");
        }

        @Test
        void refusesAFlagThatIsNotOne() {
            assertThatThrownBy(() -> Config.load(Map.of("GARTERHOLE_READ_ONLY", "maybe")))
                    .isInstanceOf(ConfigException.class);
        }
    }

    @Nested
    class Describe {

        @Test
        void saysWhatTheServiceIsDoing() {
            String line = Config.load().describe();
            assertThat(line).contains("listening on 127.0.0.1:8080");
            assertThat(line).contains("accepting writes");
        }

        @Test
        void saysWhenItIsReadOnly() {
            assertThat(Config.load(Map.of("GARTERHOLE_READ_ONLY", "true")).describe())
                    .contains("read only");
        }
    }

    @Nested
    class SettingsList {

        @Test
        void namesEverySettingAndItsVariable() {
            List<Config.Setting> settings = Config.settingsList();
            assertThat(settings).extracting(Config.Setting::name).contains("port");
            assertThat(settings).allMatch(setting -> setting.variable().startsWith("GARTERHOLE_"));
        }

        @Test
        void listsTheLogLevelsQuietestLast() {
            assertThat(Config.LOG_LEVELS.get(Config.LOG_LEVELS.size() - 1)).isEqualTo("silent");
        }
    }
}
