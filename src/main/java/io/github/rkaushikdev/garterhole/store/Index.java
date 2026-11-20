package io.github.rkaushikdev.garterhole.store;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Guards;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.LocaleOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A secondary index over a collection.
 *
 * <p>The repository can find a document by its identifier and nothing else,
 * which is all the journal supports. Everything else the service asks for, such
 * as every performance at a tower or every method at a stage, is a scan unless
 * somebody keeps an index.
 *
 * <p>An index is a function from a document to the keys it should be filed
 * under, plus a map from key to identifiers. It is rebuilt from the repository
 * rather than maintained incrementally, because rebuilding is fast at the sizes
 * this runs at and an index that can drift is worse than no index. A key is
 * filed as text, so a number and the same number written as text are one key.
 */
public final class Index {

    private final String name;
    private final String collection;
    private final KeyFunction keysOf;
    private Map<String, Set<String>> byKey = new LinkedHashMap<>();

    /**
     * The keys a document is filed under.
     */
    @FunctionalInterface
    public interface KeyFunction {
        /**
         * @param document the document
         * @param id its identifier
         * @return its keys, each a string, a number or a JSON value
         */
        Iterable<?> keysOf(JsonNode document, String id);
    }

    /**
     * What an index is: its name, the collection it covers and how a document's
     * keys are found.
     *
     * @param name the name it is looked up by
     * @param collection the collection it covers
     * @param keysOf the keys of a document
     */
    public record Definition(String name, String collection, KeyFunction keysOf) {}

    /**
     * A key and how many documents are filed under it.
     *
     * @param key the key
     * @param count how many documents
     */
    public record KeyCount(String key, int count) {}

    /**
     * @param definition the name, collection and key function
     * @throws ValidationException when there is no definition
     * @throws IllegalArgumentException when the name, the collection or the key function is missing
     */
    public Index(Definition definition) {
        if (definition == null) {
            throw new ValidationException("an index needs a definition object");
        }
        this.name = Guards.nonEmptyString(definition.name(), "an index name");
        this.collection = Guards.nonEmptyString(definition.collection(), "a collection name");
        this.keysOf = Guards.function(definition.keysOf(), "the key function");
    }

    /**
     * @param name the name it is looked up by
     * @param collection the collection it covers
     * @param keysOf the keys of a document
     * @return the index, not yet built
     */
    public static Index of(String name, String collection, KeyFunction keysOf) {
        return new Index(new Definition(name, collection, keysOf));
    }

    /**
     * @return the name it is looked up by
     */
    public String name() {
        return name;
    }

    /**
     * @return the collection it covers
     */
    public String collection() {
        return collection;
    }

    /**
     * Build the index from what the repository holds now.
     *
     * @param repository the repository
     * @return this index
     * @throws ValidationException when there is no repository
     */
    public Index build(Repository repository) {
        if (repository == null) {
            throw new ValidationException("an index is built from a Repository");
        }
        Map<String, Set<String>> built = new LinkedHashMap<>();
        for (Repository.Entry entry : repository.all(collection)) {
            for (Object key : keysOf.keysOf(entry.document(), entry.id())) {
                built.computeIfAbsent(keyText(key), text -> new LinkedHashSet<>())
                        .add(entry.id());
            }
        }
        byKey = built;
        return this;
    }

    /**
     * @param key a key, as text or as a number
     * @return the identifiers filed under it, in order
     */
    public List<String> lookup(Object key) {
        Set<String> bucket = byKey.get(keyText(key));
        return bucket == null ? List.of() : bucket.stream().sorted().toList();
    }

    /**
     * @return every key the index holds, in order
     */
    public List<String> keys() {
        return byKey.keySet().stream().sorted().toList();
    }

    /**
     * @return how many documents each key holds, largest first, then by key
     */
    public List<KeyCount> counts() {
        List<KeyCount> counts = new ArrayList<>();
        byKey.forEach((key, bucket) -> counts.add(new KeyCount(key, bucket.size())));
        counts.sort((left, right) -> left.count() != right.count()
                ? Integer.compare(right.count(), left.count())
                : LocaleOrder.compare(left.key(), right.key()));
        return counts;
    }

    /**
     * @return how many keys are filed
     */
    public int size() {
        return byKey.size();
    }

    /** A key as the text it is filed under, the way {@code String(key)} writes it. */
    private static String keyText(Object key) {
        if (key instanceof JsonNode node) {
            return Json.templateText(node);
        }
        if (key instanceof Double || key instanceof Float) {
            return Json.number(((Number) key).doubleValue());
        }
        return String.valueOf(key);
    }
}
