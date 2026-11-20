package io.github.rkaushikdev.garterhole.cli;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.validation.Coerce;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reading a command line.
 *
 * <p>The shape is the one every tool this replaces uses: a command, then
 * flags, then whatever is left over. {@code garterhole method add --stage 8
 * --name Plain} reads as the command {@code method add} with two flags.
 *
 * <p>A flag that takes no value is written on its own. A flag that takes one
 * is written either as {@code --stage 8} or as {@code --stage=8}, because both
 * are what people type and refusing one of them wins nothing.
 *
 * <p>An unknown flag is an error rather than being ignored. A typed flag that
 * is silently dropped is how somebody ends up believing they filed a peal with
 * a conductor when they did not.
 */
public final class Args {

    private Args() {}

    /**
     * Read a command line without knowing its flags, so any flag is taken and
     * only one written with {@code =} has a value.
     *
     * @param argv the arguments, without the program name
     * @return the command, the flags and the rest
     * @throws ValidationException when there is no list of arguments
     */
    public static ParsedArgs parseArgs(List<String> argv) {
        return read(argv, null);
    }

    /**
     * Read a command line against the flags a command declares.
     *
     * @param argv the arguments, without the program name
     * @param known every flag the command takes, by name
     * @return the command, the flags and the rest
     * @throws ValidationException naming a flag nobody declared or one missing its value
     */
    public static ParsedArgs parseArgs(List<String> argv, Map<String, FlagSpec> known) {
        return read(argv, known);
    }

    /**
     * A flag as a string, with a complaint naming it when it is missing.
     *
     * @param parsed the command line
     * @param name the flag
     * @return its value
     * @throws ValidationException when it was not given with a value
     */
    public static String requireFlag(ParsedArgs parsed, String name) {
        return parsed.valueOf(name).orElseThrow(() -> needs(name));
    }

    /**
     * A flag as a string, falling back when it is missing.
     *
     * @param parsed the command line
     * @param name the flag
     * @param fallback what to use when it was not given with a value
     * @return its value, or the fallback
     */
    public static String requireFlag(ParsedArgs parsed, String name, String fallback) {
        return parsed.valueOf(name).orElse(fallback);
    }

    /**
     * A flag whose value is a phrase, taking the words that followed it as well.
     *
     * <p>A method title has spaces in it, and a ringer who forgets to quote it
     * types {@code --title Cambridge Surprise Major}. The shell hands over three
     * words, the flag takes the first and the other two arrive as leftovers, so
     * they are joined back together rather than the command refusing something
     * that was perfectly clear. Only one flag in a command may do this, since
     * there is one pile of leftovers to go round.
     *
     * @param parsed the command line
     * @param name the flag
     * @return the phrase
     * @throws ValidationException when there is neither a value nor any leftovers
     */
    public static String phraseFlag(ParsedArgs parsed, String name) {
        return phrase(parsed, name).orElseThrow(() -> needs(name));
    }

    /**
     * A phrase flag, falling back when there is nothing at all.
     *
     * @param parsed the command line
     * @param name the flag
     * @param fallback what to use when there is neither a value nor any leftovers
     * @return the phrase, or the fallback
     */
    public static String phraseFlag(ParsedArgs parsed, String name, String fallback) {
        return phrase(parsed, name).orElse(fallback);
    }

    /**
     * A flag as a whole number.
     *
     * @param parsed the command line
     * @param name the flag
     * @return the number
     * @throws ValidationException when it is missing or not a whole number
     */
    public static int requireNumber(ParsedArgs parsed, String name) {
        return number(parsed, name).orElseThrow(() -> needs(name));
    }

    /**
     * A flag as a whole number, falling back when it is missing.
     *
     * @param parsed the command line
     * @param name the flag
     * @param fallback what to use when it was not given with a value
     * @return the number, or the fallback
     * @throws ValidationException when it was given but is not a whole number
     */
    public static int requireNumber(ParsedArgs parsed, String name, int fallback) {
        return number(parsed, name).orElse(fallback);
    }

    /**
     * @param parsed the command line
     * @param name the flag
     * @return true when the flag was written on its own or written as {@code true}
     */
    public static boolean flagIsSet(ParsedArgs parsed, String name) {
        return parsed.has(name) && parsed.valueOf(name).map("true"::equals).orElse(true);
    }

    private static ParsedArgs read(List<String> argv, Map<String, FlagSpec> known) {
        if (argv == null) {
            throw new ValidationException("a command line must be a list of arguments");
        }
        Map<String, String> shortNames = shortNamesOf(known);
        List<String> command = new ArrayList<>();
        Map<String, Optional<String>> flags = new LinkedHashMap<>();
        List<String> rest = new ArrayList<>();
        boolean sawFlag = false;
        int index = 0;
        while (index < argv.size()) {
            String argument = String.valueOf(argv.get(index));
            if (argument.equals("--")) {
                argv.subList(index + 1, argv.size()).forEach(entry -> rest.add(String.valueOf(entry)));
                break;
            }
            if (argument.startsWith("-") && !argument.equals("-")) {
                sawFlag = true;
                index = readFlag(argv, index, argument, flags, known, shortNames);
                continue;
            }
            (sawFlag ? rest : command).add(argument);
            index += 1;
        }
        return new ParsedArgs(command, flags, rest);
    }

    /**
     * Read one flag, and its value when it takes one.
     *
     * @return the index to carry on from
     */
    private static int readFlag(
            List<String> argv,
            int index,
            String argument,
            Map<String, Optional<String>> flags,
            Map<String, FlagSpec> known,
            Map<String, String> shortNames) {
        String withoutDashes = argument.replaceFirst("^-+", "");
        int equals = withoutDashes.indexOf('=');
        String rawName = equals == -1 ? withoutDashes : withoutDashes.substring(0, equals);
        Optional<String> inline = equals == -1 ? Optional.empty() : Optional.of(withoutDashes.substring(equals + 1));
        String name = shortNames.getOrDefault(rawName, rawName);
        if (known != null && !known.containsKey(name)) {
            throw new ValidationException("there is no flag called --" + rawName);
        }
        boolean takesValue =
                known == null ? inline.isPresent() : known.get(name).takesValue();
        if (!takesValue || inline.isPresent()) {
            flags.put(name, inline);
            return index + 1;
        }
        if (index + 1 >= argv.size() || String.valueOf(argv.get(index + 1)).startsWith("--")) {
            throw new ValidationException("--" + name + " needs a value");
        }
        flags.put(name, Optional.of(String.valueOf(argv.get(index + 1))));
        return index + 2;
    }

    private static Map<String, String> shortNamesOf(Map<String, FlagSpec> known) {
        Map<String, String> shortNames = new HashMap<>();
        if (known != null) {
            known.forEach((name, spec) -> spec.shortName().ifPresent(letter -> shortNames.put(letter, name)));
        }
        return shortNames;
    }

    private static Optional<String> phrase(ParsedArgs parsed, String name) {
        List<String> words = new ArrayList<>();
        parsed.valueOf(name).ifPresent(words::add);
        words.addAll(parsed.rest());
        return words.isEmpty() ? Optional.empty() : Optional.of(String.join(" ", words));
    }

    /**
     * The flag read the way the original read it, as a JavaScript number: blank
     * text is nought and a number in exponent form is accepted. A number too
     * large for the commands to use is refused as well as a fraction.
     */
    private static Optional<Integer> number(ParsedArgs parsed, String name) {
        Optional<String> raw = parsed.valueOf(name);
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        double value = Coerce.readNumber(raw.get());
        if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new ValidationException("--" + name + " must be a whole number, not " + Json.quote(raw.get()));
        }
        return Optional.of((int) value);
    }

    private static ValidationException needs(String name) {
        return new ValidationException("this command needs --" + name);
    }
}
