package io.github.rkaushikdev.garterhole.tools;

import io.github.rkaushikdev.garterhole.StorageException;
import io.github.rkaushikdev.garterhole.Version;
import io.github.rkaushikdev.garterhole.cli.Program;
import io.github.rkaushikdev.garterhole.config.Config;
import io.github.rkaushikdev.garterhole.http.App;
import io.github.rkaushikdev.garterhole.http.Router;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Write the list of routes, commands and settings out to
 * {@code docs/surface.md}. Run with {@code ./gradlew surface}.
 *
 * <p>The documentation of a service's HTTP surface goes stale the day somebody
 * adds a route and forgets the file. This reads the router, the command table
 * and the settings instead, so the document is a report rather than a promise.
 *
 * <p>Run with {@code --check}, which {@code ./gradlew surfaceCheck} and
 * therefore {@code check} do: it writes nothing and fails when the file on disk
 * disagrees with what the code would produce.
 */
public final class SurfaceDocument {

    /** Where the document is written, from the root of the repository. */
    public static final Path OUTPUT = Path.of("docs", "surface.md");

    private SurfaceDocument() {}

    /**
     * @return every route the service serves, in the order they are registered
     */
    public static List<Router.RouteInfo> everyRoute() {
        return App.everyRoute().list();
    }

    /**
     * @return the document
     */
    public static String document() {
        List<String> lines = new ArrayList<>(List.of(
                "# The surface",
                "",
                "Written by `./gradlew surface` from the code itself, for " + Version.buildLabel() + ".",
                "",
                "## HTTP routes",
                "",
                "| method | path |",
                "| --- | --- |"));
        everyRoute().forEach(route -> lines.add("| " + route.method() + " | `" + route.pattern() + "` |"));
        lines.addAll(List.of("", "## Commands", ""));
        Program.commandNames().forEach(name -> lines.add("- `garterhole " + name + "`"));
        lines.addAll(List.of("", "## Settings", "", "| variable | default |", "| --- | --- |"));
        Config.settingsList()
                .forEach(setting -> lines.add("| `" + setting.variable() + "` | `" + setting.fallback() + "` |"));
        lines.add("");
        return String.join("\n", lines);
    }

    /**
     * @return true when the file in the working directory is already what the code says it should be
     */
    public static boolean upToDate() {
        return upToDate(Path.of(""));
    }

    /**
     * @param root the root of the repository
     * @return true when the file on disk is already what the code says it should be
     */
    public static boolean upToDate(Path root) {
        Path file = root.resolve(OUTPUT);
        try {
            return Files.isRegularFile(file)
                    && Files.readString(file, StandardCharsets.UTF_8).equals(document());
        } catch (IOException failure) {
            throw new StorageException("cannot read " + file + ": " + failure.getMessage(), failure);
        }
    }

    /**
     * Write the document out, making the directory if it is not there yet.
     *
     * @param root the root of the repository
     * @return the path written
     */
    public static Path write(Path root) {
        Path file = root.resolve(OUTPUT);
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, document(), StandardCharsets.UTF_8);
            return file;
        } catch (IOException failure) {
            throw new StorageException("cannot write " + file + ": " + failure.getMessage(), failure);
        }
    }

    /**
     * Write the document, or with {@code --check} only compare it, exiting one
     * when it is out of date.
     *
     * @param args {@code --check} to compare rather than write
     */
    public static void main(String[] args) {
        if (List.of(args).contains("--check")) {
            if (!upToDate()) {
                System.err.println(OUTPUT + " is out of date, so run ./gradlew surface");
                System.exit(1);
            }
            System.out.println(OUTPUT + " is up to date");
        } else {
            System.out.println("wrote " + write(Path.of("")));
        }
    }
}
