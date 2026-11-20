package io.github.rkaushikdev.garterhole.cli;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * What a command says about one of its flags: whether it takes a value, and
 * the single letter it may be written with.
 *
 * @param takesValue true for a flag written {@code --stage 8} or {@code --stage=8},
 *     false for one written on its own, such as {@code --json}
 * @param shortName the letter for the short form, for example {@code s} for {@code -s 8}
 */
public record FlagSpec(boolean takesValue, Optional<String> shortName) {

    /** A flag written on its own, with no short form. */
    public static final FlagSpec SWITCH = new FlagSpec(false, Optional.empty());

    /** A flag that takes a value, with no short form. */
    public static final FlagSpec VALUE = new FlagSpec(true, Optional.empty());

    /**
     * @param letter the short form
     * @return a flag that takes a value and may be written as {@code -letter}
     */
    public static FlagSpec value(String letter) {
        return new FlagSpec(true, Optional.of(letter));
    }

    /**
     * The flags a command declares, in the order they are given.
     *
     * @param namesAndSpecs each flag's name followed by its spec
     * @return the flags, as a map that cannot be changed
     * @throws IllegalArgumentException when the arguments do not pair up
     */
    public static Map<String, FlagSpec> flags(Object... namesAndSpecs) {
        if (namesAndSpecs.length % 2 != 0) {
            throw new IllegalArgumentException("every flag needs a name and a spec");
        }
        Map<String, FlagSpec> flags = new LinkedHashMap<>();
        for (int index = 0; index < namesAndSpecs.length; index += 2) {
            if (!(namesAndSpecs[index] instanceof String name)
                    || !(namesAndSpecs[index + 1] instanceof FlagSpec spec)) {
                throw new IllegalArgumentException("every flag needs a name and a spec");
            }
            flags.put(name, spec);
        }
        return Collections.unmodifiableMap(flags);
    }
}
