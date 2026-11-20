package io.github.rkaushikdev.garterhole.cli;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.Text;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * What the command line prints.
 *
 * <p>Two shapes, chosen by a flag. A person gets a table; a script gets JSON.
 * Both come out of the same command, so nobody has to parse the table.
 *
 * <p>The output takes its sinks rather than reaching for the process streams,
 * which is what lets the tests read what a command printed without capturing
 * the standard output of the whole run. Answers go to the out sink; notes and
 * problems go to the err sink, so a script reading the answer is not handed
 * them by accident.
 */
public final class Output {

    private final Consumer<String> out;
    private final Consumer<String> err;
    private final boolean json;
    private final boolean quiet;

    /** An output that writes nowhere, answering in text. */
    public Output() {
        this(line -> {}, line -> {}, false, false);
    }

    /**
     * @param out where answers go, one line at a time
     * @param err where notes and problems go, one line at a time
     * @param json true to answer in JSON
     * @param quiet true to leave the notes out
     */
    public Output(Consumer<String> out, Consumer<String> err, boolean json, boolean quiet) {
        this.out = out;
        this.err = err;
        this.json = json;
        this.quiet = quiet;
    }

    /**
     * An output together with everything it has printed.
     *
     * @param output the output
     * @param out the lines answered, oldest first
     * @param err the notes and problems, oldest first
     */
    public record Collected(Output output, List<String> out, List<String> err) {}

    /**
     * @return an output that collects what it prints, answering in text, which is what the tests use
     */
    public static Collected collecting() {
        return collecting(false, false);
    }

    /**
     * An output that collects what it prints, which is what the tests use.
     *
     * @param json true to answer in JSON
     * @param quiet true to leave the notes out
     * @return the output and the lines it has printed
     */
    public static Collected collecting(boolean json, boolean quiet) {
        List<String> out = Collections.synchronizedList(new ArrayList<>());
        List<String> err = Collections.synchronizedList(new ArrayList<>());
        return new Collected(new Output(out::add, err::add, json, quiet), out, err);
    }

    /**
     * The output a real run uses, over the process's own streams.
     *
     * @param json true to answer in JSON
     * @param quiet true to leave the notes out
     * @return the output
     */
    public static Output console(boolean json, boolean quiet) {
        return console(System.out, System.err, json, quiet);
    }

    /**
     * An output over a pair of streams, each line ending in a line feed.
     *
     * @param stdout where answers go
     * @param stderr where notes and problems go
     * @param json true to answer in JSON
     * @param quiet true to leave the notes out
     * @return the output
     */
    public static Output console(PrintStream stdout, PrintStream stderr, boolean json, boolean quiet) {
        return new Output(line -> write(stdout, line), line -> write(stderr, line), json, quiet);
    }

    /**
     * An output over a pair of writers, each line ending in a line feed. This is
     * the shape a command line framework hands over.
     *
     * @param stdout where answers go
     * @param stderr where notes and problems go
     * @param json true to answer in JSON
     * @param quiet true to leave the notes out
     * @return the output
     */
    public static Output console(PrintWriter stdout, PrintWriter stderr, boolean json, boolean quiet) {
        return new Output(line -> write(stdout, line), line -> write(stderr, line), json, quiet);
    }

    /**
     * @return true when answers are printed as JSON
     */
    public boolean json() {
        return json;
    }

    /**
     * @return true when notes are left out
     */
    public boolean quiet() {
        return quiet;
    }

    /**
     * Print an answer: as JSON when that was asked for, a string as it is, and
     * anything else as JSON on one line.
     *
     * @param value the answer
     */
    public void answer(Object value) {
        if (json) {
            out.accept(Json.pretty(value));
        } else if (value instanceof String text) {
            out.accept(text);
        } else {
            out.accept(Json.stringify(value));
        }
    }

    /**
     * Print an answer: as JSON when that was asked for, and otherwise by handing
     * the value to the writer the caller supplied.
     *
     * @param <T> the type of the answer
     * @param value the answer
     * @param asLines how a person should see it
     */
    public <T> void answer(T value, Function<? super T, ? extends List<String>> asLines) {
        if (json) {
            out.accept(Json.pretty(value));
            return;
        }
        asLines.apply(value).forEach(out);
    }

    /**
     * Print a note, which a quiet run leaves out.
     *
     * @param message the note
     */
    public void note(String message) {
        if (!quiet) {
            err.accept(message);
        }
    }

    /**
     * Print a problem, however quiet the run is.
     *
     * @param message the problem
     */
    public void problem(String message) {
        err.accept(message);
    }

    /**
     * Print a table with every column on the left.
     *
     * @param rows the rows, each a list of cells
     * @throws ValidationException when there are no rows
     */
    public void table(List<? extends List<String>> rows) {
        table(rows, List.of());
    }

    /**
     * Print a table, or its rows as JSON when that was asked for.
     *
     * @param rows the rows, each a list of cells
     * @param align how each column is lined up
     * @throws ValidationException when there are no rows
     */
    public void table(List<? extends List<String>> rows, List<Text.Align> align) {
        if (rows == null) {
            throw new ValidationException("a table needs rows");
        }
        if (json) {
            out.accept(Json.stringify(rows));
            return;
        }
        Text.tabulate(rows, align).forEach(out);
    }

    private static void write(PrintStream stream, String line) {
        stream.print(line + "\n");
        stream.flush();
    }

    private static void write(PrintWriter writer, String line) {
        writer.print(line + "\n");
        writer.flush();
    }
}
