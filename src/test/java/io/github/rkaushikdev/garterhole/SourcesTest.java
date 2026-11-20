package io.github.rkaushikdev.garterhole;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The source files, checked as bytes rather than as behaviour.
 *
 * <p>A test that loads a class proves the class works on the machine that ran
 * it. It does not prove the file is the shape a tool will accept somewhere
 * else, and the gap between those two once cost a red build: a script carried
 * a shebang, the Windows checkout converted the line endings, and the first
 * line stopped being read as a comment.
 *
 * <p>Two things came out of that. The line endings are pinned in
 * {@code .gitattributes}, so a checkout is the same everywhere. And these
 * tests read the bytes, so the next file that arrives in an odd shape fails
 * here instead of in whichever job happens to run on a platform that converts
 * something.
 */
class SourcesTest {

    /** Every Java source and every build script, however deep. */
    static List<Path> sources() {
        List<Path> found = new ArrayList<>(filesUnder(Path.of("src"), ".java"));
        found.addAll(filesUnder(Path.of("."), ".gradle", 1));
        return found;
    }

    /** The repository's own tools, which the build runs as tasks. */
    static List<Path> tools() {
        return filesUnder(Path.of("src/main/java/io/github/rkaushikdev/garterhole/tools"), ".java").stream()
                .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                .toList();
    }

    /** The scripts a person runs directly, which keep their first line. */
    static List<Path> entryPoints() {
        return List.of(Path.of("gradlew"));
    }

    private static List<Path> filesUnder(Path directory, String suffix) {
        return filesUnder(directory, suffix, Integer.MAX_VALUE);
    }

    /**
     * Regular files only: a directory can end in the same suffix, as the
     * {@code .gradle} cache directory does.
     */
    private static List<Path> filesUnder(Path directory, String suffix, int depth) {
        try (Stream<Path> paths = Files.walk(directory, depth)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .map(Path::normalize)
                    .sorted()
                    .toList();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static String name(Path path) {
        return path.normalize().toString().replace('\\', '/');
    }

    private static String text(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * The characters that have no business being in a source file. Newline and
     * tab are ordinary; a carriage return is the one this test was written for.
     */
    static List<String> strayControlBytes(String text) {
        Set<String> strays = new LinkedHashSet<>();
        text.codePoints().forEach(code -> {
            if (code != '\n' && code != '\t' && (code < 32 || code == 127)) {
                strays.add(String.format("0x%02x", code));
            }
        });
        return List.copyOf(strays);
    }

    private static String classNameOf(Path tool) {
        String file = name(tool);
        return file.substring("src/main/java/".length(), file.length() - ".java".length())
                .replace('/', '.');
    }

    @Nested
    class TheFileListThisTestWorksFrom {

        @Test
        void foundSomethingSoAPassBelowMeansSomething() {
            assertThat(sources()).hasSizeGreaterThan(100);
            assertThat(tools()).hasSizeGreaterThanOrEqualTo(3);
            assertThat(entryPoints()).hasSizeGreaterThanOrEqualTo(1);
        }

        @Test
        void reachesIntoTheNestedDirectoriesRatherThanStoppingAtTheTop() {
            List<String> names = sources().stream().map(SourcesTest::name).toList();
            assertThat(names)
                    .contains(
                            "src/main/java/io/github/rkaushikdev/garterhole/http/routes/MethodRoutes.java",
                            "src/test/java/io/github/rkaushikdev/garterhole/striking/FaultsTest.java",
                            "build.gradle")
                    .noneMatch(file -> file.startsWith(".gradle/"));
        }

        @Test
        void wouldNoticeAStrayByteSinceThatIsTheWholePoint() {
            assertThat(strayControlBytes("a\rb")).containsExactly("0x0d");
            assertThat(strayControlBytes("a\u0000b")).containsExactly("0x00");
            assertThat(strayControlBytes("fine\n\tenough\n")).isEmpty();
        }
    }

    @Nested
    class EverySourceFile {

        @ParameterizedTest(name = "{0} has unix line endings")
        @MethodSource("io.github.rkaushikdev.garterhole.SourcesTest#sources")
        void hasUnixLineEndings(Path path) {
            assertThat(text(path)).doesNotContain("\r");
        }

        @ParameterizedTest(name = "{0} has no stray control bytes")
        @MethodSource("io.github.rkaushikdev.garterhole.SourcesTest#sources")
        void hasNoStrayControlBytes(Path path) {
            assertThat(strayControlBytes(text(path))).isEmpty();
        }

        @ParameterizedTest(name = "{0} has no byte order mark")
        @MethodSource("io.github.rkaushikdev.garterhole.SourcesTest#sources")
        void hasNoByteOrderMark(Path path) throws IOException {
            byte[] bytes = Files.readAllBytes(path);
            boolean bom = bytes.length >= 3
                    && (bytes[0] & 0xff) == 0xef
                    && (bytes[1] & 0xff) == 0xbb
                    && (bytes[2] & 0xff) == 0xbf;
            assertThat(bom).isFalse();
        }

        @ParameterizedTest(name = "{0} ends with a newline")
        @MethodSource("io.github.rkaushikdev.garterhole.SourcesTest#sources")
        void endsWithANewline(Path path) {
            assertThat(text(path)).endsWith("\n");
        }
    }

    @Nested
    class TheTools {

        @Test
        void areTheOnesTheBuildRefersTo() {
            assertThat(tools().stream().map(SourcesTest::name))
                    .containsExactly(
                            "src/main/java/io/github/rkaushikdev/garterhole/tools/Bench.java",
                            "src/main/java/io/github/rkaushikdev/garterhole/tools/ReleaseCheck.java",
                            "src/main/java/io/github/rkaushikdev/garterhole/tools/Smoke.java",
                            "src/main/java/io/github/rkaushikdev/garterhole/tools/SurfaceDocument.java");
            String build = text(Path.of("build.gradle"));
            for (Path tool : tools()) {
                String simpleName = tool.getFileName().toString().replace(".java", "");
                assertThat(build).contains("'" + simpleName + "'");
            }
        }

        @ParameterizedTest(name = "{0} has no shebang, since it is compiled")
        @MethodSource("io.github.rkaushikdev.garterhole.SourcesTest#tools")
        void hasNoShebang(Path path) {
            assertThat(text(path)).doesNotStartWith("#!");
        }

        @ParameterizedTest(name = "{0} can be loaded without doing its work")
        @MethodSource("io.github.rkaushikdev.garterhole.SourcesTest#tools")
        void canBeLoadedWithoutDoingItsWork(Path path) throws ClassNotFoundException {
            Class<?> loaded = Class.forName(classNameOf(path), true, SourcesTest.class.getClassLoader());
            List<Method> offered = Arrays.stream(loaded.getDeclaredMethods())
                    .filter(method -> Modifier.isPublic(method.getModifiers()))
                    .filter(method -> !method.getName().equals("main"))
                    .toList();
            assertThat(offered).isNotEmpty();
        }

        @ParameterizedTest(name = "{0} keeps the work it does in main")
        @MethodSource("io.github.rkaushikdev.garterhole.SourcesTest#tools")
        void guardsTheWorkItDoesFromTheCommandLine(Path path) throws ReflectiveOperationException {
            Method main = Class.forName(classNameOf(path)).getMethod("main", String[].class);
            assertThat(Modifier.isStatic(main.getModifiers())).isTrue();
            assertThat(text(path)).contains("public static void main(String[] args)");
        }

        @Test
        void leavesTheSurfaceDocumentAloneWhenItIsLoaded() throws ClassNotFoundException {
            String before = text(Path.of("docs", "surface.md"));
            Class.forName("io.github.rkaushikdev.garterhole.tools.SurfaceDocument");
            assertThat(io.github.rkaushikdev.garterhole.tools.SurfaceDocument.upToDate())
                    .isTrue();
            assertThat(text(Path.of("docs", "surface.md"))).isEqualTo(before);
        }
    }

    @Nested
    class TheEntryPoint {

        @ParameterizedTest(name = "{0} keeps its shebang, since it is run directly")
        @MethodSource("io.github.rkaushikdev.garterhole.SourcesTest#entryPoints")
        void keepsItsShebang(Path path) {
            assertThat(text(path)).startsWith("#!/bin/sh\n").doesNotContain("\r");
        }
    }

    @Nested
    class GitAttributes {

        @Test
        void isThereSinceTheLineEndingTestsAboveRelyOnIt() {
            assertThat(Path.of(".gitattributes")).isRegularFile();
        }

        @Test
        void pinsTheLineEndingsForEveryTextFile() {
            assertThat(text(Path.of(".gitattributes")))
                    .containsPattern("(?m)^\\* text=auto eol=lf$")
                    .containsPattern("(?m)^gradlew text eol=lf$");
        }
    }
}
