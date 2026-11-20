package io.github.rkaushikdev.garterhole.cli;

import io.github.rkaushikdev.garterhole.GarterholeException;
import io.github.rkaushikdev.garterhole.Version;
import io.github.rkaushikdev.garterhole.cli.commands.MethodCommands;
import io.github.rkaushikdev.garterhole.cli.commands.ProveCommands;
import io.github.rkaushikdev.garterhole.cli.commands.RecordCommands;
import io.github.rkaushikdev.garterhole.cli.commands.ServeCommand;
import io.github.rkaushikdev.garterhole.cli.commands.TowerCommands;
import io.github.rkaushikdev.garterhole.http.App;
import io.github.rkaushikdev.garterhole.util.Clock;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.Text;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.PositionalParamSpec;
import picocli.CommandLine.ParseResult;

/**
 * The command line.
 *
 * <p>Every command is a name, a line of help, its flags and what it does. The
 * table below is the single statement of all of them: picocli's command tree is
 * built from it, so {@code garterhole method add} is a real subcommand, and the
 * help, the documentation and the dispatcher all read the same list. A new
 * command is one entry in the table and a test.
 *
 * <p>picocli decides which command a line names. The flags are then read by
 * the tool's own reader, {@link Args}, because its grammar is part of what the
 * tool promises: a method title typed without quotes is joined back together,
 * and an unknown flag is refused with a message that names it.
 *
 * <p>Nothing here exits the process. {@link #run} gives back a status and the
 * entry point decides what to do with it, which is what lets the tests run
 * every command in the same process. A refused command answers 1, a line that
 * names no command answers 2, and help and the version answer 0.
 */
public final class Program {

    /** The flags every command understands. */
    static final Map<String, FlagSpec> GLOBAL_FLAGS = FlagSpec.flags(
            "json", FlagSpec.SWITCH, "quiet", FlagSpec.SWITCH, "help", FlagSpec.SWITCH, "version", FlagSpec.SWITCH);

    /** Every command, in the order the help lists them. */
    private static final List<Command> COMMANDS = List.of(
            service(
                    "method add",
                    "add a method from its name, stage and notation",
                    MethodCommands.METHOD_FLAGS,
                    MethodCommands::addMethod),
            service("method list", "list the method library", MethodCommands.METHOD_FLAGS, MethodCommands::listMethods),
            service("method show", "describe one method", MethodCommands.METHOD_FLAGS, MethodCommands::showMethod),
            service(
                    "method remove",
                    "take a method out of the library",
                    MethodCommands.METHOD_FLAGS,
                    MethodCommands::removeMethod),
            new Command("serve", "start the HTTP service", ServeCommand.SERVE_FLAGS, new Serves()),
            service(
                    "tower add",
                    "add a tower from its name and its bell weights",
                    TowerCommands.TOWER_FLAGS,
                    TowerCommands::addTower),
            service("tower list", "list the towers", TowerCommands.TOWER_FLAGS, TowerCommands::listTowers),
            service("tower show", "describe one tower", TowerCommands.TOWER_FLAGS, TowerCommands::showTower),
            service(
                    "prove",
                    "ring a touch and say whether it is true",
                    ProveCommands.PROVE_FLAGS,
                    ProveCommands::prove),
            service("lead", "print one lead of a method", ProveCommands.PROVE_FLAGS, ProveCommands::printLead),
            service("records file", "file a performance", RecordCommands.RECORD_FLAGS, RecordCommands::fileRecord),
            service("records list", "list the performances", RecordCommands.RECORD_FLAGS, RecordCommands::listRecords),
            service(
                    "records report",
                    "write a report of the performances",
                    RecordCommands.RECORD_FLAGS,
                    RecordCommands::reportRecords),
            service(
                    "records board",
                    "print a performance as a board",
                    RecordCommands.RECORD_FLAGS,
                    RecordCommands::printBoard));

    private Program() {}

    /**
     * One command in the table.
     *
     * @param name the words that name it, for example {@code method add}
     * @param help one line saying what it does
     * @param flags the flags it takes, besides the ones every command takes
     * @param action what it does
     */
    public record Command(String name, String help, Map<String, FlagSpec> flags, Action action) {}

    /** What a command does once its flags are read. */
    public sealed interface Action permits UsesService, Serves {}

    /**
     * A command that asks the service something.
     *
     * @param handler the command
     */
    public record UsesService(CommandHandler handler) implements Action {}

    /** The command that starts the service, which needs no service of its own. */
    public record Serves() implements Action {}

    /**
     * The command a line names, and what is left of the line for it to read.
     *
     * @param command the command
     * @param argv the line with the words that name the command taken out
     */
    public record Found(Command command, List<String> argv) {}

    /**
     * What a run prints and how many lines of each.
     *
     * @param status the status to exit with
     * @param out what was printed as answers
     * @param err what was printed as notes and problems
     */
    public record Collected(int status, List<String> out, List<String> err) {}

    /**
     * How a run is set up. Anything left out is made the way a real run makes it:
     * the output on the command line's own streams, the application built from
     * the environment, and the service really started by {@code serve}.
     *
     * @param environment the variables the run sees
     * @param output where it prints, or {@code null} for the command line's streams
     * @param app an application already built, or {@code null} to build one per command
     * @param listener how {@code serve} starts the service
     * @param clock what time it is
     */
    public record Options(
            Map<String, String> environment, Output output, App app, ServeCommand.Listener listener, Clock clock) {

        /**
         * Copies the environment so a run cannot change what the caller holds.
         *
         * @param environment the variables the run sees
         * @param output where it prints
         * @param app an application already built
         * @param listener how {@code serve} starts the service
         * @param clock what time it is
         */
        public Options {
            environment = Map.copyOf(environment);
        }

        /**
         * @param environment the variables the run sees
         * @return a run set up the way a real one is
         */
        public static Options of(Map<String, String> environment) {
            return new Options(environment, null, null, ServeCommand.startingTheService(), Clock.system());
        }

        /**
         * @param given where the run prints
         * @return these options printing there
         */
        public Options withOutput(Output given) {
            return new Options(environment, given, app, listener, clock);
        }

        /**
         * @param prepared an application already built, which the run does not close
         * @return these options using it
         */
        public Options withApp(App prepared) {
            return new Options(environment, output, prepared, listener, clock);
        }

        /**
         * @param starting how {@code serve} starts the service
         * @return these options starting it that way
         */
        public Options withListener(ServeCommand.Listener starting) {
            return new Options(environment, output, app, starting, clock);
        }

        /**
         * @param time what time it is
         * @return these options on that clock
         */
        public Options withClock(Clock time) {
            return new Options(environment, output, app, listener, time);
        }
    }

    /**
     * Find the command a line names.
     *
     * @param argv the line
     * @return the command and the line without the words that name it, when the
     *     line names one
     */
    public static Optional<Found> findCommand(List<String> argv) {
        try {
            return found(commandLine(Options.of(Map.of())).parseArgs(argv.toArray(String[]::new)));
        } catch (CommandLine.ParameterException unreadable) {
            return Optional.empty();
        }
    }

    /**
     * @return the help text, one line per entry
     */
    public static List<String> helpLines() {
        int width = COMMANDS.stream()
                .mapToInt(command -> command.name().length())
                .max()
                .orElse(0);
        List<String> lines = new ArrayList<>();
        lines.add(Version.buildLabel() + " - tooling for change ringing bands");
        lines.add("");
        lines.add("usage: garterhole <command> [flags]");
        lines.add("");
        COMMANDS.forEach(command -> lines.add("  " + Text.padRight(command.name(), width) + "  " + command.help()));
        lines.add("");
        lines.add("flags: --json for machine readable output, --quiet to say less");
        return lines;
    }

    /**
     * @return every command name, in the order the help lists them
     */
    public static List<String> commandNames() {
        return COMMANDS.stream().map(Command::name).toList();
    }

    /**
     * Run a command line through picocli.
     *
     * @param argv the arguments, without the program name
     * @param options how the run is set up
     * @return the status to exit with
     */
    public static int run(List<String> argv, Options options) {
        return commandLine(options).execute(argv.toArray(String[]::new));
    }

    /**
     * Run a command and collect what it printed, which is what the tests use.
     *
     * @param argv the arguments
     * @param options how the run is set up; its output is replaced
     * @return the status and the lines printed
     */
    public static Collected runCollecting(List<String> argv, Options options) {
        boolean quiet = Args.flagIsSet(
                Args.parseArgs(
                        argv.stream().filter(word -> word.startsWith("--")).toList()),
                "quiet");
        Output.Collected collected = Output.collecting(argv.contains("--json"), quiet);
        int status = run(argv, options.withOutput(collected.output()));
        return new Collected(status, collected.out(), collected.err());
    }

    /**
     * The picocli command tree, built from the command table. Its execution
     * prints on the options' output, or on the tree's own streams when there is
     * none, and gives back the status a run exits with.
     *
     * @param options how runs of this tree are set up
     * @return the command line, ready to execute
     */
    public static CommandLine commandLine(Options options) {
        CommandLine root = new CommandLine(group("garterhole"));
        for (Command command : COMMANDS) {
            String[] words = command.name().split(" ");
            CommandLine parent = root;
            for (int index = 0; index < words.length - 1; index += 1) {
                CommandLine existing = parent.getSubcommands().get(words[index]);
                if (existing == null) {
                    existing = new CommandLine(group(words[index]));
                    parent.addSubcommand(words[index], existing);
                }
                parent = existing;
            }
            CommandSpec leaf = CommandSpec.wrapWithoutInspection(command).name(words[words.length - 1]);
            leaf.addPositional(anyWords());
            leaf.usageMessage().description(command.help());
            parent.addSubcommand(words[words.length - 1], new CommandLine(leaf));
        }
        // The flags are read by Args, so picocli treats every word it does not
        // route on as a word for the command, and expands nothing.
        root.setUnmatchedOptionsArePositionalParams(true);
        root.setExpandAtFiles(false);
        root.setExecutionStrategy(parsed -> execute(parsed, options, root));
        root.setParameterExceptionHandler((refused, args) -> execute(null, options, root, List.of(args)));
        return root;
    }

    private static int execute(ParseResult parsed, Options options, CommandLine root) {
        return execute(parsed, options, root, parsed.originalArgs());
    }

    private static int execute(ParseResult parsed, Options options, CommandLine root, List<String> argv) {
        Output output = options.output() != null
                ? options.output()
                : Output.console(root.getOut(), root.getErr(), argv.contains("--json"), argv.contains("--quiet"));
        if (argv.isEmpty() || argv.contains("--help")) {
            helpLines().forEach(output::answer);
            return 0;
        }
        if (argv.contains("--version")) {
            output.answer(Version.buildLabel());
            return 0;
        }
        Optional<Found> found = parsed == null ? Optional.empty() : found(parsed);
        if (found.isEmpty()) {
            output.problem("there is no command called " + Json.quote(String.join(" ", argv)));
            return 2;
        }
        return runCommand(found.get(), output, options);
    }

    private static int runCommand(Found found, Output output, Options options) {
        App own = null;
        try {
            ParsedArgs parsed =
                    Args.parseArgs(found.argv(), withGlobalFlags(found.command().flags()));
            switch (found.command().action()) {
                case Serves serves -> ServeCommand.serve(parsed, output, options.environment(), options.listener());
                case UsesService uses -> {
                    App app = options.app();
                    if (app == null) {
                        own = App.build(App.Options.of(options.environment()));
                        app = own;
                    }
                    uses.handler()
                            .run(
                                    parsed,
                                    new CommandContext(app.service(), output, options.environment(), options.clock()));
                }
            }
            return 0;
        } catch (GarterholeException refused) {
            output.problem(refused.getMessage());
            return 1;
        } catch (RuntimeException failure) {
            output.problem(String.valueOf(failure.getMessage()));
            return 1;
        } finally {
            if (own != null) {
                own.close();
            }
        }
    }

    /** The deepest command picocli routed to, and the line with its name taken out. */
    private static Optional<Found> found(ParseResult parsed) {
        ParseResult deepest = parsed;
        while (deepest.hasSubcommand()) {
            deepest = deepest.subcommand();
        }
        if (!(deepest.commandSpec().userObject() instanceof Command command)) {
            return Optional.empty();
        }
        List<String> remaining = new ArrayList<>(parsed.originalArgs());
        for (String word : command.name().split(" ")) {
            remaining.remove(word);
        }
        return Optional.of(new Found(command, List.copyOf(remaining)));
    }

    private static CommandSpec group(String name) {
        CommandSpec spec = CommandSpec.create().name(name);
        spec.addPositional(anyWords());
        return spec;
    }

    private static PositionalParamSpec anyWords() {
        return PositionalParamSpec.builder()
                .index("0..*")
                .arity("0..*")
                .type(String[].class)
                .paramLabel("WORD")
                .build();
    }

    private static Map<String, FlagSpec> withGlobalFlags(Map<String, FlagSpec> flags) {
        Map<String, FlagSpec> merged = new LinkedHashMap<>(GLOBAL_FLAGS);
        merged.putAll(flags);
        return merged;
    }

    private static Command service(String name, String help, Map<String, FlagSpec> flags, CommandHandler handler) {
        return new Command(name, help, flags, new UsesService(handler));
    }
}
