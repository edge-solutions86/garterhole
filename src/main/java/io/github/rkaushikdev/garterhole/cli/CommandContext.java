package io.github.rkaushikdev.garterhole.cli;

import io.github.rkaushikdev.garterhole.Service;
import io.github.rkaushikdev.garterhole.util.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * What a command is handed besides its arguments: the service to ask, the
 * output to answer on, the environment it was run in and the clock that says
 * what day it is. The clock is handed over rather than read, so that filing a
 * performance "today" is something a test can pin to a known day.
 *
 * @param service the service every command goes through
 * @param output where the command prints
 * @param environment the variables the command was run with
 * @param clock what time it is
 */
public record CommandContext(Service service, Output output, Map<String, String> environment, Clock clock) {

    /**
     * Copies the environment, so a command cannot change what the caller holds.
     *
     * @param service the service every command goes through
     * @param output where the command prints
     * @param environment the variables the command was run with
     * @param clock what time it is
     */
    public CommandContext {
        environment = Map.copyOf(environment);
    }

    /**
     * A context with no environment, on the real clock.
     *
     * @param service the service every command goes through
     * @param output where the command prints
     */
    public CommandContext(Service service, Output output) {
        this(service, output, Map.of(), Clock.system());
    }

    /**
     * @return the day it is, in UTC, which is the day a performance filed now is dated
     */
    public LocalDate today() {
        return LocalDate.ofInstant(clock.date(), ZoneOffset.UTC);
    }
}
