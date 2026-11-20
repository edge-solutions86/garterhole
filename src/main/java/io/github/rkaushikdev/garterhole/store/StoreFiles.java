package io.github.rkaushikdev.garterhole.store;

import io.github.rkaushikdev.garterhole.StorageException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The file system chores every file in the store shares.
 */
final class StoreFiles {

    private StoreFiles() {}

    /**
     * Make the directory a file lives in, and any above it, when they are not
     * there yet.
     *
     * @param file the file
     * @throws StorageException when a directory cannot be made
     */
    static void ensureParent(Path file) {
        ensureDirectory(file.toAbsolutePath().getParent());
    }

    /**
     * Make a directory, and any above it, when it is not there yet.
     *
     * @param directory the directory, or {@code null} for none
     * @throws StorageException when a directory cannot be made
     */
    static void ensureDirectory(Path directory) {
        if (directory == null || Files.isDirectory(directory)) {
            return;
        }
        try {
            Files.createDirectories(directory);
        } catch (IOException failure) {
            throw new StorageException("cannot make the directory " + directory + ": " + failure.getMessage(), failure);
        }
    }

    /**
     * Remove a file.
     *
     * @param file the file
     * @param complaint what to say before the path when it cannot be removed
     * @throws StorageException when it cannot be removed
     */
    static void delete(Path file, String complaint) {
        try {
            Files.delete(file);
        } catch (IOException failure) {
            throw new StorageException(complaint + file + ": " + failure.getMessage(), failure);
        }
    }
}
