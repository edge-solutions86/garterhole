package io.github.rkaushikdev.garterhole.validation;

import static io.github.rkaushikdev.garterhole.validation.Types.changeCount;
import static io.github.rkaushikdev.garterhole.validation.Types.isoDate;
import static io.github.rkaushikdev.garterhole.validation.Types.methodTitle;
import static io.github.rkaushikdev.garterhole.validation.Types.placeNotation;
import static io.github.rkaushikdev.garterhole.validation.Types.row;
import static io.github.rkaushikdev.garterhole.validation.Types.stage;
import static io.github.rkaushikdev.garterhole.validation.Types.touch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TypesTest {

    private static List<String> messages(Schema schema, Object value) {
        JsonNode node = value instanceof JsonNode given ? given : Json.toNode(value);
        return schema.problems(node, "").stream().map(Problem::message).toList();
    }

    @Nested
    class StageType {

        @Test
        void acceptsAStageThisLibraryWorksWith() {
            assertThat(messages(stage(), 8)).isEmpty();
            assertThat(messages(stage(), 3)).isEmpty();
        }

        @Test
        void refusesOneOutsideTheRange() {
            assertThat(messages(stage(), 2).get(0)).contains("at least 3");
            assertThat(messages(stage(), 17).get(0)).contains("at most 16");
        }

        @Test
        void refusesSomethingThatIsNotAWholeNumber() {
            assertThat(messages(stage(), 6.5)).containsExactly("must be a whole number");
            assertThat(messages(stage(), "8")).containsExactly("must be a number");
        }
    }

    @Nested
    class RowType {

        @Test
        void acceptsARow() {
            assertThat(messages(row(), "13527486")).isEmpty();
        }

        @Test
        void refusesOneThatIsNotAPermutation() {
            assertThat(messages(row(), "1123").get(0)).contains("appears more than once");
        }

        @Test
        void checksTheStageWhenOneIsGiven() {
            assertThat(messages(row(8), "123456").get(0)).contains("but the stage is 8");
        }

        @Test
        void refusesSomethingThatIsNotText() {
            assertThat(messages(row(), 12345678)).containsExactly("must be a string");
            assertThat(messages(row(), "")).containsExactly("must be at least 1 characters");
        }
    }

    @Nested
    class PlaceNotationType {

        @Test
        void acceptsNotationThatExpands() {
            assertThat(messages(placeNotation(6), "x1x1x1,2")).isEmpty();
        }

        @Test
        void refusesNotationWithABadSymbol() {
            assertThat(messages(placeNotation(6), "x1?x1").get(0)).contains("not part of place notation");
        }

        @Test
        void refusesPlacesThatCannotMakeAChange() {
            assertThat(messages(placeNotation(8), "x13x").get(0)).contains("would leave");
        }

        @Test
        void refusesSomethingThatIsNotText() {
            assertThat(messages(placeNotation(6), NullNode.getInstance())).containsExactly("must be a string");
        }
    }

    @Nested
    class MethodTitleType {

        @Test
        void acceptsATitle() {
            assertThat(messages(methodTitle(), "Cambridge Surprise Major")).isEmpty();
            assertThat(messages(methodTitle(), "Stedman Triples")).isEmpty();
        }

        @Test
        void refusesOneWithNoStageOnTheEnd() {
            assertThat(messages(methodTitle(), "Cambridge Surprise")).containsExactly("is not a method title");
        }
    }

    @Nested
    class TouchType {

        @Test
        void acceptsBothWritingStyles() {
            assertThat(messages(touch(), "3(H)")).isEmpty();
            assertThat(messages(touch(), "-.-.s-")).isEmpty();
        }

        @Test
        void refusesOneItCannotRead() {
            assertThat(messages(touch(), "W)").get(0)).contains("without opening");
        }
    }

    @Nested
    class IsoDateType {

        @Test
        void acceptsADateAFormSends() {
            assertThat(messages(isoDate(), "2025-05-18")).isEmpty();
        }

        @Test
        void refusesOneWrittenAnyOtherWay() {
            assertThat(messages(isoDate(), "18/05/2025")).containsExactly("must be a date like 2025-05-18");
            assertThat(messages(isoDate(), 20250518)).containsExactly("must be a date like 2025-05-18");
        }

        @Test
        void refusesALineBreakAfterTheDate() {
            assertThat(messages(isoDate(), "2025-05-18\n")).containsExactly("must be a date like 2025-05-18");
        }

        @Test
        void refusesADayThatDoesNotExist() {
            assertThat(messages(isoDate(), "2025-02-30")).containsExactly("is not a real date");
            assertThat(messages(isoDate(), "2025-13-01")).containsExactly("is not a real date");
        }
    }

    @Nested
    class ChangeCountType {

        @Test
        void acceptsALength() {
            assertThat(messages(changeCount(), 5040)).isEmpty();
        }

        @Test
        void refusesNothingAndRefusesNonsense() {
            assertThat(messages(changeCount(), 0)).containsExactly("must be at least 1");
            assertThat(messages(changeCount(), 1000000)).containsExactly("must be at most 100000");
        }
    }

    @Nested
    class TheTypesTogether {

        private final Schema request = Schemas.object(fields());

        private static Map<String, Schema> fields() {
            Map<String, Schema> fields = new LinkedHashMap<>();
            fields.put("title", methodTitle());
            fields.put("touch", touch());
            fields.put("stage", stage());
            return fields;
        }

        @Test
        void acceptsASoundRequest() {
            assertThatCode(() -> Schemas.check(
                            request, Json.parse("{\"title\":\"Plain Bob Major\",\"touch\":\"3(H)\",\"stage\":8}")))
                    .doesNotThrowAnyException();
        }

        @Test
        void reportsEveryFieldThatIsWrongAtOnce() {
            List<Problem> problems =
                    request.problems(Json.parse("{\"title\":\"nope\",\"touch\":\"W)\",\"stage\":2}"), "");
            assertThat(problems).hasSize(3);
            assertThat(problems).extracting(Problem::path).containsExactly("title", "touch", "stage");
        }
    }
}
