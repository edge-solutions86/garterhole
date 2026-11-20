package io.github.rkaushikdev.garterhole.store;

import static io.github.rkaushikdev.garterhole.store.Documents.doc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.rkaushikdev.garterhole.util.Json;
import org.junit.jupiter.api.Test;

/**
 * The journal's checksums are over the JSON text, so the text has to be what
 * {@code JSON.stringify} wrote. Each expectation here is what a JavaScript
 * runtime writes for the same value.
 */
class StoreJsonTest {

    private static String written(String json) {
        return StoreJson.write(doc(json)).orElseThrow();
    }

    @Test
    void writesNumbersTheWayJavaScriptDoes() {
        assertThat(written("[1, 2.0, 1.5, 0.1, 1e21, 1e-7, 100000, -0.0]"))
                .isEqualTo("[1,2,1.5,0.1,1e+21,1e-7,100000,0]");
    }

    @Test
    void writesANumberThatIsNotFiniteAsNull() {
        assertThat(StoreJson.write(DoubleNode.valueOf(Double.NaN))).hasValue("null");
    }

    @Test
    void putsKeysThatLookLikeArrayIndexesFirst() {
        assertThat(written("{'b':1,'10':2,'a':3,'2':4,'01':5}"))
                .isEqualTo("{\"2\":4,\"10\":2,\"b\":1,\"a\":3,\"01\":5}");
    }

    @Test
    void escapesControlCharactersInLowerCase() {
        String separator = String.valueOf((char) 0x2028);
        assertThat(StoreJson.write(TextNode.valueOf("a\u001fb\nc\"\\" + separator)))
                .hasValue("\"a\\u001fb\\nc\\\"\\\\" + separator + "\"");
    }

    @Test
    void escapesALoneSurrogate() {
        String bell = new String(Character.toChars(0x1F514));
        assertThat(StoreJson.write(TextNode.valueOf("x\ud800y"))).hasValue("\"x\\ud800y\"");
        assertThat(StoreJson.write(TextNode.valueOf(bell))).hasValue("\"" + bell + "\"");
    }

    @Test
    void leavesOutAFieldWithNoJsonForm() {
        ObjectNode object = Json.object();
        object.set("kept", TextNode.valueOf("yes"));
        object.set("gone", Documents.function());
        assertThat(StoreJson.write(object)).hasValue("{\"kept\":\"yes\"}");
        assertThat(StoreJson.write(MissingNode.getInstance())).isEmpty();
    }

    @Test
    void refusesTrailingTextTheWayJsonParseDoes() {
        assertThatThrownBy(() -> StoreJson.read("{\"a\":1} extra")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StoreJson.read("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void writesAFieldTheWayATemplateLiteralDoes() {
        assertThat(StoreJson.templateText(MissingNode.getInstance())).isEqualTo("undefined");
        assertThat(StoreJson.templateText(doc("'merge'"))).isEqualTo("merge");
        assertThat(StoreJson.templateText(doc("[1,null,'a']"))).isEqualTo("1,,a");
        assertThat(StoreJson.templateText(doc("{}"))).isEqualTo("[object Object]");
    }
}
