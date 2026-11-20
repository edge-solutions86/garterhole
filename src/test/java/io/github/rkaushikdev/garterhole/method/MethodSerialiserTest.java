package io.github.rkaushikdev.garterhole.method;

import static io.github.rkaushikdev.garterhole.method.MethodSerialiser.FORMAT_VERSION;
import static io.github.rkaushikdev.garterhole.method.MethodSerialiser.libraryFromJson;
import static io.github.rkaushikdev.garterhole.method.MethodSerialiser.libraryToJson;
import static io.github.rkaushikdev.garterhole.method.MethodSerialiser.methodFromJson;
import static io.github.rkaushikdev.garterhole.method.MethodSerialiser.methodToJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MethodSerialiserTest {

    private static Method cambridge() {
        return Method.of("Cambridge", 8, "x38x14x1258x36x14x58x16x78,12");
    }

    private static Method plainBobMinor() {
        return Method.of("Plain", 6, "x1x1x1,2");
    }

    @Nested
    class MethodToJson {

        @Test
        void carriesTheNotationAndTheDerivedFacts() {
            assertThat(methodToJson(cambridge()))
                    .isEqualTo(Json.parse("{\"title\": \"Cambridge Surprise Major\", \"name\": \"Cambridge\","
                            + " \"stage\": 8, \"notation\": \"x38x14x1258x36x14x58x16x78,12\", \"class\": \"Surprise\","
                            + " \"leadLength\": 32, \"leadHead\": \"15738264\", \"huntBells\": [1]}"));
        }

        @Test
        void refusesAnythingThatIsNotAMethod() {
            assertThatThrownBy(() -> methodToJson(null)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class MethodFromJson {

        @Test
        void rebuildsAMethodFromADocument() {
            Method rebuilt = methodFromJson(methodToJson(cambridge()));
            assertThat(rebuilt.name()).isEqualTo("Cambridge");
            assertThat(rebuilt.stage()).isEqualTo(8);
            assertThat(rebuilt.ringsTheSameAs(cambridge())).isTrue();
        }

        @Test
        void takesTheNameAndStageFromTheTitleWhenTheyAreMissing() {
            Method rebuilt = methodFromJson(Json.parse("{\"title\": \"Plain Bob Minor\", \"notation\": \"x1x1x1,2\"}"));
            assertThat(rebuilt.name()).isEqualTo("Plain");
            assertThat(rebuilt.stage()).isEqualTo(6);
        }

        @Test
        void ignoresAClassThatDisagreesWithTheNotation() {
            Method rebuilt =
                    methodFromJson(Json.parse("{\"name\": \"Cambridge\", \"stage\": 8, \"class\": \"Treble Bob\","
                            + " \"notation\": \"x38x14x1258x36x14x58x16x78,12\"}"));
            assertThat(methodToJson(rebuilt).get("class").asText()).isEqualTo("Surprise");
        }

        @Test
        void refusesADocumentWithNoNotation() {
            assertThatThrownBy(() -> methodFromJson(Json.parse("{\"title\": \"Plain Bob Minor\"}")))
                    .hasMessageContaining("needs its notation");
        }

        @Test
        void refusesADocumentWithNoNameAndNoTitle() {
            assertThatThrownBy(() -> methodFromJson(Json.parse("{\"notation\": \"x1x1x1,2\"}")))
                    .hasMessageContaining("either a title or a name and a stage");
        }

        @Test
        void refusesSomethingThatIsNotAnObject() {
            assertThatThrownBy(() -> methodFromJson(null)).hasMessageContaining("must be an object");
            assertThatThrownBy(() -> methodFromJson(TextNode.valueOf("Plain Bob Minor")))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class LibraryToJson {

        @Test
        void stampsTheFormatAndTheVersion() {
            MethodLibrary library = new MethodLibrary();
            library.add(cambridge());
            ObjectNode document = libraryToJson(library);
            assertThat(document.get("format").asText()).isEqualTo("garterhole.methods");
            assertThat(document.get("version").asInt()).isEqualTo(FORMAT_VERSION);
            assertThat(document.get("methods")).hasSize(1);
        }

        @Test
        void writesTheMethodsInTitleOrder() {
            MethodLibrary library = new MethodLibrary();
            library.addAll(List.of(plainBobMinor(), cambridge()));
            assertThat(libraryToJson(library).get("methods"))
                    .extracting(entry -> entry.get("title").asText())
                    .containsExactly("Cambridge Surprise Major", "Plain Bob Minor");
        }

        @Test
        void refusesAnythingThatIsNotALibrary() {
            assertThatThrownBy(() -> libraryToJson(null)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class LibraryFromJson {

        @Test
        void roundTripsALibrary() {
            MethodLibrary library = new MethodLibrary();
            library.addAll(List.of(cambridge(), plainBobMinor()));
            MethodLibrary rebuilt = libraryFromJson(Json.parse(Json.stringify(libraryToJson(library))));
            assertThat(rebuilt.titles()).isEqualTo(library.titles());
        }

        @Test
        void refusesAVersionItDoesNotUnderstand() {
            assertThatThrownBy(() -> libraryFromJson(Json.parse("{\"version\": 99, \"methods\": []}")))
                    .hasMessageContaining("understands version 1 but the document is version 99");
        }

        @Test
        void refusesADocumentWithNoMethodsList() {
            assertThatThrownBy(() -> libraryFromJson(Json.parse("{\"version\": " + FORMAT_VERSION + "}")))
                    .hasMessageContaining("needs a list of methods");
        }

        @Test
        void saysWhichMethodCouldNotBeRead() {
            ObjectNode document = Json.object();
            document.put("version", FORMAT_VERSION);
            document.putArray("methods")
                    .add(methodToJson(cambridge()))
                    .add(Json.parse("{\"title\": \"Broken Major\"}"));
            assertThatThrownBy(() -> libraryFromJson(document)).hasMessageContaining("method 1 could not be read");
        }

        @Test
        void refusesSomethingThatIsNotAnObject() {
            assertThatThrownBy(() -> libraryFromJson((JsonNode) null)).hasMessageContaining("must be an object");
        }
    }
}
