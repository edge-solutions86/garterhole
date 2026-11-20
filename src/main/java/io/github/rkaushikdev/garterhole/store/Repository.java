package io.github.rkaushikdev.garterhole.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.NotFoundException;
import io.github.rkaushikdev.garterhole.StorageException;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Guards;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The service's state, kept as a fold over the journal.
 *
 * <p>Everything is a document with an identifier, filed in a named collection.
 * That is a deliberately thin model: the towers, the band, the method library
 * and the performance records all have the same shape as far as storage is
 * concerned, and the domain classes are rebuilt from the documents when they
 * are wanted rather than being stored as themselves.
 *
 * <p>There are only two operations in the journal, put and delete, and the
 * state is the result of applying them in order. That means the state can
 * always be rebuilt from the file, and it means a bug in this class cannot lose
 * data that was written before the bug existed.
 *
 * <p>Documents are held as they were filed, not copied, so a caller must not
 * change a document after filing it or after reading it back.
 */
public final class Repository {

    /** The operation that files a document. */
    public static final String PUT = "put";

    /** The operation that takes a document out. */
    public static final String DELETE = "delete";

    private final Journal journal;
    private final Snapshot snapshot;
    private Map<String, Map<String, JsonNode>> collections = new LinkedHashMap<>();
    private int applied;

    /**
     * A repository over a journal, with no snapshot.
     *
     * @param journal the journal
     * @throws ValidationException when there is no journal
     * @throws StorageException when the journal holds something that is not a change
     */
    public Repository(Journal journal) {
        this(journal, null);
    }

    /**
     * A repository over a journal, starting from a snapshot when there is one.
     *
     * @param journal the journal
     * @param snapshot the snapshot, or {@code null} for none
     * @throws ValidationException when there is no journal
     * @throws StorageException when the journal or the snapshot cannot be used
     */
    public Repository(Journal journal, Snapshot snapshot) {
        if (journal == null) {
            throw new ValidationException("a repository needs a Journal");
        }
        this.journal = journal;
        this.snapshot = snapshot;
        load();
    }

    /**
     * What loading found.
     *
     * @param fromSnapshot true when a snapshot was used
     * @param replayed how many journal records were applied
     */
    public record Loaded(boolean fromSnapshot, int replayed) {}

    /**
     * One document in a collection.
     *
     * @param id its identifier
     * @param document the document
     */
    public record Entry(String id, JsonNode document) {}

    /**
     * @return the journal the state is a fold over
     */
    public Journal journal() {
        return journal;
    }

    /**
     * @return the snapshot, when the repository has one
     */
    public Optional<Snapshot> snapshot() {
        return Optional.ofNullable(snapshot);
    }

    /**
     * Rebuild the state from the snapshot and the journal.
     *
     * @return whether a snapshot was used and how many records were replayed
     * @throws StorageException when the journal or the snapshot cannot be used
     */
    public Loaded load() {
        if (snapshot == null) {
            Map<String, Map<String, JsonNode>> state = new LinkedHashMap<>();
            List<JsonNode> records = journal.read();
            for (int index = 0; index < records.size(); index += 1) {
                applyRecord(state, records.get(index), index);
            }
            collections = state;
            applied = records.size();
            return new Loaded(false, records.size());
        }
        Snapshot.Loaded<Map<String, Map<String, JsonNode>>> loaded = Snapshot.loadState(
                journal,
                snapshot,
                (state, record, index) -> {
                    applyRecord(state, record, index);
                    return state;
                },
                LinkedHashMap::new,
                Repository::toMap);
        collections = loaded.state();
        applied = journal.count();
        return new Loaded(loaded.fromSnapshot(), loaded.replayed());
    }

    /**
     * File a document, replacing any with the same identifier.
     *
     * @param collection the collection
     * @param id the identifier
     * @param document the document
     * @return this repository
     * @throws IllegalArgumentException when the collection or the identifier is empty
     * @throws ValidationException when there is no document
     */
    public Repository put(String collection, String id, JsonNode document) {
        Guards.nonEmptyString(collection, "a collection name");
        Guards.nonEmptyString(id, "a document id");
        if (StoreJson.isUndefined(document)) {
            throw new ValidationException("there is no document to file");
        }
        applyBatch(List.of(putRecord(collection, id, document)));
        return this;
    }

    /**
     * Take a document out. Taking out a document that is not there writes
     * nothing to the journal.
     *
     * @param collection the collection
     * @param id the identifier
     * @return true when there was one to remove
     */
    public boolean delete(String collection, String id) {
        Guards.nonEmptyString(collection, "a collection name");
        Guards.nonEmptyString(id, "a document id");
        if (!has(collection, id)) {
            return false;
        }
        applyBatch(List.of(deleteRecord(collection, id)));
        return true;
    }

    /**
     * Write several changes as one append, so that they land together or not
     * at all. Every write goes through here, which is what makes a single put
     * and a whole unit of work follow the same path.
     *
     * @param changes journal records, each a put or a delete
     * @return how many landed
     * @throws ValidationException when there is no batch
     * @throws StorageException when any change is not one this can apply, in
     *     which case nothing is written
     */
    public int applyBatch(List<? extends JsonNode> changes) {
        if (changes == null) {
            throw new ValidationException("a batch of changes must be an array");
        }
        if (changes.isEmpty()) {
            return 0;
        }
        for (int offset = 0; offset < changes.size(); offset += 1) {
            checkRecord(changes.get(offset), applied + offset);
        }
        journal.appendAll(changes);
        for (int offset = 0; offset < changes.size(); offset += 1) {
            applyRecord(collections, changes.get(offset), applied + offset);
        }
        applied += changes.size();
        return changes.size();
    }

    /**
     * @param collection the collection
     * @param id the identifier
     * @return true when the collection holds a document with that identifier
     */
    public boolean has(String collection, String id) {
        Map<String, JsonNode> held = collections.get(collection);
        return held != null && held.containsKey(id);
    }

    /**
     * @param collection the collection
     * @param id the identifier
     * @return the document
     * @throws NotFoundException when there is no such document
     */
    public JsonNode get(String collection, String id) {
        return find(collection, id).orElseThrow(() -> new NotFoundException(collection, id));
    }

    /**
     * The document, where a miss is ordinary.
     *
     * @param collection the collection
     * @param id the identifier
     * @return the document, when there is one
     */
    public Optional<JsonNode> find(String collection, String id) {
        Map<String, JsonNode> held = collections.get(collection);
        JsonNode found = held == null ? null : held.get(id);
        return StoreJson.isUndefined(found) ? Optional.empty() : Optional.of(found);
    }

    /**
     * Everything in a collection, in identifier order.
     *
     * @param collection the collection
     * @return the entries, none for a collection that holds nothing
     */
    public List<Entry> all(String collection) {
        Map<String, JsonNode> held = collections.get(collection);
        if (held == null) {
            return List.of();
        }
        List<Entry> entries = new ArrayList<>(held.size());
        held.forEach((id, document) -> entries.add(new Entry(id, document)));
        entries.sort((left, right) -> LocaleOrder.compare(left.id(), right.id()));
        return entries;
    }

    /**
     * @param collection the collection
     * @return how many documents it holds
     */
    public int count(String collection) {
        Map<String, JsonNode> held = collections.get(collection);
        return held == null ? 0 : held.size();
    }

    /**
     * @return every collection with anything in it, in name order
     */
    public List<String> collectionNames() {
        return collections.keySet().stream()
                .filter(name -> count(name) > 0)
                .sorted()
                .toList();
    }

    /**
     * Write a snapshot of where the journal has got to.
     *
     * @return true when a snapshot was written, false when the repository has none
     */
    public boolean save() {
        if (snapshot == null) {
            return false;
        }
        snapshot.write(fromMap(collections), applied);
        return true;
    }

    /**
     * Note that the journal has been rewritten to hold a given number of
     * records, which is what compaction does underneath the repository.
     *
     * @param records how many records the journal now holds
     */
    void journalRewritten(int records) {
        applied = records;
    }

    /**
     * @param collection the collection
     * @param id the identifier
     * @param document the document
     * @return the journal record that files the document
     */
    static ObjectNode putRecord(String collection, String id, JsonNode document) {
        ObjectNode record = Json.object();
        record.put("op", PUT);
        record.put("collection", collection);
        record.put("id", id);
        record.set("document", document);
        return record;
    }

    /**
     * @param collection the collection
     * @param id the identifier
     * @return the journal record that takes the document out
     */
    static ObjectNode deleteRecord(String collection, String id) {
        ObjectNode record = Json.object();
        record.put("op", DELETE);
        record.put("collection", collection);
        record.put("id", id);
        return record;
    }

    /**
     * Check that a record is a change this can apply, without applying it.
     * Doing this before anything is written is what lets a bad batch be refused
     * rather than half written.
     */
    private static void checkRecord(JsonNode record, int index) {
        if (record == null || !record.isContainerNode()) {
            throw new StorageException("record " + index + " is not a change", Map.of("index", index), null);
        }
        if (!record.path("collection").isTextual() || !record.path("id").isTextual()) {
            throw new StorageException(
                    "record " + index + " does not say what it changes", Map.of("index", index), null);
        }
        JsonNode op = record.path("op");
        if (!op.isTextual() || !(PUT.equals(op.textValue()) || DELETE.equals(op.textValue()))) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("index", index);
            details.put("op", StoreJson.isUndefined(op) ? null : op);
            throw new StorageException(
                    "record " + index + " has an operation this cannot apply: " + StoreJson.templateText(op),
                    details,
                    null);
        }
    }

    private static void applyRecord(Map<String, Map<String, JsonNode>> state, JsonNode record, int index) {
        checkRecord(record, index);
        String collection = record.path("collection").textValue();
        String id = record.path("id").textValue();
        if (PUT.equals(record.path("op").textValue())) {
            state.computeIfAbsent(collection, name -> new LinkedHashMap<>()).put(id, record.path("document"));
            return;
        }
        Map<String, JsonNode> held = state.get(collection);
        if (held != null) {
            held.remove(id);
        }
    }

    private static Map<String, Map<String, JsonNode>> toMap(JsonNode state) {
        Map<String, Map<String, JsonNode>> rebuilt = new LinkedHashMap<>();
        if (state == null || !state.isObject()) {
            return rebuilt;
        }
        for (Map.Entry<String, JsonNode> collection : state.properties()) {
            Map<String, JsonNode> documents = new LinkedHashMap<>();
            if (collection.getValue().isObject()) {
                for (Map.Entry<String, JsonNode> field : collection.getValue().properties()) {
                    documents.put(field.getKey(), field.getValue());
                }
            }
            rebuilt.put(collection.getKey(), documents);
        }
        return rebuilt;
    }

    private static ObjectNode fromMap(Map<String, Map<String, JsonNode>> state) {
        ObjectNode plain = Json.object();
        state.forEach((collection, documents) -> {
            ObjectNode held = plain.putObject(collection);
            documents.forEach(held::set);
        });
        return plain;
    }
}
