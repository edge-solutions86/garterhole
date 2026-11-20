package io.github.rkaushikdev.garterhole.tools;

import io.github.rkaushikdev.garterhole.StorageException;
import io.github.rkaushikdev.garterhole.Version;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Check that a release says the same thing everywhere. Run with
 * {@code ./gradlew releaseCheck}, which {@code check} also runs.
 *
 * <p>Three files carry the version: the build file, the class the banner and
 * the health route read, and the changelog. They drift, and they drift quietly,
 * because nothing breaks when they disagree. A service reporting a version it
 * is not is a bad afternoon during an incident, so this refuses to let them.
 */
public final class ReleaseCheck {

    /** Where the build file is, from the root of the repository. */
    public static final String BUILD_FILE = "build.gradle";

    /** Where the version class is, from the root of the repository. */
    public static final String VERSION_CLASS = "src/main/java/io/github/rkaushikdev/garterhole/Version.java";

    /** Where the changelog is, from the root of the repository. */
    public static final String CHANGELOG = "CHANGELOG.md";

    private static final Pattern BUILD_VERSION = Pattern.compile("(?m)^version\\s*=\\s*'([^']*)'\\s*$");
    private static final Pattern CHANGELOG_VERSION = Pattern.compile("(?m)^##\\s+(\\d+\\.\\d+\\.\\d+)[ \\t]*$");
    private static final Pattern VERSION_NUMBER = Pattern.compile("\\d+\\.\\d+\\.\\d+");

    private ReleaseCheck() {}

    /**
     * One place the version is written.
     *
     * @param where the file, from the root of the repository
     * @param version what it says, when it says anything
     */
    public record Found(String where, Optional<String> version) {}

    /**
     * @return everywhere the version is written, and what it says, read from
     *     the working directory
     */
    public static List<Found> versionsFound() {
        return versionsFound(Path.of(""));
    }

    /**
     * @param root the root of the repository
     * @return everywhere the version is written, and what it says
     */
    public static List<Found> versionsFound(Path root) {
        return List.of(
                new Found(BUILD_FILE, firstGroup(BUILD_VERSION, read(root.resolve(BUILD_FILE)))),
                new Found(VERSION_CLASS, Optional.of(Version.VERSION)),
                new Found(CHANGELOG, firstGroup(CHANGELOG_VERSION, read(root.resolve(CHANGELOG)))));
    }

    /**
     * @return what is wrong with the release in the working directory, if anything
     */
    public static List<String> releaseProblems() {
        return releaseProblems(versionsFound());
    }

    /**
     * @param found everywhere the version is written
     * @return what is wrong with the release, if anything
     */
    public static List<String> releaseProblems(List<Found> found) {
        List<String> problems = new ArrayList<>();
        for (Found entry : found) {
            if (entry.version().isEmpty()) {
                problems.add(entry.where() + " does not say what version this is");
            }
        }
        long distinct = found.stream()
                .map(Found::version)
                .flatMap(Optional::stream)
                .distinct()
                .count();
        if (distinct > 1) {
            List<String> sayings = found.stream()
                    .map(entry -> entry.where() + " says " + entry.version().orElse("null"))
                    .toList();
            problems.add("the version disagrees: " + String.join(", ", sayings));
        }
        if (!VERSION_NUMBER.matcher(Version.VERSION).matches()) {
            problems.add(Version.VERSION + " is not a version number");
        }
        return problems;
    }

    /**
     * Check the release and say what is wrong, exiting one when anything is.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        List<String> problems = releaseProblems();
        problems.forEach(System.err::println);
        if (!problems.isEmpty()) {
            System.exit(1);
        }
        System.out.println("everything agrees that this is " + Version.VERSION);
    }

    private static Optional<String> firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Optional.of(Objects.requireNonNull(matcher.group(1))) : Optional.empty();
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new StorageException("cannot read " + file + ": " + failure.getMessage(), failure);
        }
    }
}
