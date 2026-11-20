package io.github.rkaushikdev.garterhole.store;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A set of indexes over one repository, rebuilt together, which is what a
 * write does before anything reads.
 */
public final class IndexSet {

    private final Repository repository;
    private final Map<String, Index> indexes = new LinkedHashMap<>();

    /**
     * @param repository the repository every index is built from
     * @throws ValidationException when there is no repository
     */
    public IndexSet(Repository repository) {
        if (repository == null) {
            throw new ValidationException("an index set needs a Repository");
        }
        this.repository = repository;
    }

    /**
     * Add an index and build it at once.
     *
     * @param index the index
     * @return this set
     * @throws ValidationException when there is no index
     */
    public IndexSet add(Index index) {
        if (index == null) {
            throw new ValidationException("only an Index can be added to an index set");
        }
        index.build(repository);
        indexes.put(index.name(), index);
        return this;
    }

    /**
     * @param name the name of an index
     * @return the index
     * @throws ValidationException when there is no index by that name
     */
    public Index get(String name) {
        Index found = indexes.get(name);
        if (found == null) {
            throw new ValidationException("there is no index called " + Json.quote(name));
        }
        return found;
    }

    /**
     * Look up through a named index.
     *
     * @param name the name of an index
     * @param key the key, as text or as a number
     * @return the documents filed under the key, in identifier order
     */
    public List<Repository.Entry> find(String name, Object key) {
        Index index = get(name);
        return index.lookup(key).stream()
                .map(id -> new Repository.Entry(id, repository.get(index.collection(), id)))
                .toList();
    }

    /**
     * Rebuild every index.
     *
     * @return this set
     */
    public IndexSet rebuild() {
        for (Index index : indexes.values()) {
            index.build(repository);
        }
        return this;
    }

    /**
     * @return the names of the indexes, in order
     */
    public List<String> names() {
        return indexes.keySet().stream().sorted().toList();
    }
}
