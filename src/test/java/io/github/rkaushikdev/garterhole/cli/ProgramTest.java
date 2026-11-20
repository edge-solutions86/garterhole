package io.github.rkaushikdev.garterhole.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.garterhole.Version;
import io.github.rkaushikdev.garterhole.http.App;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.Logger;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class ProgramTest {

    @TempDir
    Path directory;

    private App app;

    @BeforeEach
    void buildTheApp() {
        app = App.build(
                App.Options.of(Map.of("GARTERHOLE_DATA", directory.toString())).withLogger(Logger.silent()));
    }

    @AfterEach
    void closeTheApp() {
        app.close();
    }

    private Program.Collected call(String line) {
        List<String> argv =
                Arrays.stream(line.split(" ")).filter(word -> !word.isEmpty()).toList();
        return Program.runCollecting(argv, Program.Options.of(Map.of()).withApp(app));
    }

    @Nested
    class HelpAndVersion {

        @Test
        void printsTheHelpWhenNothingIsAskedFor() {
            Program.Collected result = call("");
            assertThat(result.status()).isZero();
            assertThat(String.join("\n", result.out())).contains("usage: garterhole");
        }

        @Test
        void printsTheHelpWhenItIsAskedFor() {
            assertThat(String.join("\n", call("--help").out())).contains("method add");
        }

        @Test
        void listsEveryCommandInTheHelp() {
            String text = String.join("\n", Program.helpLines());
            for (String name : Program.commandNames()) {
                assertThat(text).contains(name);
            }
        }

        @Test
        void printsTheVersion() {
            assertThat(call("--version").out().get(0)).contains(Version.VERSION);
        }
    }

    @Nested
    class FindingACommand {

        @Test
        void findsAOneWordCommand() {
            assertThat(Program.findCommand(List.of("prove"))
                            .orElseThrow()
                            .command()
                            .name())
                    .isEqualTo("prove");
        }

        @Test
        void prefersTheLongerName() {
            assertThat(Program.findCommand(List.of("method", "add"))
                            .orElseThrow()
                            .command()
                            .name())
                    .isEqualTo("method add");
        }

        @Test
        void leavesTheFlagsBehindForTheCommandToRead() {
            assertThat(Program.findCommand(List.of("method", "add", "--name", "Plain"))
                            .orElseThrow()
                            .argv())
                    .containsExactly("--name", "Plain");
        }

        @Test
        void givesNothingForACommandNobodyWrote() {
            assertThat(Program.findCommand(List.of("sing"))).isEmpty();
        }
    }

    @Nested
    class RunningCommands {

        @Test
        void addsAMethodAndListsIt() {
            Program.Collected added = call("method add --name Plain --stage 8 --notation x18x18x18x18,12");
            assertThat(added.status()).isZero();
            Program.Collected listed = call("method list");
            assertThat(String.join("\n", listed.out())).contains("Plain Bob Major");
        }

        @Test
        void provesATouch() {
            call("method add --name Plain --stage 8 --notation x18x18x18x18,12");
            Program.Collected result = call("prove --title Plain Bob Major --touch 3(H)");
            assertThat(result.out().get(1)).isEqualTo("336 changes, touch");
        }

        @Test
        void answersInJsonWhenAsked() {
            call("method add --name Plain --stage 8 --notation x18x18x18x18,12");
            Program.Collected result = call("method list --json");
            assertThat(Json.parse(String.join("\n", result.out()))).hasSize(1);
        }

        @Test
        void reportsACommandThatDoesNotExist() {
            Program.Collected result = call("sing loudly");
            assertThat(result.status()).isEqualTo(2);
            assertThat(result.err().get(0)).contains("there is no command called");
        }

        @Test
        void reportsACommandThatFailedWithoutAStackTrace() {
            Program.Collected result = call("method show --title Nothing Major");
            assertThat(result.status()).isEqualTo(1);
            assertThat(result.err().get(0)).isEqualTo("no method with id \"Nothing Major\"");
        }

        @Test
        void reportsAMissingFlag() {
            Program.Collected result = call("method add --name Plain --stage 8");
            assertThat(result.status()).isEqualTo(1);
            assertThat(result.err().get(0)).contains("needs --notation");
        }

        @Test
        void reportsAFlagNobodyDeclared() {
            Program.Collected result = call("method list --colour blue");
            assertThat(result.status()).isEqualTo(1);
            assertThat(result.err().get(0)).contains("there is no flag called --colour");
        }
    }

    @Nested
    class RunWithoutAPreparedApplication {

        @Test
        void opensItsOwnStoreAndClosesItAgain() {
            Map<String, String> environment =
                    Map.of("GARTERHOLE_DATA", directory.resolve("own").toString());
            int status = Program.run(
                    List.of("method", "list"), Program.Options.of(environment).withOutput(new Output()));
            assertThat(status).isZero();
            assertThat(directory.resolve("own").resolve("lock")).doesNotExist();
        }
    }

    @Nested
    class Serving {

        @Test
        void servesTheDataDirectoryTheProgramWouldOtherwiseHaveLocked() {
            Path data = directory.resolve("served");
            java.util.concurrent.atomic.AtomicReference<App.Running> started =
                    new java.util.concurrent.atomic.AtomicReference<>();
            Program.Options options = Program.Options.of(
                            Map.of("GARTERHOLE_DATA", data.toString(), "GARTERHOLE_LOG_LEVEL", "silent"))
                    .withOutput(new Output())
                    .withListener(environment -> {
                        started.set(App.start(App.Options.of(environment)));
                        return started.get();
                    });
            try {
                assertThat(Program.run(List.of("serve", "--port", "0"), options))
                        .isZero();
                assertThat(started.get().port()).isPositive();
                assertThat(data.resolve("lock")).exists();
            } finally {
                started.get().close();
            }
            assertThat(data.resolve("lock")).doesNotExist();
        }
    }

    /** The same program driven the way the entry point drives it: through picocli, onto its writers. */
    @Nested
    class ThroughPicocli {

        private final StringWriter out = new StringWriter();
        private final StringWriter err = new StringWriter();

        private int execute(String... argv) {
            CommandLine line = Program.commandLine(Program.Options.of(Map.of()).withApp(app));
            line.setOut(new PrintWriter(out, true));
            line.setErr(new PrintWriter(err, true));
            return line.execute(argv);
        }

        @Test
        void buildsACommandTreeFromTheTable() {
            CommandLine line = Program.commandLine(Program.Options.of(Map.of()));
            assertThat(line.getCommandName()).isEqualTo("garterhole");
            assertThat(line.getSubcommands()).containsOnlyKeys("method", "serve", "tower", "prove", "lead", "records");
            assertThat(line.getSubcommands().get("method").getSubcommands())
                    .containsOnlyKeys("add", "list", "show", "remove");
            assertThat(line.getSubcommands().get("records").getSubcommands())
                    .containsOnlyKeys("file", "list", "report", "board");
        }

        @Test
        void runsACommandAndPrintsOnTheCommandLinesWriters() {
            int status = execute("method", "add", "--name", "Plain", "--stage", "8", "--notation", "x18x18x18x18,12");
            assertThat(status).isZero();
            assertThat(out.toString()).startsWith("Plain Bob Major\n");
            assertThat(err.toString()).isEqualTo("filed as Plain Bob Major\n");
        }

        @Test
        void exitsWithTwoOnTheErrorWriterForALineThatNamesNoCommand() {
            assertThat(execute("method")).isEqualTo(2);
            assertThat(err.toString()).isEqualTo("there is no command called \"method\"\n");
            assertThat(out.toString()).isEmpty();
        }

        @Test
        void readsFlagsWrittenBeforeTheCommand() {
            execute("method", "add", "--name", "Plain", "--stage", "8", "--notation", "x18x18x18x18,12");
            out.getBuffer().setLength(0);
            assertThat(execute("--json", "method", "list")).isZero();
            assertThat(Json.parse(out.toString())).hasSize(1);
        }

        @Test
        void keepsADoubleDashAndAnAtSignForTheCommandToRead() {
            assertThat(execute("method", "show", "--title", "@library", "Major"))
                    .isEqualTo(1);
            assertThat(err.toString()).isEqualTo("no method with id \"@library Major\"\n");
        }
    }
}
