package io.github.rkaushikdev.garterhole.store;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One step of folding a journal into a state: given the state so far and the
 * next record, the state after it.
 *
 * @param <T> the type of the state
 */
@FunctionalInterface
public interface Fold<T> {

    /**
     * @param state the state so far
     * @param record the next record of the journal
     * @param index where the record sits in the journal, counting from zero
     * @return the state once the record is applied
     */
    T step(T state, JsonNode record, int index);
}
