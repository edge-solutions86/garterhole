package io.github.rkaushikdev.garterhole.cli.commands;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.cli.FlagSpec;
import io.github.rkaushikdev.garterhole.cli.Output;
import io.github.rkaushikdev.garterhole.cli.ParsedArgs;
import io.github.rkaushikdev.garterhole.config.Config;
import io.github.rkaushikdev.garterhole.http.App;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Starting the service from the command line.
 *
 * <p>This was once a branch inside the dispatcher, which meant the one command
 * that does not return was the one command with no test. It is a command like
 * the others, and what makes it testable is that it gives back the handle
 * rather than waiting for ever: the caller decides whether to hold the process
 * open.
 */
public final class ServeCommand {

    /** The flags this command takes. */
    public static final Map<String, FlagSpec> SERVE_FLAGS = FlagSpec.flags(
            "port", FlagSpec.value("p"),
            "host", FlagSpec.VALUE,
            "data", FlagSpec.value("d"),
            "json", FlagSpec.SWITCH,
            "quiet", FlagSpec.SWITCH);

    private ServeCommand() {}

    /** How the service is started, so that a test need not open a port. */
    @FunctionalInterface
    public interface Listener {
        /**
         * @param environment the settings to start with
         * @return the running service
         */
        App.Running listen(Map<String, String> environment);
    }

    /**
     * @return the listener that really starts the service
     */
    public static Listener startingTheService() {
        return environment -> App.start(App.Options.of(environment));
    }

    /**
     * The environment the settings should be read from, with anything named on
     * the command line laid over it.
     *
     * @param parsed the command line
     * @param environment the variables the command was run with
     * @return the variables with the flags laid over them
     */
    public static Map<String, String> environmentFor(ParsedArgs parsed, Map<String, String> environment) {
        Map<String, String> combined = new LinkedHashMap<>(environment);
        parsed.valueOf("port").ifPresent(port -> combined.put("GARTERHOLE_PORT", port));
        parsed.valueOf("host").ifPresent(host -> combined.put("GARTERHOLE_HOST", host));
        parsed.valueOf("data").ifPresent(data -> combined.put("GARTERHOLE_DATA", data));
        return combined;
    }

    /**
     * {@code serve --port 8080 --data ./data}
     *
     * <p>The settings are read before anything is opened, so a port that is not
     * one is refused without a store being touched.
     *
     * @param parsed the command line
     * @param output where to say where the service is listening
     * @param environment the variables the command was run with
     * @param listener what starts the service
     * @return the running service
     * @throws ValidationException when there is nowhere to write to
     */
    public static App.Running serve(
            ParsedArgs parsed, Output output, Map<String, String> environment, Listener listener) {
        if (output == null) {
            throw new ValidationException("serving needs somewhere to write to");
        }
        Map<String, String> combined = environmentFor(parsed, environment == null ? Map.of() : environment);
        Config config = Config.load(combined);
        App.Running running = listener.listen(combined);
        output.note(config.withPort(running.port()).describe());
        output.answer(Json.object().put("port", running.port()).put("dataDirectory", config.dataDirectory()));
        return running;
    }
}
