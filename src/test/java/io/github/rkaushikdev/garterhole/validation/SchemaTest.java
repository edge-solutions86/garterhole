package io.github.rkaushikdev.garterhole.validation;

import static io.github.rkaushikdev.garterhole.validation.Schemas.anything;
import static io.github.rkaushikdev.garterhole.validation.Schemas.arrayOf;
import static io.github.rkaushikdev.garterhole.validation.Schemas.bool;
import static io.github.rkaushikdev.garterhole.validation.Schemas.check;
import static io.github.rkaushikdev.garterhole.validation.Schemas.number;
import static io.github.rkaushikdev.garterhole.validation.Schemas.object;
import static io.github.rkaushikdev.garterhole.validation.Schemas.oneOf;
import static io.github.rkaushikdev.garterhole.validation.Schemas.optional;
import static io.github.rkaushikdev.garterhole.validation.Schemas.string;
import static io.github.rkaushikdev.garterhole.validation.Schemas.validate;
import static io.github.rkaushikdev.garterhole.validation.Schemas.withDefault;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.NullNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SchemaTest {

    private static JsonNode node(String json) {
        return Json.parse(json);
    }

    private static List<String> messages(Schema schema, JsonNode value) {
        return schema.problems(value, "").stream().map(Problem::message).toList();
    }

    private static Map<String, Schema> fields(Object... namesAndSchemas) {
        Map<String, Schema> fields = new LinkedHashMap<>();
        for (int index = 0; index < namesAndSchemas.length; index += 2) {
            fields.put((String) namesAndSchemas[index], (Schema) namesAndSchemas[index + 1]);
        }
        return fields;
    }

    @Nested
    class Strings {

        @Test
        void acceptsAString() {
            assertThat(messages(string(), node("\"hello\""))).isEmpty();
        }

        @Test
        void refusesAnythingElse() {
            assertThat(messages(string(), node("7"))).containsExactly("must be a string");
            assertThat(messages(string(), NullNode.getInstance())).containsExactly("must be a string");
        }

        @Test
        void checksAMinimumAndAMaximumLength() {
            assertThat(messages(string().min(3), node("\"ab\""))).containsExactly("must be at least 3 characters");
            assertThat(messages(string().max(2), node("\"abc\""))).containsExactly("must be at most 2 characters");
        }

        @Test
        void checksAPattern() {
            Schema lowerCase = string().pattern(Pattern.compile("^[a-z]+$"));
            assertThat(messages(lowerCase, node("\"Abc\"")).get(0)).contains("must match");
            assertThat(messages(lowerCase, node("\"abc\""))).isEmpty();
        }
    }

    @Nested
    class Numbers {

        @Test
        void acceptsANumber() {
            assertThat(messages(number(), node("4"))).isEmpty();
        }

        @Test
        void refusesAnythingThatIsNotOne() {
            assertThat(messages(number(), node("\"4\""))).containsExactly("must be a number");
            assertThat(messages(number(), DoubleNode.valueOf(Double.NaN))).containsExactly("must be a number");
        }

        @Test
        void checksWholeNumbersAndBounds() {
            assertThat(messages(number().integer(), node("1.5"))).containsExactly("must be a whole number");
            assertThat(messages(number().min(3), node("2"))).containsExactly("must be at least 3");
            assertThat(messages(number().max(3), node("4"))).containsExactly("must be at most 3");
        }
    }

    @Nested
    class BooleanAndOneOf {

        @Test
        void acceptsTrueAndFalse() {
            assertThat(messages(bool(), node("true"))).isEmpty();
            assertThat(messages(bool(), node("\"yes\""))).containsExactly("must be true or false");
        }

        @Test
        void acceptsAListedValue() {
            assertThat(messages(oneOf(List.of("a", "b")), node("\"b\""))).isEmpty();
            assertThat(messages(oneOf(List.of("a", "b")), node("\"c\""))).containsExactly("must be one of a, b");
        }

        @Test
        void refusesAChoiceWithNothingToChooseFrom() {
            assertThatThrownBy(() -> oneOf(List.of())).hasMessageContaining("something to choose from");
        }
    }

    @Nested
    class ArrayOf {

        @Test
        void checksEveryEntry() {
            assertThat(messages(arrayOf(number()), node("[1, 2]"))).isEmpty();
            assertThat(messages(arrayOf(number()), node("[1, \"two\"]"))).containsExactly("must be a number");
        }

        @Test
        void namesTheEntryThatWasWrong() {
            List<Problem> problems = arrayOf(number()).problems(node("[1, \"two\"]"), "bells");
            assertThat(problems.get(0).path()).isEqualTo("bells[1]");
        }

        @Test
        void refusesSomethingThatIsNotAList() {
            assertThat(messages(arrayOf(number()), node("\"one\""))).containsExactly("must be a list");
        }

        @Test
        void checksHowManyEntriesThereAre() {
            assertThat(messages(arrayOf(number()).min(2), node("[1]"))).containsExactly("must have at least 2 entries");
            assertThat(messages(arrayOf(number()).max(1), node("[1, 2]")))
                    .containsExactly("must have at most 1 entries");
        }
    }

    @Nested
    class Objects {

        private final Schema person = object(fields("name", string().min(1), "age", number().integer()));

        @Test
        void checksEveryField() {
            assertThat(messages(person, node("{\"name\": \"Alice\", \"age\": 40}")))
                    .isEmpty();
        }

        @Test
        void reportsEveryFieldThatIsWrongAtOnce() {
            List<Problem> problems = person.problems(node("{\"name\": \"\", \"age\": \"old\"}"), "");
            assertThat(problems).hasSize(2);
            assertThat(problems).extracting(Problem::path).containsExactly("name", "age");
        }

        @Test
        void ignoresFieldsTheSchemaDoesNotKnowAbout() {
            assertThat(messages(person, node("{\"name\": \"Alice\", \"age\": 40, \"colour\": \"blue\"}")))
                    .isEmpty();
        }

        @Test
        void refusesSomethingThatIsNotAnObject() {
            assertThat(messages(person, node("[]"))).containsExactly("must be an object");
            assertThat(messages(person, NullNode.getInstance())).containsExactly("must be an object");
        }

        @Test
        void namesNestedPaths() {
            Schema nested = object(fields("inner", object(fields("n", number()))));
            assertThat(nested.problems(node("{\"inner\": {\"n\": \"x\"}}"), "")
                            .get(0)
                            .path())
                    .isEqualTo("inner.n");
        }

        @Test
        void refusesASchemaWithNoFields() {
            assertThatThrownBy(() -> object(null)).hasMessageContaining("needs its fields");
        }
    }

    @Nested
    class OptionalFields {

        private final Schema record = object(fields("name", string(), "note", optional(string())));

        @Test
        void acceptsAMissingOptionalField() {
            assertThat(messages(record, node("{\"name\": \"Alice\"}"))).isEmpty();
        }

        @Test
        void stillChecksItWhenItIsThere() {
            assertThat(messages(record, node("{\"name\": \"Alice\", \"note\": 7}")))
                    .containsExactly("must be a string");
        }

        @Test
        void carriesADefaultThatAReaderCanUse() {
            Schemas.Defaulted field = withDefault(number(), 8);
            assertThat(field.fallback()).isEqualTo(8);
            assertThat(messages(field, MissingNode.getInstance())).isEmpty();
            assertThat(messages(field, node("\"x\""))).containsExactly("must be a number");
        }
    }

    @Nested
    class CheckAndValidate {

        private final Schema person = object(fields("name", string().min(1)));

        @Test
        void givesTheValueBackWhenItIsSound() {
            assertThat(check(person, node("{\"name\": \"Alice\"}"))).isEqualTo(node("{\"name\": \"Alice\"}"));
        }

        @Test
        void throwsWithTheFirstProblemAndKeepsThemAll() {
            ValidationException error = catchThrowableOfType(
                    ValidationException.class, () -> check(person, node("{\"name\": \"\"}"), "the ringer"));
            assertThat(error).isNotNull();
            assertThat(error.getMessage()).isEqualTo("the ringer is wrong: name must be at least 1 characters");
            assertThat(error.issues()).hasSize(1);
        }

        @Test
        void reportsRatherThanThrowing() {
            assertThat(validate(person, node("{\"name\": \"Alice\"}"))).isEqualTo(new Schemas.Verdict(true, List.of()));
            assertThat(validate(person, node("{}")).ok()).isFalse();
        }

        @Test
        void namesTheRootWhenTheWholeValueIsWrong() {
            assertThat(validate(person, node("\"Alice\"")).problems().get(0).path())
                    .isEqualTo("(root)");
        }

        @Test
        void acceptsAnythingThroughTheAnythingSchema() {
            assertThat(messages(anything(), MissingNode.getInstance())).isEmpty();
            assertThat(messages(anything(), node("{\"a\": 1}"))).isEmpty();
        }
    }
}
