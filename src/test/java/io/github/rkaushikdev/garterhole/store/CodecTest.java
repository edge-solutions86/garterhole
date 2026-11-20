package io.github.rkaushikdev.garterhole.store;

import static io.github.rkaushikdev.garterhole.store.Codec.checksum;
import static io.github.rkaushikdev.garterhole.store.Codec.decode;
import static io.github.rkaushikdev.garterhole.store.Codec.decodeAll;
import static io.github.rkaushikdev.garterhole.store.Codec.encode;
import static io.github.rkaushikdev.garterhole.store.Codec.encodeAll;
import static io.github.rkaushikdev.garterhole.store.Documents.doc;
import static io.github.rkaushikdev.garterhole.store.Documents.docs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.StorageException;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CodecTest {

    @Nested
    class Checksum {

        @Test
        void isEightHexadecimalCharacters() {
            assertThat(checksum("hello")).matches("^[0-9a-f]{8}$");
        }

        @Test
        void isTheSameForTheSameText() {
            assertThat(checksum("hello")).isEqualTo(checksum("hello"));
        }

        @Test
        void changesWhenTheTextChanges() {
            assertThat(checksum("hello")).isNotEqualTo(checksum("hellp"));
        }

        @Test
        void noticesATransposition() {
            assertThat(checksum("ab")).isNotEqualTo(checksum("ba"));
        }

        @Test
        void handlesALongStringAndAnEmptyOne() {
            assertThat(checksum("")).matches("^[0-9a-f]{8}$");
            assertThat(checksum("x".repeat(10000))).matches("^[0-9a-f]{8}$");
        }
    }

    @Nested
    class Encode {

        @Test
        void putsTheChecksumInFrontOfThePayload() {
            String line = encode(doc("{'a':1}"));
            assertThat(line.substring(0, 8)).isEqualTo(checksum("{\"a\":1}"));
            assertThat(line.substring(9)).isEqualTo("{\"a\":1}");
        }

        @Test
        void writesNoNewline() {
            assertThat(encode(doc("{'a':1}"))).doesNotContain("\n");
        }

        @Test
        void refusesNothingAtAll() {
            assertThatThrownBy(() -> encode(null)).hasMessageContaining("nothing to write");
        }

        @Test
        void refusesSomethingJsonCannotHold() {
            ObjectNode looping = Json.object();
            looping.set("self", looping);
            assertThatThrownBy(() -> encode(looping)).hasMessageContaining("cannot be written as JSON");
            assertThatThrownBy(() -> encode(Documents.function())).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class Decode {

        @Test
        void readsALineBack() {
            assertThat(decode(encode(doc("{'a':1,'b':'two'}")))).isEqualTo(doc("{'a':1,'b':'two'}"));
        }

        @Test
        void roundTripsEveryKindOfValue() {
            for (JsonNode value : docs("null", "0", "'text'", "[1,2,3]", "{'nested':{'deep':true}}")) {
                assertThat(decode(encode(value))).isEqualTo(value);
            }
        }

        @Test
        void refusesALineWithNoChecksum() {
            assertThatThrownBy(() -> decode("{\"a\":1}", 4)).hasMessage("line 4 does not start with a checksum");
        }

        @Test
        void catchesAPayloadThatHasBeenChanged() {
            String line = encode(doc("{'a':1}"));
            String damaged = line.substring(0, 9) + "{\"a\":2}";
            assertThatThrownBy(() -> decode(damaged, 7)).isInstanceOf(StorageException.class);
            assertThatThrownBy(() -> decode(damaged, 7)).hasMessageContaining("line 7 is damaged");
        }

        @Test
        void catchesAPayloadThatIsNotJson() {
            String payload = "not json";
            assertThatThrownBy(() -> decode(checksum(payload) + " " + payload, 2))
                    .hasMessageContaining("line 2 is not JSON");
        }

        @Test
        void refusesSomethingThatIsNotAString() {
            assertThatThrownBy(() -> decode(null)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class WholeFiles {

        private final List<JsonNode> records = docs("{'a':1}", "{'b':2}", "{'c':3}");

        @Test
        void writesANewlineAfterEveryRecord() {
            String text = encodeAll(records);
            assertThat(text).endsWith("\n");
            assertThat(Arrays.stream(text.split("\n")).filter(line -> !line.isEmpty()))
                    .hasSize(3);
        }

        @Test
        void readsThemBackInOrder() {
            assertThat(decodeAll(encodeAll(records))).isEqualTo(records);
        }

        @Test
        void readsAnEmptyFile() {
            assertThat(decodeAll("")).isEmpty();
            assertThat(encodeAll(List.of())).isEmpty();
        }

        @Test
        void reportsADamagedLineRatherThanSkippingIt() {
            String text = encodeAll(records) + "00000000 {\"d\":4}\n";
            assertThatThrownBy(() -> decodeAll(text)).hasMessageContaining("line 4 is damaged");
        }

        @Test
        void refusesSomethingThatIsNotAString() {
            assertThatThrownBy(() -> decodeAll(null)).isInstanceOf(ValidationException.class);
        }
    }
}
