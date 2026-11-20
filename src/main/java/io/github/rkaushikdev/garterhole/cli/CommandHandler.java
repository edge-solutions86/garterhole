package io.github.rkaushikdev.garterhole.cli;

/**
 * One command: it reads its arguments, asks the service, and prints on the
 * output it is handed. A handler never exits the process; a refusal is thrown
 * and the program decides what status that becomes.
 */
@FunctionalInterface
public interface CommandHandler {

    /**
     * @param parsed the command line, read against the command's flags
     * @param context the service, the output, the environment and the clock
     */
    void run(ParsedArgs parsed, CommandContext context);
}
