package io.github.rkaushikdev.garterhole.store;

import io.github.rkaushikdev.garterhole.GarterholeException;
import io.github.rkaushikdev.garterhole.StorageException;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Clock;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Copying the journal somewhere safe.
 *
 * <p>A tower's records are irreplaceable and live on one machine in a vestry. A
 * backup is a copy of the journal with the time in its name, and restoring one
 * is copying it back, which is as complicated as this needs to be.
 *
 * <p>Every backup is read before it is kept. A backup that turns out to be
 * unreadable at the moment it is needed is worse than no backup, because
 * somebody stopped worrying about it years ago.
 */
public final class Backup {

    private static final String PREFIX = "journal-";
    private static final String SUFFIX = ".jsonl";

    private Backup() {}

    /**
     * A backup that was taken.
     *
     * @param path where the copy is
     * @param records how many records it holds
     * @param removed the names of older backups pruned to make room
     */
    public record Taken(Path path, int records, List<String> removed) {}

    /**
     * A backup in a directory.
     *
     * @param name the file name
     * @param path where it is
     * @param bytes how big it is
     */
    public record BackupFile(String name, Path path, long bytes) {}

    /**
     * Take a backup stamped with the time now, keeping every older one.
     *
     * @param journal the journal to copy
     * @param directory where backups are kept
     * @return the backup
     */
    public static Taken backup(Journal journal, Path directory) {
        return take(journal, directory, Instant::now, null);
    }

    /**
     * Take a backup, keeping every older one.
     *
     * @param journal the journal to copy
     * @param directory where backups are kept
     * @param now the time the backup is stamped with
     * @return the backup
     */
    public static Taken backup(Journal journal, Path directory, Supplier<Instant> now) {
        return take(journal, directory, now, null);
    }

    /**
     * Take a backup and keep only the newest few.
     *
     * @param journal the journal to copy
     * @param directory where backups are kept
     * @param now the time the backup is stamped with
     * @param keep how many backups to keep, this one included
     * @return the backup, naming the ones pruned
     */
    public static Taken backup(Journal journal, Path directory, Supplier<Instant> now, int keep) {
        return take(journal, directory, now, keep);
    }

    private static Taken take(Journal journal, Path directory, Supplier<Instant> now, Integer keep) {
        if (journal == null) {
            throw new ValidationException("a backup needs a Journal");
        }
        if (directory == null || directory.toString().isEmpty()) {
            throw new ValidationException("a backup needs a directory");
        }
        if (!journal.exists()) {
            throw new StorageException("there is no journal to back up");
        }
        String stamp = Clock.isoString(now.get()).replaceAll("[:.]", "-");
        StoreFiles.ensureDirectory(directory);
        Path path = directory.resolve(PREFIX + stamp + SUFFIX);
        try {
            Files.copy(journal.path(), path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            throw new StorageException(
                    "cannot copy " + journal.path() + " to " + path + ": " + failure.getMessage(), failure);
        }
        int records = verify(path);
        List<String> removed = keep == null ? List.of() : prune(directory, keep);
        return new Taken(path, records, removed);
    }

    /**
     * Read a backup and count what is in it, so that an unreadable copy is
     * found now rather than when it is needed. A copy that cannot be read back
     * is thrown away.
     *
     * @param path the backup
     * @return how many records it holds
     * @throws StorageException when it is not there or cannot be read back
     */
    public static int verify(Path path) {
        if (path == null || !Files.exists(path)) {
            throw new StorageException("there is no backup at " + path);
        }
        try {
            return Codec.decodeAll(Files.readString(path, StandardCharsets.UTF_8))
                    .size();
        } catch (IOException | GarterholeException failure) {
            StoreFiles.delete(path, "cannot remove the unreadable backup at ");
            throw new StorageException(
                    "the backup at " + path + " could not be read back: " + failure.getMessage(), failure);
        }
    }

    /**
     * @param directory where backups are kept
     * @return the backups in it, newest first, none when it is not there
     */
    public static List<BackupFile> listBackups(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<BackupFile> found = new ArrayList<>();
        try (Stream<Path> entries = Files.list(directory)) {
            for (Path path : entries.toList()) {
                String name = path.getFileName().toString();
                if (name.startsWith(PREFIX) && name.endsWith(SUFFIX) && Files.isRegularFile(path)) {
                    found.add(new BackupFile(name, path, Files.size(path)));
                }
            }
        } catch (IOException failure) {
            throw new StorageException(
                    "cannot list the backups in " + directory + ": " + failure.getMessage(), failure);
        }
        found.sort(Comparator.comparing(BackupFile::name).reversed());
        return found;
    }

    /**
     * Keep only the newest few backups.
     *
     * @param directory where backups are kept
     * @param keep how many to keep
     * @return the names removed
     * @throws ValidationException when the policy keeps nothing
     */
    public static List<String> prune(Path directory, int keep) {
        if (keep < 1) {
            throw new ValidationException("keeping " + keep + " backups is not a policy");
        }
        List<BackupFile> backups = listBackups(directory);
        List<String> removed = new ArrayList<>();
        for (BackupFile entry : backups.subList(Math.min(keep, backups.size()), backups.size())) {
            StoreFiles.delete(entry.path(), "cannot remove the old backup at ");
            removed.add(entry.name());
        }
        return removed;
    }

    /**
     * Put a backup back, over the journal. The backup is read first, so an
     * unreadable one leaves the journal alone.
     *
     * @param journal the journal to write to
     * @param path the backup
     * @return how many records were restored
     * @throws ValidationException when there is no journal
     * @throws StorageException when the backup is not there or cannot be read
     */
    public static int restore(Journal journal, Path path) {
        if (journal == null) {
            throw new ValidationException("a restore needs a Journal to write to");
        }
        int records = verify(path);
        journal.ensureDirectory();
        try {
            Files.copy(path, journal.path(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            throw new StorageException(
                    "cannot copy " + path + " to " + journal.path() + ": " + failure.getMessage(), failure);
        }
        return records;
    }

    /**
     * @param directory where backups are kept
     * @return the newest backup, when there is one
     */
    public static Optional<Path> newestBackup(Path directory) {
        List<BackupFile> backups = listBackups(directory);
        return backups.isEmpty() ? Optional.empty() : Optional.of(backups.get(0).path());
    }

    /**
     * @param path a backup
     * @return the name a backup file has, which the tests and the log line share
     */
    public static String backupName(Path path) {
        return path.getFileName().toString();
    }
}
