package io.github.rkaushikdev.garterhole.composition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.method.Method;
import io.github.rkaushikdev.garterhole.method.MethodSerialiser;
import io.github.rkaushikdev.garterhole.method.Title;
import io.github.rkaushikdev.garterhole.util.Guards;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.Text;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A composition as it is filed: who wrote it, what method it is in, how it is
 * called, and what its author says it comes to.
 *
 * <p>The claimed length is stored rather than derived on purpose. Half the value
 * of the library is telling a composer that the calling in front of them does
 * not give the length printed above it, and that comparison needs both numbers.
 */
public final class Composition {

    /** The format stamped into a composition document. */
    public static final String FORMAT = "garterhole.composition";

    /** The version stamped into a composition document, so that a reader can refuse a future one. */
    public static final int FORMAT_VERSION = 1;

    private final String composer;
    private final Method method;
    private final String touch;
    private final OptionalInt claimedLength;
    private final String notes;

    /**
     * What a composition is filed with.
     *
     * @param composer who wrote it
     * @param method the method it is in
     * @param touch how it is called
     * @param claimedLength the length its author says it comes to, when one is given
     * @param notes anything else the author wrote, when anything was
     */
    public record Definition(
            String composer, Method method, String touch, OptionalInt claimedLength, Optional<String> notes) {

        /**
         * A composition with no claimed length and no notes.
         *
         * @param composer who wrote it
         * @param method the method it is in
         * @param touch how it is called
         */
        public Definition(String composer, Method method, String touch) {
            this(composer, method, touch, OptionalInt.empty(), Optional.empty());
        }

        /**
         * @param length the length its author says it comes to
         * @return this definition with a claimed length
         */
        public Definition withClaimedLength(int length) {
            return new Definition(composer, method, touch, OptionalInt.of(length), notes);
        }

        /**
         * @param text what the author wrote
         * @return this definition with notes
         */
        public Definition withNotes(String text) {
            return new Definition(composer, method, touch, claimedLength, Optional.of(text));
        }
    }

    /**
     * @param definition the composer, method, calling, claimed length and notes
     * @throws ValidationException when there is no definition or no method, or
     *     the claimed length is not a positive whole number
     * @throws IllegalArgumentException when the composer or the calling is empty
     */
    public Composition(Definition definition) {
        if (definition == null) {
            throw new ValidationException("a composition needs a definition object");
        }
        Guards.nonEmptyString(definition.composer(), "a composer");
        if (definition.method() == null) {
            throw new ValidationException("a composition needs a Method");
        }
        Guards.nonEmptyString(definition.touch(), "a calling");
        if (definition.claimedLength().isPresent() && definition.claimedLength().getAsInt() < 1) {
            throw new ValidationException(
                    "a composition cannot be " + definition.claimedLength().getAsInt() + " changes long");
        }
        this.composer = Text.squash(definition.composer());
        this.method = definition.method();
        this.touch = Text.squash(definition.touch());
        this.claimedLength = definition.claimedLength();
        this.notes = definition.notes().map(Text::squash).orElse("");
    }

    /**
     * @return who wrote it, with stray whitespace squashed
     */
    public String composer() {
        return composer;
    }

    /**
     * @return the method it is in
     */
    public Method method() {
        return method;
    }

    /**
     * @return how it is called, with stray whitespace squashed
     */
    public String touch() {
        return touch;
    }

    /**
     * @return the length its author says it comes to, when one was given
     */
    public OptionalInt claimedLength() {
        return claimedLength;
    }

    /**
     * @return anything else the author wrote, empty when nothing was
     */
    public String notes() {
        return notes;
    }

    /**
     * @return the title a peal board would carry, for example {@code 336 Plain Bob Major}
     */
    public String title() {
        String length = claimedLength.isPresent() ? claimedLength.getAsInt() + " " : "";
        return length + Title.of(method);
    }

    /**
     * Ring the composition and report on it.
     *
     * @return the report
     */
    public TouchAnalysis analyse() {
        return Analysis.analyseTouch(method, touch);
    }

    /**
     * @return everything wrong with the composition, empty when it is sound
     */
    public List<String> problems() {
        Analysis.Claim claim = new Analysis.Claim(claimedLength, Optional.empty(), method.stage());
        return Analysis.checkClaim(analyse(), claim);
    }

    /**
     * @return true when nothing is wrong with the composition
     */
    public boolean isSound() {
        return problems().isEmpty();
    }

    /**
     * @return the title and the composer, for example {@code 336 Plain Bob Major by A Ringer}
     */
    @Override
    public String toString() {
        return title() + " by " + composer;
    }

    /**
     * The composition as a document.
     *
     * @param composition the composition
     * @return the document, with a missing claimed length written as null
     * @throws ValidationException when there is no composition
     */
    public static ObjectNode toJson(Composition composition) {
        if (composition == null) {
            throw new ValidationException("only a Composition can be serialised");
        }
        ObjectNode json = Json.object();
        json.put("format", FORMAT);
        json.put("version", FORMAT_VERSION);
        json.put("composer", composition.composer);
        json.set("method", MethodSerialiser.methodToJson(composition.method));
        json.put("touch", composition.touch);
        if (composition.claimedLength.isPresent()) {
            json.put("claimedLength", composition.claimedLength.getAsInt());
        } else {
            json.putNull("claimedLength");
        }
        json.put("notes", composition.notes);
        return json;
    }

    /**
     * Read a composition back from its document. A missing or null claimed
     * length reads as no claim.
     *
     * @param document the document
     * @return the composition
     * @throws ValidationException when the document is not an object, is
     *     another version, or holds a composition that is not sound to file
     */
    public static Composition fromJson(JsonNode document) {
        if (document == null || !document.isContainerNode()) {
            throw new ValidationException("a composition document must be an object");
        }
        JsonNode version = document.path("version");
        if (!version.isNumber() || version.doubleValue() != FORMAT_VERSION) {
            throw new ValidationException(
                    "this reader understands version 1 but the document is version " + Json.templateText(version));
        }
        Definition definition = new Definition(
                textOr(document.path("composer")),
                MethodSerialiser.methodFromJson(document.path("method")),
                textOr(document.path("touch")),
                claimedLengthOf(document.path("claimedLength")),
                Json.isUndefined(document.path("notes"))
                        ? Optional.empty()
                        : Optional.of(Json.templateText(document.path("notes"))));
        return new Composition(definition);
    }

    private static String textOr(JsonNode field) {
        return Json.isUndefined(field) || field.isNull() ? "" : Json.templateText(field);
    }

    private static OptionalInt claimedLengthOf(JsonNode field) {
        if (Json.isUndefined(field) || field.isNull()) {
            return OptionalInt.empty();
        }
        if (!field.isNumber() || field.doubleValue() != Math.rint(field.doubleValue()) || !field.canConvertToInt()) {
            throw new ValidationException("a composition cannot be " + Json.templateText(field) + " changes long");
        }
        return OptionalInt.of(field.intValue());
    }
}
