package io.github.rkaushikdev.garterhole.store;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.garterhole.ConflictException;
import io.github.rkaushikdev.garterhole.StorageException;
import io.github.rkaushikdev.garterhole.ValidationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * A lock file, so that two copies of the service cannot write to one journal.
 *
 * <p>The lock is a file created only when it is not already there, which the
 * operating system does as a single atomic operation rather than a check
 * followed by a write, the version that lets two processes both think they won.
 *
 * <p>A lock left behind by a process that died is a real problem, so the file
 * says which process holds it and when it was taken, as
 * {@code {"pid":1234,"taken":1700000000000}}. A stale lock can then be broken
 * deliberately, which is a different thing from breaking one by accident.
 */
public final class Lock {

    private final Path path;
    private final LongSupplier now;
    private final long pid;
    private boolean held;

    /**
     * A lock for this process on the real clock.
     *
     * @param path where the lock file goes
     * @throws ValidationException when there is no path
     */
    public Lock(String path) {
        this(pathOf(path), System::currentTimeMillis, ProcessHandle.current().pid());
    }

    /**
     * A lock for this process on the real clock.
     *
     * @param path where the lock file goes
     * @throws ValidationException when there is no path
     */
    public Lock(Path path) {
        this(path, System::currentTimeMillis, ProcessHandle.current().pid());
    }

    /**
     * A lock for this process on a given clock.
     *
     * @param path where the lock file goes
     * @param now milliseconds since the epoch
     * @throws ValidationException when there is no path
     */
    public Lock(Path path, LongSupplier now) {
        this(path, now, ProcessHandle.current().pid());
    }

    /**
     * @param path where the lock file goes
     * @param now milliseconds since the epoch
     * @param pid the process the lock is taken for
     * @throws ValidationException when there is no path
     */
    public Lock(Path path, LongSupplier now, long pid) {
        if (path == null || path.toString().isEmpty()) {
            throw new ValidationException("a lock needs a path");
        }
        this.path = path;
        this.now = now;
        this.pid = pid;
    }

    /**
     * Who holds a lock, and since when.
     *
     * @param pid the process holding it
     * @param taken when it was taken, in milliseconds since the epoch
     */
    public record Holder(long pid, long taken) {}

    /**
     * @return where the lock file goes
     */
    public Path path() {
        return path;
    }

    /**
     * @return true when somebody holds the lock
     */
    public boolean exists() {
        return Files.exists(path);
    }

    /**
     * Take the lock.
     *
     * @return this lock
     * @throws ConflictException when another process holds it
     * @throws StorageException when the file cannot be made
     */
    public Lock acquire() {
        StoreFiles.ensureParent(path);
        String body = "{\"pid\":" + pid + ",\"taken\":" + now.getAsLong() + "}";
        try {
            Files.writeString(
                    path, body, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException taken) {
            Optional<Holder> holder = holder();
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("path", path.toString());
            details.put("holder", holder.orElse(null));
            throw new ConflictException(
                    "the lock at " + path + " is held by process "
                            + holder.map(found -> Long.toString(found.pid())).orElse("unknown"),
                    details);
        } catch (IOException failure) {
            throw new StorageException("cannot take the lock at " + path + ": " + failure.getMessage(), failure);
        }
        held = true;
        return this;
    }

    /**
     * @return who holds the lock, or nothing when nobody does or the file is unreadable
     */
    public Optional<Holder> holder() {
        if (!exists()) {
            return Optional.empty();
        }
        try {
            JsonNode parsed = StoreJson.read(Files.readString(path, StandardCharsets.UTF_8));
            JsonNode pidNode = parsed.path("pid");
            JsonNode takenNode = parsed.path("taken");
            if (!isWholeNumber(pidNode) || !isWholeNumber(takenNode)) {
                return Optional.empty();
            }
            return Optional.of(new Holder(pidNode.longValue(), takenNode.longValue()));
        } catch (IOException | IllegalArgumentException unreadable) {
            return Optional.empty();
        }
    }

    /**
     * @return how long the lock has been held, in milliseconds, or nothing when nobody holds it
     */
    public OptionalLong age() {
        return holder().map(found -> OptionalLong.of(now.getAsLong() - found.taken()))
                .orElse(OptionalLong.empty());
    }

    /**
     * Give the lock up. Only the holder may do this, which stops one process
     * releasing another's lock by accident.
     *
     * @return true when there was a lock to give up
     * @throws StorageException when the file cannot be removed
     */
    public boolean release() {
        if (!held) {
            return false;
        }
        held = false;
        if (!exists()) {
            return false;
        }
        StoreFiles.delete(path, "cannot release the lock at ");
        return true;
    }

    /**
     * Break a lock left behind by a process that is gone. The caller has to say
     * how old a lock has to be before it counts as abandoned, because only the
     * caller knows how long its own work takes.
     *
     * @param olderThan in milliseconds
     * @return true when a lock was broken
     * @throws ValidationException when the age is negative
     */
    public boolean breakStale(long olderThan) {
        if (olderThan < 0) {
            throw new ValidationException(olderThan + " is not an age in milliseconds");
        }
        OptionalLong age = age();
        if (age.isEmpty() || age.getAsLong() < olderThan) {
            return false;
        }
        StoreFiles.delete(path, "cannot break the lock at ");
        return true;
    }

    /**
     * Run something with the lock held, giving it up afterwards whatever happens.
     *
     * @param <T> what the work answers
     * @param work the work
     * @return what the work answered
     * @throws ValidationException when there is no work
     */
    public <T> T around(Supplier<? extends T> work) {
        if (work == null) {
            throw new ValidationException("the lock needs something to do while it is held");
        }
        acquire();
        try {
            return work.get();
        } finally {
            release();
        }
    }

    private static Path pathOf(String path) {
        if (path == null || path.isEmpty()) {
            throw new ValidationException("a lock needs a path");
        }
        return Path.of(path);
    }

    private static boolean isWholeNumber(JsonNode value) {
        return value.isNumber()
                && Double.isFinite(value.doubleValue())
                && value.doubleValue() == Math.rint(value.doubleValue());
    }
}
