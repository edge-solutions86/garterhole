package io.github.rkaushikdev.garterhole.cli;

import io.github.rkaushikdev.garterhole.cli.commands.ServeCommand;
import io.github.rkaushikdev.garterhole.http.App;
import java.util.List;

/**
 * The {@code garterhole} entry point.
 *
 * <p>It does three things and nothing else: run the command line against the
 * process environment, keep the process alive while the service is serving,
 * and exit with the status the command gave back. Everything that could fail
 * lives in {@link Program}, where it can be tested.
 *
 * <p>A command that finishes leaves nothing running, so the process ends on its
 * own with status zero. {@code serve} leaves the service listening, and the
 * process stays up until it is stopped; stopping it closes the service, which
 * saves the store and gives up the lock on the data directory.
 */
public final class Main {

    private Main() {}

    /**
     * @param args the command line, without the program name
     */
    public static void main(String[] args) {
        ServeCommand.Listener closingOnShutdown = environment -> {
            App.Running running = App.start(App.Options.of(environment));
            Runtime.getRuntime().addShutdownHook(new Thread(running::close, "garterhole-shutdown"));
            return running;
        };
        int status =
                Program.run(List.of(args), Program.Options.of(System.getenv()).withListener(closingOnShutdown));
        if (status != 0) {
            System.exit(status);
        }
    }
}
