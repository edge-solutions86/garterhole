package io.github.rkaushikdev.garterhole.cli;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A command line once it has been read: the words naming the command, the
 * flags, and whatever was left over.
 *
 * <p>A flag written on its own is held as an empty value, and one written with
 * text holds the text exactly, so {@code --quiet=true} stays the word
 * {@code true} rather than becoming a switch.
 *
 * @param command the words before the first flag, for example {@code method add}
 * @param flags every flag given, by name, in the order they were given
 * @param rest the words after the first flag that no flag took, and everything after {@code --}
 */
public record ParsedArgs(List<String> command, Map<String, Optional<String>> flags, List<String> rest) {

    /**
     * Copies everything, so a parsed line cannot be changed afterwards.
     *
     * @param command the words naming the command
     * @param flags the flags, by name
     * @param rest the words left over
     */
    public ParsedArgs {
        command = List.copyOf(command);
        flags = Collections.unmodifiableMap(new LinkedHashMap<>(flags));
        rest = List.copyOf(rest);
    }

    /**
     * @param name a flag
     * @return true when the flag was given at all, with a value or without
     */
    public boolean has(String name) {
        return flags.containsKey(name);
    }

    /**
     * @param name a flag
     * @return the text written with the flag, empty when the flag was not given
     *     or was written on its own
     */
    public Optional<String> valueOf(String name) {
        Optional<String> value = flags.get(name);
        return value == null ? Optional.empty() : value;
    }

    /**
     * The flag as text whenever it was given: its value, or the word
     * {@code true} for a flag written on its own.
     *
     * @param name a flag
     * @return the text, empty only when the flag was not given
     */
    public Optional<String> asText(String name) {
        Optional<String> value = flags.get(name);
        return value == null ? Optional.empty() : Optional.of(value.orElse("true"));
    }
}
