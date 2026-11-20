package io.github.rkaushikdev.garterhole.method;

import io.github.rkaushikdev.garterhole.GarterholeException;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.core.Stage;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Method titles.
 *
 * <p>A method title is its name, then its class, then the name of its stage:
 * {@code Cambridge Surprise Major}. The class is left out for a principle,
 * which is why {@code Stedman Triples} has only two words of its own.
 *
 * <p>Reading a title back apart is worth doing carefully, because the name can
 * contain any word at all. {@code Double Norwich Court Bob Major} has a class
 * word in the middle of its name and a different one at the end.
 */
public final class Title {

    /** The class words, longest first so that Treble Bob is matched before Bob. */
    private static final List<String> CLASS_WORDS = List.of(
            "Treble Place", "Treble Bob", "Slow Course", "Surprise", "Delight", "Alliance", "Place", "Hybrid", "Bob");

    /** Words that can sit in front of the class word. */
    private static final List<String> MODIFIERS = List.of("Differential", "Little");

    private static final String PRINCIPLE = "Principle";

    private Title() {}

    /**
     * A title read apart.
     *
     * @param name the name, which may be empty for a title such as {@code Little Bob Major}
     * @param className the class with its modifiers, or {@code Principle} when there is none
     * @param stage the stage the title ends with
     */
    public record Parts(String name, String className, int stage) {}

    /**
     * Build a title from its parts.
     *
     * @param name the name, or {@code null} for none
     * @param className the class, {@code Principle} or {@code null} to leave it out
     * @param stage the stage
     * @return the title
     * @throws ValidationException when there is neither a name nor a class
     * @throws IllegalArgumentException when the stage has no name
     */
    public static String build(String name, String className, int stage) {
        String squashedName = Text.squash(name == null ? "" : name);
        String squashedClass = Text.squash(className == null ? "" : className);
        boolean hasClass = !squashedClass.isEmpty() && !PRINCIPLE.equals(squashedClass);
        if (squashedName.isEmpty() && !hasClass) {
            throw new ValidationException("a title needs a name or a class");
        }
        List<String> words = new ArrayList<>();
        if (!squashedName.isEmpty()) {
            words.add(squashedName);
        }
        if (hasClass) {
            words.add(squashedClass);
        }
        words.add(Stage.name(stage));
        return String.join(" ", words);
    }

    /**
     * Build the title of a principle, or of anything else with no class.
     *
     * @param name the name
     * @param stage the stage
     * @return the title
     */
    public static String build(String name, int stage) {
        return build(name, null, stage);
    }

    /**
     * The title a method should have, worked out from its own changes.
     *
     * @param method a method
     * @return its title
     */
    public static String of(Method method) {
        return build(method.name(), Classifier.classify(method).className(), method.stage());
    }

    /**
     * Read a title apart. The stage comes off the end first, then any class word
     * and its modifiers, and whatever is left is the name. Some titles have no
     * name of their own at all, {@code Little Bob Major} being one, and those
     * give back an empty name rather than an error.
     *
     * @param title the title
     * @return its parts
     * @throws ValidationException when the title is blank, does not end with a stage,
     *     or is nothing but a stage name
     */
    public static Parts parse(String title) {
        String text = Text.squash(title == null ? "" : title);
        if (text.isEmpty()) {
            throw new ValidationException("a title cannot be blank");
        }
        OptionalInt found = trailingStage(text);
        if (found.isEmpty()) {
            throw new ValidationException(Json.quote(title) + " does not end with the name of a stage");
        }
        int stage = found.getAsInt();
        String rest =
                text.substring(0, text.length() - Stage.name(stage).length()).strip();
        String className = trailingClass(rest);
        if (!className.isEmpty()) {
            rest = rest.substring(0, rest.length() - className.length()).strip();
        }
        if (rest.isEmpty() && className.isEmpty()) {
            throw new ValidationException(Json.quote(title) + " is nothing but a stage name");
        }
        return new Parts(rest, className.isEmpty() ? PRINCIPLE : className, stage);
    }

    /**
     * @param title a title
     * @return true when the title has the shape a title should have
     */
    public static boolean isWellFormed(String title) {
        try {
            parse(title);
            return true;
        } catch (GarterholeException notATitle) {
            return false;
        }
    }

    private static OptionalInt trailingStage(String text) {
        for (int stage : Stage.supported()) {
            if (text.endsWith(" " + Stage.name(stage))) {
                return OptionalInt.of(stage);
            }
        }
        try {
            return OptionalInt.of(Stage.fromName(text));
        } catch (IllegalArgumentException notAStage) {
            return OptionalInt.empty();
        }
    }

    /** The class words at the end of the text, or an empty string. */
    private static String trailingClass(String text) {
        for (String word : CLASS_WORDS) {
            if (text.equals(word) || text.endsWith(" " + word)) {
                return withModifiers(text, word);
            }
        }
        return "";
    }

    private static String withModifiers(String text, String word) {
        String found = word;
        String head = text.substring(0, text.length() - found.length()).strip();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String modifier : MODIFIERS) {
                if (head.equals(modifier) || head.endsWith(" " + modifier)) {
                    found = modifier + " " + found;
                    head = head.substring(0, head.length() - modifier.length()).strip();
                    changed = true;
                }
            }
        }
        return found;
    }
}
