package io.github.rkaushikdev.garterhole.tools;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.garterhole.Version;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The version is written in three places and read from two of them at run
 * time. These tests are the reason it cannot quietly disagree with itself.
 */
class ReleaseCheckTest {

    private static final Pattern VERSION_NUMBER = Pattern.compile("\\d+\\.\\d+\\.\\d+");

    @Nested
    class VersionsFound {

        @Test
        void looksInAllThreePlaces() {
            assertThat(ReleaseCheck.versionsFound())
                    .extracting(ReleaseCheck.Found::where)
                    .containsExactly(
                            "build.gradle",
                            "src/main/java/io/github/rkaushikdev/garterhole/Version.java",
                            "CHANGELOG.md");
        }

        @Test
        void findsAVersionInEachOfThem() {
            for (ReleaseCheck.Found entry : ReleaseCheck.versionsFound()) {
                assertThat(entry.version())
                        .as(entry.where() + " has no version")
                        .hasValueSatisfying(version -> assertThat(version).matches(VERSION_NUMBER));
            }
        }

        @Test
        void findsTheSameVersionInEachOfThem() {
            assertThat(ReleaseCheck.versionsFound().stream()
                            .map(ReleaseCheck.Found::version)
                            .distinct())
                    .hasSize(1);
        }
    }

    @Nested
    class ReleaseProblems {

        @Test
        void findsNothingWrongWithThisRelease() {
            assertThat(ReleaseCheck.releaseProblems()).isEmpty();
            List<ReleaseCheck.Found> drifted = List.of(
                    new ReleaseCheck.Found("build.gradle", Optional.of("1.0.1")),
                    new ReleaseCheck.Found("Version.java", Optional.of("1.0.0")),
                    new ReleaseCheck.Found("CHANGELOG.md", Optional.empty()));
            assertThat(ReleaseCheck.releaseProblems(drifted))
                    .containsExactly(
                            "CHANGELOG.md does not say what version this is",
                            "the version disagrees: build.gradle says 1.0.1, Version.java says 1.0.0, CHANGELOG.md says null");
        }
    }

    @Nested
    class TheVersionItself {

        @Test
        void isAVersionNumber() {
            assertThat(Version.VERSION).matches(VERSION_NUMBER);
        }

        @Test
        void isWhatTheBuildFileSays() throws IOException {
            Matcher declared =
                    Pattern.compile("(?m)^version = '([^']+)'$").matcher(Files.readString(Path.of("build.gradle")));
            assertThat(declared.find()).isTrue();
            assertThat(declared.group(1)).isEqualTo(Version.VERSION);
        }

        @Test
        void isWhatTheBannerSays() {
            assertThat(Version.buildLabel()).isEqualTo(Version.PRODUCT + "/" + Version.VERSION);
        }

        @Test
        void isMentionedInTheChangelog() throws IOException {
            assertThat(Files.readString(Path.of("CHANGELOG.md"))).contains("## " + Version.VERSION);
        }
    }
}
