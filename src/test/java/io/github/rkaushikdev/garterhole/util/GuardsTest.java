package io.github.rkaushikdev.garterhole.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GuardsTest {

    @Nested
    class IntegerGuard {

        @Test
        void returnsTheValueItWasGiven() {
            assertThat(Guards.integer(7, "lead")).isEqualTo(7);
            assertThat(Guards.integer(0, "lead")).isZero();
            assertThat(Guards.integer(-3, "lead")).isEqualTo(-3);
        }

        @Test
        void refusesAFraction() {
            assertThatThrownBy(() -> Guards.integer(1.5, "lead"))
                    .hasMessageContaining("lead 1.5 is not a whole number");
        }

        @Test
        void refusesAStringThatLooksLikeANumber() {
            assertThatThrownBy(() -> Guards.integer("7", "lead"))
                    .hasMessageContaining("lead \"7\" is not a whole number");
        }

        @Test
        void refusesNaNAndInfinity() {
            assertThatThrownBy(() -> Guards.integer(Double.NaN, "lead")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Guards.integer(Double.POSITIVE_INFINITY, "lead"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class IntegerInRange {

        @Test
        void acceptsBothEndsOfTheRange() {
            assertThat(Guards.integerInRange(1, 1, 8, "bell")).isEqualTo(1);
            assertThat(Guards.integerInRange(8, 1, 8, "bell")).isEqualTo(8);
        }

        @Test
        void namesTheRangeInTheMessage() {
            assertThatThrownBy(() -> Guards.integerInRange(9, 1, 8, "bell")).hasMessage("bell 9 is outside 1..8");
            assertThatThrownBy(() -> Guards.integerInRange(0, 1, 8, "bell")).hasMessage("bell 0 is outside 1..8");
        }

        @Test
        void refusesAValueThatIsNotANumberAtAll() {
            assertThatThrownBy(() -> Guards.integerInRange((Object) null, 1, 8, "bell"))
                    .hasMessage("bell null is outside 1..8");
        }
    }

    @Nested
    class NonEmptyString {

        @Test
        void acceptsAStringWithContent() {
            assertThat(Guards.nonEmptyString("Cambridge", "title")).isEqualTo("Cambridge");
        }

        @Test
        void refusesTheEmptyString() {
            assertThatThrownBy(() -> Guards.nonEmptyString("", "title")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void refusesANonString() {
            assertThatThrownBy(() -> Guards.nonEmptyString(12, "title"))
                    .hasMessageContaining("title must be a non empty string");
            assertThatThrownBy(() -> Guards.nonEmptyString(null, "title")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class OneOf {

        @Test
        void acceptsAListedValue() {
            assertThat(Guards.oneOf("bob", List.of("plain", "bob", "single"), "call"))
                    .isEqualTo("bob");
        }

        @Test
        void listsTheAlternativesWhenItRefuses() {
            assertThatThrownBy(() -> Guards.oneOf("extreme", List.of("plain", "bob", "single"), "call"))
                    .hasMessage("call \"extreme\" must be one of plain, bob, single");
        }
    }

    @Nested
    class ArrayGuards {

        @Test
        void acceptsAnArray() {
            assertThat(Guards.array(List.of(1, 2), "places")).isEqualTo(List.of(1, 2));
        }

        @Test
        void refusesAStringWhichIsIterableButNotAnArray() {
            assertThatThrownBy(() -> Guards.array("12", "places")).hasMessageContaining("places must be an array");
        }

        @Test
        void refusesAnEmptyArrayOnlyWhereContentIsRequired() {
            assertThat(Guards.array(List.of(), "places")).isEqualTo(List.of());
            assertThatThrownBy(() -> Guards.nonEmptyArray(List.of(), "places"))
                    .hasMessageContaining("places must not be empty");
        }
    }

    @Nested
    class FunctionGuard {

        @Test
        void acceptsAFunction() {
            Supplier<Integer> fn = () -> 1;
            assertThat(Guards.function(fn, "callback")).isSameAs(fn);
        }

        @Test
        void refusesAnythingElse() {
            assertThatThrownBy(() -> Guards.function((Function<String, String>) null, "callback"))
                    .hasMessageContaining("callback must be a function");
        }
    }

    @Nested
    class PlainObject {

        @Test
        void acceptsAnObjectLiteral() {
            assertThat(Guards.plainObject(Map.of("a", 1), "options")).isEqualTo(Map.of("a", 1));
        }

        @Test
        void acceptsAnObjectWithNoParticularOrder() {
            Map<String, Integer> bare = new TreeMap<>();
            assertThat(Guards.plainObject(bare, "options")).isSameAs(bare);
        }

        @Test
        void refusesNullAnArrayAndAClassInstance() {
            assertThatThrownBy(() -> Guards.plainObject(null, "options"))
                    .hasMessageContaining("options must be an object");
            assertThatThrownBy(() -> Guards.plainObject(List.of(), "options"))
                    .hasMessageContaining("options must be an object");
            assertThatThrownBy(() -> Guards.plainObject(Instant.EPOCH, "options"))
                    .hasMessageContaining("options must be a plain object");
        }
    }

    @Nested
    class Describe {

        @Test
        void quotesStringsSoThatPaddingIsVisible() {
            assertThat(Guards.describe(" x ")).isEqualTo("\" x \"");
        }

        @Test
        void printsNumbersBooleansAndNullAsTheyAre() {
            assertThat(Guards.describe(4)).isEqualTo("4");
            assertThat(Guards.describe(true)).isEqualTo("true");
            assertThat(Guards.describe(null)).isEqualTo("null");
            assertThat(Guards.describe(4.0)).isEqualTo("4");
        }

        @Test
        void summarisesAnArrayRatherThanDumpingIt() {
            assertThat(Guards.describe(List.of(1, 2, 3))).isEqualTo("an array of 3");
        }

        @Test
        void fallsBackToTheTypeName() {
            assertThat(Guards.describe(Map.of())).isEqualTo("object");
            Supplier<Integer> fn = () -> 1;
            assertThat(Guards.describe(fn)).isEqualTo("function");
        }
    }
}
