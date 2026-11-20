package io.github.rkaushikdev.garterhole.tools;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.garterhole.cli.Program;
import io.github.rkaushikdev.garterhole.config.Config;
import io.github.rkaushikdev.garterhole.http.Router;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The generated document has to match the code, and the test that checks it is
 * the same check the build runs. Keeping it here as well means a developer who
 * adds a route without regenerating the file finds out from the test run rather
 * than from a red build.
 */
class SurfaceDocumentTest {

    @Nested
    class EveryRoute {

        @Test
        void includesARouteFromEachGroup() {
            List<String> patterns = SurfaceDocument.everyRoute().stream()
                    .map(Router.RouteInfo::pattern)
                    .toList();
            assertThat(patterns).contains("/health", "/methods", "/towers", "/performances");
        }

        @Test
        void givesEveryRouteAMethod() {
            for (Router.RouteInfo route : SurfaceDocument.everyRoute()) {
                assertThat(List.of("GET", "POST", "DELETE")).contains(route.method());
            }
        }

        @Test
        void hasNoTwoRoutesThatWouldShadowEachOther() {
            Set<String> seen = new HashSet<>();
            for (Router.RouteInfo route : SurfaceDocument.everyRoute()) {
                assertThat(seen.add(route.method() + " " + route.pattern())).isTrue();
            }
        }
    }

    @Nested
    class TheDocument {

        @Test
        void listsEveryRoute() {
            String written = SurfaceDocument.document();
            for (Router.RouteInfo route : SurfaceDocument.everyRoute()) {
                assertThat(written).contains("`" + route.pattern() + "`");
            }
        }

        @Test
        void listsEveryCommand() {
            String written = SurfaceDocument.document();
            for (String name : Program.commandNames()) {
                assertThat(written).contains("garterhole " + name);
            }
        }

        @Test
        void listsEverySetting() {
            String written = SurfaceDocument.document();
            for (Config.Setting setting : Config.settingsList()) {
                assertThat(written).contains(setting.variable());
            }
        }

        @Test
        void matchesTheFileOnDisk() throws IOException {
            String onDisk = Files.readString(SurfaceDocument.OUTPUT, StandardCharsets.UTF_8);
            assertThat(onDisk).isEqualTo(SurfaceDocument.document());
        }
    }
}
