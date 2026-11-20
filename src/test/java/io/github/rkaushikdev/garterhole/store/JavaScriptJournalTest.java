package io.github.rkaushikdev.garterhole.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.garterhole.util.Json;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A journal in the format the JavaScript version wrote.
 *
 * <p>The fixture was not written by this code. Its checksums were worked out
 * from the original's checksum function by a separate implementation, and its
 * lines are what {@code JSON.stringify} writes for each record, including a
 * no-break space, accented letters and a character outside the basic plane,
 * whose two UTF-16 halves the checksum counts separately. A data directory a
 * tower kept with the earlier version has to keep loading, and this is the test
 * that would notice if it stopped.
 */
class JavaScriptJournalTest {

    private static final String FIXTURE = "/store/javascript-journal.jsonl";

    @TempDir
    Path directory;

    private static String fixture() throws IOException {
        try (InputStream stream = Objects.requireNonNull(JavaScriptJournalTest.class.getResourceAsStream(FIXTURE))) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void checksumsTextTheWayTheOriginalDid() {
        assertThat(Codec.checksum("hello")).isEqualTo("06270214");
        assertThat(Codec.checksum("")).isEqualTo("ffffffff");
        assertThat(Codec.checksum("x".repeat(10000))).isEqualTo("496b4f92");
    }

    @Test
    void loadsAJournalTheJavaScriptVersionWrote() throws IOException {
        Path journal = directory.resolve("journal.jsonl");
        Files.writeString(journal, fixture(), StandardCharsets.UTF_8);
        Repository repository = new Repository(new Journal(journal));
        repository.load();
        assertThat(repository.count("methods")).isEqualTo(1);
        assertThat(repository.count("towers")).isEqualTo(1);
        assertThat(repository.count("performances")).isEqualTo(1);
        JsonNode tower = repository.get("towers", "bridgend-st-mary");
        assertThat(tower.get("name").asText()).isEqualTo("St Mary");
        assertThat(tower.get("rate").doubleValue()).isEqualTo(0.5);
        assertThat(repository
                        .get("performances", "st-mary-2025-05-18-plain-bob-minor")
                        .get("composer")
                        .isNull())
                .isTrue();
    }

    @Test
    void writesEveryLineBackByteForByte() throws IOException {
        List<String> lines = fixture().lines().toList();
        assertThat(lines).hasSize(5);
        for (String line : lines) {
            JsonNode record = Json.parse(line.substring(9));
            assertThat(Codec.encode(record)).isEqualTo(line);
        }
    }
}
