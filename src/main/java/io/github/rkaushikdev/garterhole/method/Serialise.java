package io.github.rkaushikdev.garterhole.method;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.GarterholeException;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Methods on the wire and on disk.
 *
 * <p>The serialised form carries the title and the class as well as the
 * notation, even though both can be worked out from the notation. That is for
 * the reader on the other end, which may not be this library. Coming back in,
 * the derived fields are ignored and everything is worked out again, so a file
 * written by something that got the class wrong still loads correctly.
 */
public final class Serialise {

    /** The version stamped into every library document, so that a reader can refuse a future one. */
    public static final int FORMAT_VERSION = 1;

    /** The format name stamped into every library document. */
    public static final String FORMAT = "garterhole.methods";

    private Serialise() {}

    /**
     * @param method a method
     * @return its document: {@code title}, {@code name}, {@code stage}, {@code notation},
     *     {@code class}, {@code leadLength}, {@code leadHead} and {@code huntBells}, in that order
     * @throws ValidationException when there is no method
     */
    public static ObjectNode methodToJson(Method method) {
        if (method == null) {
            throw new ValidationException("only a Method can be serialised");
        }
        Classification facts = Classifier.classify(method);
        ObjectNode document = Json.object();
        document.put("title", Title.of(method));
        document.put("name", method.name());
        document.put("stage", method.stage());
        document.put("notation", method.notation());
        document.put("class", facts.className());
        document.put("leadLength", method.leadLength());
        document.put("leadHead", method.leadHead().toString());
        ArrayNode hunts = document.putArray("huntBells");
        facts.huntBells().forEach(hunts::add);
        return document;
    }

    /**
     * Rebuild a method from its document. Only the notation and either the
     * name and stage or the title are read; everything derived is worked out
     * again.
     *
     * @param document the document
     * @return the method
     * @throws ValidationException when the document is not an object or lacks what is needed
     */
    public static Method methodFromJson(JsonNode document) {
        if (document == null || !(document.isObject() || document.isArray())) {
            throw new ValidationException("a method document must be an object");
        }
        JsonNode notation = document.path("notation");
        if (!notation.isTextual()) {
            throw new ValidationException("a method document needs its notation");
        }
        NameParts parts = namePartsFrom(document);
        return new Method(new Method.Definition(parts.name(), parts.stage(), notation.textValue()));
    }

    /**
     * @param library a library
     * @return the library document, with its methods in title order
     * @throws ValidationException when there is no library
     */
    public static ObjectNode libraryToJson(MethodLibrary library) {
        if (library == null) {
            throw new ValidationException("only a MethodLibrary can be serialised");
        }
        ObjectNode document = Json.object();
        document.put("format", FORMAT);
        document.put("version", FORMAT_VERSION);
        ArrayNode methods = document.putArray("methods");
        library.methods().forEach(method -> methods.add(methodToJson(method)));
        return document;
    }

    /**
     * @param document a library document
     * @return the library it describes
     * @throws ValidationException when the document is of another version, has no
     *     list of methods, or holds a method that cannot be read
     */
    public static MethodLibrary libraryFromJson(JsonNode document) {
        if (document == null || !(document.isObject() || document.isArray())) {
            throw new ValidationException("a library document must be an object");
        }
        JsonNode version = document.path("version");
        if (!(version.isNumber() && version.doubleValue() == FORMAT_VERSION)) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("expected", FORMAT_VERSION);
            details.put("found", version.isMissingNode() ? null : version);
            throw new ValidationException(
                    "this reader understands version " + FORMAT_VERSION + " but the document is version "
                            + written(version),
                    details);
        }
        JsonNode methods = document.path("methods");
        if (!methods.isArray()) {
            throw new ValidationException("a library document needs a list of methods");
        }
        MethodLibrary library = new MethodLibrary();
        for (int index = 0; index < methods.size(); index += 1) {
            try {
                library.add(methodFromJson(methods.get(index)));
            } catch (GarterholeException | IllegalArgumentException failure) {
                throw new ValidationException(
                        "method " + index + " could not be read: " + failure.getMessage(), Map.of("index", index));
            }
        }
        return library;
    }

    /**
     * Work out the name and stage of a method document. A document with a title
     * and nothing else is enough, because a title carries both.
     */
    private static NameParts namePartsFrom(JsonNode document) {
        JsonNode name = document.path("name");
        JsonNode stage = document.path("stage");
        if (name.isTextual() && stage.isNumber() && stage.doubleValue() == Math.rint(stage.doubleValue())) {
            return new NameParts(name.textValue(), stage.intValue());
        }
        JsonNode title = document.path("title");
        if (title.isTextual()) {
            Title.Parts parts = Title.parse(title.textValue());
            return new NameParts(parts.name().isEmpty() ? parts.className() : parts.name(), parts.stage());
        }
        throw new ValidationException("a method document needs either a title or a name and a stage");
    }

    /** A value written the way a template string writes it, with a missing one as {@code undefined}. */
    private static String written(JsonNode value) {
        if (value.isMissingNode()) {
            return "undefined";
        }
        if (value.isTextual()) {
            return value.textValue();
        }
        if (value.isNumber()) {
            return Json.number(value.doubleValue());
        }
        return value.toString();
    }

    private record NameParts(String name, int stage) {}
}
