package io.github.rkaushikdev.garterhole.config;

import io.github.rkaushikdev.garterhole.ConfigException;
import io.github.rkaushikdev.garterhole.GarterholeException;
import io.github.rkaushikdev.garterhole.validation.Coerce;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Where the settings come from and what they mean.
 *
 * <p>Every setting has a default that works, so the service starts with no
 * configuration at all. That is worth insisting on: a tower installs this on a
 * spare machine in the vestry and nobody is going to write a configuration file
 * before finding out whether it runs.
 *
 * <p>Anything wrong in the environment is fatal at start up rather than at the
 * first request, because a service running with a port it did not want is
 * harder to notice than one that refused to start.
 *
 * <p>Port zero is allowed and means what it means everywhere else: let the
 * operating system choose one. That is what the tests use, and what a tower
 * running several copies behind a proxy wants.
 *
 * @param port the port to listen on, zero for any
 * @param host the address to listen on
 * @param dataDirectory where the journal, the snapshot and the lock live
 * @param logLevel the quietest level the log writes
 * @param requestsPerMinute how many requests one caller may make in a minute
 * @param readOnly whether the service refuses writes
 * @param journalPath the journal inside the data directory
 * @param snapshotPath the snapshot inside the data directory
 * @param lockPath the lock file inside the data directory
 */
public record Config(
        int port,
        String host,
        String dataDirectory,
        String logLevel,
        int requestsPerMinute,
        boolean readOnly,
        String journalPath,
        String snapshotPath,
        String lockPath) {

    /** The log levels, quietest last. */
    public static final List<String> LOG_LEVELS = List.of("debug", "info", "warn", "error", "silent");

    private static final int HIGHEST_PORT = 65535;

    /** The settings, their defaults and how their text is read, in the order they are documented. */
    private static final List<SettingDefinition> SETTINGS = List.of(
            new SettingDefinition("port", "GARTERHOLE_PORT", 8080, text -> Coerce.toInteger(text, "GARTERHOLE_PORT")),
            new SettingDefinition("host", "GARTERHOLE_HOST", "127.0.0.1", text -> text),
            new SettingDefinition("dataDirectory", "GARTERHOLE_DATA", "data", text -> text),
            new SettingDefinition("logLevel", "GARTERHOLE_LOG_LEVEL", "info", Config::readLevel),
            new SettingDefinition(
                    "requestsPerMinute",
                    "GARTERHOLE_RATE_LIMIT",
                    120,
                    text -> Coerce.toInteger(text, "GARTERHOLE_RATE_LIMIT")),
            new SettingDefinition(
                    "readOnly", "GARTERHOLE_READ_ONLY", false, text -> Coerce.toBoolean(text, "GARTERHOLE_READ_ONLY")));

    /**
     * @return the settings with nothing set, which are the defaults
     */
    public static Config load() {
        return load(Map.of());
    }

    /**
     * Read the settings out of an environment. A variable that is missing or
     * empty takes its default.
     *
     * @param environment the variables, usually the process environment
     * @return the settings
     * @throws ConfigException naming the variable that is wrong
     */
    public static Config load(Map<String, String> environment) {
        Map<String, Object> settings = new LinkedHashMap<>();
        for (SettingDefinition setting : SETTINGS) {
            String text = environment.get(setting.variable());
            if (text == null || text.isEmpty()) {
                settings.put(setting.name(), setting.fallback());
                continue;
            }
            try {
                settings.put(setting.name(), setting.read().apply(text));
            } catch (GarterholeException failure) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("variable", setting.variable());
                details.put("value", text);
                throw new ConfigException(setting.variable() + " is wrong: " + failure.getMessage(), details);
            }
        }
        long port = ((Number) settings.get("port")).longValue();
        if (port < 0 || port > HIGHEST_PORT) {
            throw new ConfigException("GARTERHOLE_PORT must be a port number, not " + port);
        }
        long requestsPerMinute = ((Number) settings.get("requestsPerMinute")).longValue();
        if (requestsPerMinute < 1) {
            throw new ConfigException(
                    "GARTERHOLE_RATE_LIMIT must allow at least one request, not " + requestsPerMinute);
        }
        String dataDirectory = (String) settings.get("dataDirectory");
        return new Config(
                (int) port,
                (String) settings.get("host"),
                dataDirectory,
                (String) settings.get("logLevel"),
                // A limit beyond what an int holds is no limit at all, so it is held at the largest one.
                (int) Math.min(requestsPerMinute, Integer.MAX_VALUE),
                (Boolean) settings.get("readOnly"),
                Path.of(dataDirectory, "journal.jsonl").toString(),
                Path.of(dataDirectory, "snapshot.json").toString(),
                Path.of(dataDirectory, "lock").toString());
    }

    /**
     * @param newPort the port actually listened on
     * @return these settings with the port replaced
     */
    public Config withPort(int newPort) {
        return new Config(
                newPort,
                host,
                dataDirectory,
                logLevel,
                requestsPerMinute,
                readOnly,
                journalPath,
                snapshotPath,
                lockPath);
    }

    /**
     * The settings written out for the log line at start up. Nothing here is
     * secret, but the habit of listing what is printed rather than printing the
     * whole record is worth keeping.
     *
     * @return one line describing the settings
     */
    public String describe() {
        return String.join(
                ", ",
                "listening on " + host + ":" + port,
                "data in " + dataDirectory,
                "log level " + logLevel,
                readOnly ? "read only" : "accepting writes");
    }

    /**
     * @return every setting, the variable it reads and its default
     */
    public static List<Setting> settingsList() {
        return SETTINGS.stream()
                .map(setting -> new Setting(setting.name(), setting.variable(), setting.fallback()))
                .toList();
    }

    private static String readLevel(String text) {
        String level = text.toLowerCase(Locale.ROOT);
        if (!LOG_LEVELS.contains(level)) {
            throw new ConfigException("must be one of " + String.join(", ", LOG_LEVELS));
        }
        return level;
    }

    /**
     * One setting, as the documentation lists it.
     *
     * @param name the name of the setting in {@link Config}
     * @param variable the environment variable it is read from
     * @param fallback the value it takes when the variable is not set
     */
    public record Setting(String name, String variable, Object fallback) {}

    private record SettingDefinition(String name, String variable, Object fallback, Function<String, Object> read) {}
}
