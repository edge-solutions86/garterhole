package io.github.rkaushikdev.garterhole.util;

import io.github.rkaushikdev.garterhole.GarterholeException;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.config.Config;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Logging.
 *
 * <p>One line per event, as JSON, with the fields the reader will want to
 * filter on at the front. A tower running this reads the log with
 * {@code grep}, and an association running several of them feeds it into
 * something that wants structure, so JSON serves both.
 *
 * <p>The logger takes the sink and the clock rather than reaching for the
 * console and the wall clock. That is what makes the tests able to assert on
 * what was logged, and what lets the HTTP layer write a request log that a
 * caller can turn off.
 */
public final class Logger {

    private static final String SILENT = "silent";

    private final String level;
    private final Consumer<String> sink;
    private final Supplier<Instant> now;
    private final Map<String, Object> fields;

    /** A logger at {@code info} that writes nowhere. */
    public Logger() {
        this("info", line -> {}, Instant::now, Map.of());
    }

    /**
     * @param level the quietest level written
     * @param sink where each line goes
     */
    public Logger(String level, Consumer<String> sink) {
        this(level, sink, Instant::now, Map.of());
    }

    /**
     * @param level the quietest level written
     * @param sink where each line goes
     * @param now the time each line is stamped with
     * @param fields written on every line
     * @throws ValidationException when the level is not one of {@link Config#LOG_LEVELS}
     */
    public Logger(String level, Consumer<String> sink, Supplier<Instant> now, Map<String, ?> fields) {
        requireLevel(level);
        this.level = level;
        this.sink = sink;
        this.now = now;
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    /**
     * @return a logger that writes nothing, for the code paths that want one and do not care
     */
    public static Logger silent() {
        return new Logger(SILENT, line -> {});
    }

    /**
     * A logger that collects its lines at {@code debug}, stamping each with the
     * epoch, which is what the tests use.
     *
     * @return the logger and the lines it has written
     */
    public static Collecting collecting() {
        return collecting("debug", () -> Instant.EPOCH);
    }

    /**
     * A logger that collects its lines, which is what the tests use.
     *
     * @param level the quietest level written
     * @param now the time each line is stamped with
     * @return the logger and the lines it has written
     */
    public static Collecting collecting(String level, Supplier<Instant> now) {
        List<String> lines = Collections.synchronizedList(new ArrayList<>());
        return new Collecting(new Logger(level, lines::add, now, Map.of()), lines);
    }

    /**
     * @return the quietest level this logger writes
     */
    public String level() {
        return level;
    }

    /**
     * A logger that writes the same fields on every line, on top of this one's.
     *
     * @param more the fields to add
     * @return the new logger, with this one's level and sink
     */
    public Logger with(Map<String, ?> more) {
        Map<String, Object> merged = new LinkedHashMap<>(fields);
        merged.putAll(more);
        return new Logger(level, sink, now, merged);
    }

    /**
     * @param wanted a level
     * @return true when a line at that level would be written
     * @throws ValidationException when the level is not one of {@link Config#LOG_LEVELS}
     */
    public boolean writes(String wanted) {
        requireLevel(wanted);
        if (SILENT.equals(level)) {
            return false;
        }
        return Config.LOG_LEVELS.indexOf(wanted) >= Config.LOG_LEVELS.indexOf(level);
    }

    /**
     * Write a line.
     *
     * @param wanted the level of the line
     * @param message what happened
     * @param extra fields for this line only, which win over the carried ones
     * @return true when the line was written
     */
    public boolean log(String wanted, String message, Map<String, ?> extra) {
        if (!writes(wanted)) {
            return false;
        }
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("at", Clock.isoString(now.get()));
        line.put("level", wanted);
        line.put("message", message);
        line.putAll(fields);
        line.putAll(extra);
        line.values().removeIf(java.util.Objects::isNull);
        sink.accept(Json.stringify(line));
        return true;
    }

    /**
     * @param message what happened
     * @return true when the line was written
     */
    public boolean debug(String message) {
        return log("debug", message, Map.of());
    }

    /**
     * @param message what happened
     * @param extra fields for this line only
     * @return true when the line was written
     */
    public boolean debug(String message, Map<String, ?> extra) {
        return log("debug", message, extra);
    }

    /**
     * @param message what happened
     * @return true when the line was written
     */
    public boolean info(String message) {
        return log("info", message, Map.of());
    }

    /**
     * @param message what happened
     * @param extra fields for this line only
     * @return true when the line was written
     */
    public boolean info(String message, Map<String, ?> extra) {
        return log("info", message, extra);
    }

    /**
     * @param message what happened
     * @return true when the line was written
     */
    public boolean warn(String message) {
        return log("warn", message, Map.of());
    }

    /**
     * @param message what happened
     * @param extra fields for this line only
     * @return true when the line was written
     */
    public boolean warn(String message, Map<String, ?> extra) {
        return log("warn", message, extra);
    }

    /**
     * @param message what happened
     * @return true when the line was written
     */
    public boolean error(String message) {
        return error(message, null, Map.of());
    }

    /**
     * @param message what happened
     * @param error the failure, or anything else that describes it
     * @return true when the line was written
     */
    public boolean error(String message, Object error) {
        return error(message, error, Map.of());
    }

    /**
     * Write an error, unpacking an exception into fields rather than writing
     * its whole stack.
     *
     * @param message what happened
     * @param error the failure, anything else that describes it, or {@code null}
     * @param extra fields for this line only
     * @return true when the line was written
     */
    public boolean error(String message, Object error, Map<String, ?> extra) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (error instanceof Throwable failure) {
            details.put("error", failure.getMessage());
            if (failure instanceof GarterholeException ours) {
                details.put("errorName", ours.name());
                details.put("code", ours.code());
            } else {
                details.put("errorName", failure.getClass().getSimpleName());
            }
        } else if (error != null) {
            details.put("error", String.valueOf(error));
        }
        details.putAll(extra);
        return log("error", message, details);
    }

    private static void requireLevel(String level) {
        if (!Config.LOG_LEVELS.contains(level)) {
            throw new ValidationException(Json.quote(level) + " is not a log level");
        }
    }

    /**
     * A logger together with the lines it has written.
     *
     * @param logger the logger
     * @param lines every line written so far, oldest first
     */
    public record Collecting(Logger logger, List<String> lines) {

        /**
         * @return every line written so far, read back as documents
         */
        public List<com.fasterxml.jackson.databind.JsonNode> records() {
            synchronized (lines) {
                return lines.stream().map(Json::parse).toList();
            }
        }
    }
}
