package io.github.rkaushikdev.garterhole.store;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.garterhole.StorageException;
import io.github.rkaushikdev.garterhole.ValidationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * An append only journal on disk.
 *
 * <p>Everything the service knows is a fold over this file. Nothing is ever
 * changed in place, which makes the failure modes small: a write either lands
 * whole or leaves a line the checksum rejects, and either way the records
 * before it are untouched.
 *
 * <p>Writes are synchronous on purpose. A tower's journal is a few thousand
 * lines and the simplicity of knowing the record is on disk when the call
 * returns is worth more than the throughput of doing it any other way.
 */
public final class Journal {

    private final Path path;

    /**
     * @param path where the journal lives
     * @throws ValidationException when there is no path
     */
    public Journal(String path) {
        if (path == null || path.isEmpty()) {
            throw new ValidationException("a journal needs a path");
        }
        this.path = Path.of(path);
    }

    /**
     * @param path where the journal lives
     * @throws ValidationException when there is no path
     */
    public Journal(Path path) {
        this(path == null ? null : path.toString());
    }

    /**
     * @return where the journal lives
     */
    public Path path() {
        return path;
    }

    /**
     * @return true when the file is there
     */
    public boolean exists() {
        return Files.exists(path);
    }

    /**
     * @return how many bytes the journal takes up, or zero when it is not there yet
     */
    public long bytes() {
        if (!exists()) {
            return 0;
        }
        try {
            return Files.size(path);
        } catch (IOException failure) {
            throw new StorageException("cannot read " + path + ": " + failure.getMessage(), failure);
        }
    }

    /**
     * Add one record.
     *
     * @param record the record
     * @return this journal
     * @throws StorageException when the file cannot be written
     */
    public Journal append(JsonNode record) {
        appendText(Codec.encode(record) + "\n");
        return this;
    }

    /**
     * Add several records in one write, so that a batch either lands or does not.
     *
     * @param records the records
     * @return how many were written
     * @throws StorageException when the file cannot be written
     */
    public int appendAll(Iterable<? extends JsonNode> records) {
        String text = Codec.encodeAll(records);
        if (text.isEmpty()) {
            return 0;
        }
        appendText(text);
        return lineCount(text);
    }

    /**
     * Every record, in the order they were written.
     *
     * @return the records, none when the journal is not there yet
     * @throws StorageException when the file cannot be read or a line is damaged
     */
    public List<JsonNode> read() {
        if (!exists()) {
            return List.of();
        }
        String text;
        try {
            text = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new StorageException("cannot read " + path + ": " + failure.getMessage(), failure);
        }
        return List.copyOf(Codec.decodeAll(text));
    }

    /**
     * Fold the journal into whatever the records mean.
     *
     * @param <T> the type of the state
     * @param step how each record changes the state
     * @param initial the state before the first record
     * @return the state after the last record
     * @throws ValidationException when there is nothing to fold with
     */
    public <T> T replay(Fold<T> step, T initial) {
        if (step == null) {
            throw new ValidationException("replaying a journal needs a function to fold with");
        }
        T state = initial;
        List<JsonNode> records = read();
        for (int index = 0; index < records.size(); index += 1) {
            state = step.step(state, records.get(index), index);
        }
        return state;
    }

    /**
     * @return how many records the journal holds
     */
    public int count() {
        return read().size();
    }

    /**
     * Replace the whole journal with a new set of records. This is how
     * compaction lands, and it is the only operation here that is not an append.
     *
     * @param records the records the journal should hold
     * @return how many were written
     * @throws StorageException when the file cannot be written
     */
    public int rewrite(Iterable<? extends JsonNode> records) {
        String text = Codec.encodeAll(records);
        ensureDirectory();
        try {
            Files.writeString(path, text, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new StorageException("cannot rewrite " + path + ": " + failure.getMessage(), failure);
        }
        return text.isEmpty() ? 0 : lineCount(text);
    }

    /**
     * Make sure the directory the journal lives in is there.
     *
     * @throws StorageException when it cannot be made
     */
    public void ensureDirectory() {
        StoreFiles.ensureParent(path);
    }

    private void appendText(String text) {
        ensureDirectory();
        try {
            Files.writeString(path, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException failure) {
            throw new StorageException("cannot write to " + path + ": " + failure.getMessage(), failure);
        }
    }

    private static int lineCount(String text) {
        return (int) text.chars().filter(character -> character == '\n').count();
    }
}
