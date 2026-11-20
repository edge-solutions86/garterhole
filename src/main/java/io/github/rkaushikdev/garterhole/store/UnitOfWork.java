package io.github.rkaushikdev.garterhole.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Guards;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Several changes that have to land together.
 *
 * <p>Filing a performance touches more than one document: the record itself,
 * and the first in a method for whoever it was a first for. Writing those one
 * at a time leaves a window where the journal holds half the change, and a
 * process that dies in that window comes back up with a state nobody intended.
 *
 * <p>A unit of work collects the changes and writes them in a single append,
 * which either lands whole or does not land at all.
 *
 * <p>Reads inside the unit see the pending changes, so code that writes a
 * document and then reads it back gets what it wrote rather than what is on
 * disk. A unit is finished by committing or rolling back, and cannot be used
 * after that.
 */
public final class UnitOfWork {

    private final Repository repository;
    private List<ObjectNode> changes = new ArrayList<>();
    private boolean finished;

    /**
     * @param repository the repository the changes will land in
     * @throws ValidationException when there is no repository
     */
    public UnitOfWork(Repository repository) {
        if (repository == null) {
            throw new ValidationException("a unit of work needs a Repository");
        }
        this.repository = repository;
    }

    /**
     * Run some work in a unit, committing when it returns and rolling back when
     * it throws.
     *
     * @param <T> what the work answers
     * @param repository the repository the changes land in
     * @param work the work, given the unit to write through
     * @return what the work answered
     * @throws ValidationException when there is no work
     */
    public static <T> T inUnitOfWork(Repository repository, Function<? super UnitOfWork, ? extends T> work) {
        if (work == null) {
            throw new ValidationException("a unit of work needs something to do");
        }
        UnitOfWork unit = new UnitOfWork(repository);
        T answer;
        try {
            answer = work.apply(unit);
        } catch (RuntimeException | Error failure) {
            unit.rollback();
            throw failure;
        }
        unit.commit();
        return answer;
    }

    /**
     * @return how many changes are waiting
     */
    public int size() {
        return changes.size();
    }

    /**
     * File a document as part of this unit.
     *
     * @param collection the collection
     * @param id the identifier
     * @param document the document
     * @return this unit
     * @throws ValidationException when the unit is finished or there is no document
     * @throws IllegalArgumentException when the collection or the identifier is empty
     */
    public UnitOfWork put(String collection, String id, JsonNode document) {
        requireOpen();
        Guards.nonEmptyString(collection, "a collection name");
        Guards.nonEmptyString(id, "a document id");
        if (Json.isUndefined(document)) {
            throw new ValidationException("there is no document to file");
        }
        changes.add(Repository.putRecord(collection, id, document));
        return this;
    }

    /**
     * Take a document out as part of this unit.
     *
     * @param collection the collection
     * @param id the identifier
     * @return this unit
     * @throws ValidationException when the unit is finished
     * @throws IllegalArgumentException when the collection or the identifier is empty
     */
    public UnitOfWork delete(String collection, String id) {
        requireOpen();
        Guards.nonEmptyString(collection, "a collection name");
        Guards.nonEmptyString(id, "a document id");
        changes.add(Repository.deleteRecord(collection, id));
        return this;
    }

    /**
     * The document as it will be once this unit lands.
     *
     * @param collection the collection
     * @param id the identifier
     * @return the document, when there will be one
     */
    public Optional<JsonNode> find(String collection, String id) {
        for (int index = changes.size() - 1; index >= 0; index -= 1) {
            ObjectNode change = changes.get(index);
            if (!collection.equals(change.path("collection").textValue())
                    || !id.equals(change.path("id").textValue())) {
                continue;
            }
            return Repository.PUT.equals(change.path("op").textValue())
                    ? Optional.of(change.path("document"))
                    : Optional.empty();
        }
        return repository.find(collection, id);
    }

    /**
     * @param collection the collection
     * @param id the identifier
     * @return true when the document will be there once this unit lands
     */
    public boolean has(String collection, String id) {
        return find(collection, id).isPresent();
    }

    /**
     * Write everything at once.
     *
     * @return how many changes landed
     * @throws ValidationException when the unit is already finished
     */
    public int commit() {
        requireOpen();
        finished = true;
        if (changes.isEmpty()) {
            return 0;
        }
        int landed = repository.applyBatch(changes);
        changes = new ArrayList<>();
        return landed;
    }

    /**
     * Throw the changes away.
     *
     * @return how many were thrown away
     */
    public int rollback() {
        int thrown = changes.size();
        changes = new ArrayList<>();
        finished = true;
        return thrown;
    }

    private void requireOpen() {
        if (finished) {
            throw new ValidationException("this unit of work has already been finished");
        }
    }
}
