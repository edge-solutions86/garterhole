package io.github.rkaushikdev.garterhole.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.StorageException;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A snapshot of the state, so that start up does not have to replay the whole
 * journal every time.
 *
 * <p>A snapshot records how far through the journal it was taken, and the
 * loader replays only what came after. Getting that number wrong in either
 * direction is the whole risk: too low and records are applied twice, too high
 * and they are lost. So it is written inside the snapshot rather than inferred
 * from the file name or the modification time.
 *
 * <p>The file is written to a temporary name and then renamed, which is atomic
 * on every filesystem this runs on. A snapshot half written is a snapshot that
 * never existed.
 */
public final class Snapshot {

    /** The version stamped into every snapshot. */
    public static final int SNAPSHOT_VERSION = 1;

    private final Path path;

    /**
     * @param path where the snapshot lives
     * @throws ValidationException when there is no path
     */
    public Snapshot(String path) {
        if (path == null || path.isEmpty()) {
            throw new ValidationException("a snapshot needs a path");
        }
        this.path = Path.of(path);
    }

    /**
     * @param path where the snapshot lives
     * @throws ValidationException when there is no path
     */
    public Snapshot(Path path) {
        this(path == null ? null : path.toString());
    }

    /**
     * A state read back from a snapshot.
     *
     * @param state the state as it was saved
     * @param through how many journal records the state includes
     */
    public record Saved(JsonNode state, int through) {}

    /**
     * A state loaded from a snapshot and whatever the journal added since.
     *
     * @param <T> the type of the state
     * @param state the state
     * @param fromSnapshot true when a snapshot was used
     * @param replayed how many journal records were applied on top of it
     */
    public record Loaded<T>(T state, boolean fromSnapshot, int replayed) {}

    /**
     * @return where the snapshot lives
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
     * Write the state out.
     *
     * @param state the state
     * @param through how many journal records the state includes
     * @return this snapshot
     * @throws ValidationException when the count is negative or the state has no JSON form
     * @throws StorageException when the file cannot be written
     */
    public Snapshot write(JsonNode state, int through) {
        if (through < 0) {
            throw new ValidationException(through + " is not a number of records");
        }
        String body = StoreJson.write(state)
                .orElseThrow(() -> new ValidationException("this state cannot be written as JSON"));
        ObjectNode document = Json.object();
        document.put("version", SNAPSHOT_VERSION);
        document.put("through", through);
        document.put("checksum", Codec.checksum(body));
        document.set("state", state);
        String text = StoreJson.write(document).orElseThrow();
        StoreFiles.ensureParent(path);
        Path temporary = Path.of(path + ".writing");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            moveIntoPlace(temporary);
        } catch (IOException failure) {
            throw new StorageException("cannot write the snapshot at " + path + ": " + failure.getMessage(), failure);
        }
        return this;
    }

    /**
     * Read the state back.
     *
     * @return the state and how far it goes, or nothing when there is no snapshot
     * @throws StorageException when the snapshot is unreadable, of another version or damaged
     */
    public Optional<Saved> read() {
        if (!exists()) {
            return Optional.empty();
        }
        JsonNode document;
        try {
            document = StoreJson.read(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException | IllegalArgumentException failure) {
            throw new StorageException("the snapshot at " + path + " cannot be read: " + failure.getMessage(), failure);
        }
        if (!document.isObject() && !document.isArray()) {
            throw new StorageException("the snapshot at " + path + " is not a snapshot");
        }
        JsonNode version = document.path("version");
        if (!version.isNumber() || version.doubleValue() != SNAPSHOT_VERSION) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("found", StoreJson.isUndefined(version) ? null : version);
            details.put("expected", SNAPSHOT_VERSION);
            throw new StorageException(
                    "the snapshot at " + path + " is version " + StoreJson.templateText(version)
                            + " and this reads version " + SNAPSHOT_VERSION,
                    details,
                    null);
        }
        JsonNode through = document.path("through");
        if (!isWholeNumber(through) || through.doubleValue() < 0) {
            throw new StorageException("the snapshot at " + path + " does not say how far it goes");
        }
        JsonNode state = document.path("state");
        String found = StoreJson.write(state).map(Codec::checksum).orElse(null);
        String wanted = document.path("checksum").textValue();
        if (found == null || !found.equals(wanted)) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("found", found);
            details.put("wanted", wanted);
            throw new StorageException("the snapshot at " + path + " is damaged", details, null);
        }
        return Optional.of(new Saved(state, through.intValue()));
    }

    /**
     * Throw the snapshot away, so that the next start up replays everything.
     *
     * @return true when there was one to remove
     * @throws StorageException when it cannot be removed
     */
    public boolean remove() {
        if (!exists()) {
            return false;
        }
        StoreFiles.delete(path, "cannot remove the snapshot at ");
        return true;
    }

    /**
     * Load a state held as JSON from a snapshot and whatever the journal has
     * added since.
     *
     * @param journal the journal
     * @param snapshot the snapshot, which may not have been written yet
     * @param step how each record changes the state
     * @param initial the state when there is no snapshot
     * @return the state, whether a snapshot was used and how much was replayed
     * @throws ValidationException when something to load with is missing
     * @throws StorageException when the snapshot claims more records than the journal holds
     */
    public static Loaded<JsonNode> loadState(
            Journal journal, Snapshot snapshot, Fold<JsonNode> step, Supplier<JsonNode> initial) {
        return loadState(journal, snapshot, step, initial, Function.identity());
    }

    /**
     * Load a state from a snapshot and whatever the journal has added since,
     * turning the saved JSON back into the state's own type first.
     *
     * @param <T> the type of the state
     * @param journal the journal
     * @param snapshot the snapshot, which may not have been written yet
     * @param step how each record changes the state
     * @param initial the state when there is no snapshot
     * @param restore how the state saved in a snapshot becomes a state again
     * @return the state, whether a snapshot was used and how much was replayed
     * @throws ValidationException when something to load with is missing
     * @throws StorageException when the snapshot claims more records than the journal holds
     */
    public static <T> Loaded<T> loadState(
            Journal journal,
            Snapshot snapshot,
            Fold<T> step,
            Supplier<? extends T> initial,
            Function<? super JsonNode, ? extends T> restore) {
        if (journal == null || snapshot == null) {
            throw new ValidationException("loading a state needs a journal and a snapshot");
        }
        if (step == null || initial == null || restore == null) {
            throw new ValidationException("loading a state needs a fold and a starting point");
        }
        Optional<Saved> saved = snapshot.read();
        List<JsonNode> records = journal.read();
        T state = saved.isPresent() ? restore.apply(saved.get().state()) : initial.get();
        int from = saved.map(Saved::through).orElse(0);
        if (from > records.size()) {
            throw new StorageException(
                    "the snapshot claims " + from + " records but the journal holds " + records.size(),
                    Map.of("snapshot", from, "journal", records.size()),
                    null);
        }
        for (int index = from; index < records.size(); index += 1) {
            state = step.step(state, records.get(index), index);
        }
        return new Loaded<>(state, saved.isPresent(), records.size() - from);
    }

    private void moveIntoPlace(Path temporary) throws IOException {
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException notAtomic) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean isWholeNumber(JsonNode value) {
        return value.isNumber()
                && Double.isFinite(value.doubleValue())
                && value.doubleValue() == Math.rint(value.doubleValue());
    }
}
