package io.github.rkaushikdev.garterhole.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.composition.Composition.Definition;
import io.github.rkaushikdev.garterhole.method.Method;
import io.github.rkaushikdev.garterhole.util.Json;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CompositionTest {

    private static Method plainBobMajor() {
        return Method.of("Plain", 8, "x18x18x18x18,12");
    }

    private static Composition sound() {
        return new Composition(new Definition("A Ringer", plainBobMajor(), "3(H)").withClaimedLength(336));
    }

    @Nested
    class TheComposition {

        @Test
        void keepsWhatItWasGiven() {
            Composition composition = sound();
            assertThat(composition.composer()).isEqualTo("A Ringer");
            assertThat(composition.touch()).isEqualTo("3(H)");
            assertThat(composition.claimedLength()).hasValue(336);
        }

        @Test
        void isFrozen() {
            assertThat(Modifier.isFinal(Composition.class.getModifiers())).isTrue();
            assertThat(Arrays.stream(Composition.class.getDeclaredFields())
                            .filter(field -> !Modifier.isStatic(field.getModifiers()))
                            .map(Field::getModifiers))
                    .allMatch(Modifier::isFinal);
        }

        @Test
        void squashesStrayWhitespaceInTheCalling() {
            Composition composition = new Composition(new Definition("  A Ringer ", plainBobMajor(), " 3 (H) "));
            assertThat(composition.composer()).isEqualTo("A Ringer");
            assertThat(composition.touch()).isEqualTo("3 (H)");
        }

        @Test
        void titlesItselfTheWayAPealBoardWould() {
            assertThat(sound().title()).isEqualTo("336 Plain Bob Major");
            assertThat(sound()).hasToString("336 Plain Bob Major by A Ringer");
        }

        @Test
        void leavesTheLengthOutOfTheTitleWhenNoneWasClaimed() {
            Composition composition = new Composition(new Definition("A Ringer", plainBobMajor(), "3(H)"));
            assertThat(composition.title()).isEqualTo("Plain Bob Major");
        }

        @Test
        void refusesADefinitionThatIsNotAnObject() {
            assertThatThrownBy(() -> new Composition(null)).hasMessage("a composition needs a definition object");
        }

        @Test
        void refusesAMissingComposerOrCalling() {
            assertThatThrownBy(() -> new Composition(new Definition("", plainBobMajor(), "3(H)")))
                    .hasMessageContaining("a composer must be a non empty string");
            assertThatThrownBy(() -> new Composition(new Definition("A", plainBobMajor(), "")))
                    .hasMessageContaining("a calling must be a non empty string");
        }

        @Test
        void refusesSomethingThatIsNotAMethod() {
            assertThatThrownBy(() -> new Composition(new Definition("A", null, "3(H)")))
                    .hasMessage("a composition needs a Method");
        }

        @Test
        void refusesAClaimedLengthThatIsNotAPositiveWholeNumber() {
            assertThatThrownBy(() -> new Composition(new Definition("A", plainBobMajor(), "3(H)").withClaimedLength(0)))
                    .hasMessageContaining("cannot be 0 changes long");
        }
    }

    @Nested
    class AnalysingAComposition {

        @Test
        void ringsItAndReports() {
            assertThat(sound().analyse().length()).isEqualTo(336);
        }

        @Test
        void findsNothingWrongWithASoundComposition() {
            assertThat(sound().problems()).isEmpty();
            assertThat(sound().isSound()).isTrue();
        }

        @Test
        void catchesALengthThatDoesNotMatchTheCalling() {
            Composition wrong =
                    new Composition(new Definition("A Ringer", plainBobMajor(), "3(H)").withClaimedLength(1280));
            assertThat(wrong.isSound()).isFalse();
            assertThat(wrong.problems().get(0)).contains("gives 336 changes, not the 1280 claimed");
        }
    }

    @Nested
    class SerialisingAComposition {

        @Test
        void writesTheFormatAndVersion() {
            ObjectNode document = Composition.toJson(sound());
            assertThat(document.get("format").asText()).isEqualTo("garterhole.composition");
            assertThat(document.get("version").asInt()).isEqualTo(1);
        }

        @Test
        void roundTrips() {
            JsonNode document = Json.parse(Json.stringify(Composition.toJson(sound())));
            Composition rebuilt = Composition.fromJson(document);
            assertThat(rebuilt.composer()).isEqualTo("A Ringer");
            assertThat(rebuilt.touch()).isEqualTo("3(H)");
            assertThat(rebuilt.claimedLength()).hasValue(336);
            assertThat(rebuilt.method().ringsTheSameAs(plainBobMajor())).isTrue();
        }

        @Test
        void writesAMissingLengthAsNullAndReadsItBackAsMissing() {
            Composition composition = new Composition(new Definition("A Ringer", plainBobMajor(), "3(H)"));
            ObjectNode document = Composition.toJson(composition);
            assertThat(document.get("claimedLength").isNull()).isTrue();
            assertThat(Composition.fromJson(document).claimedLength()).isEmpty();
        }

        @Test
        void refusesAVersionItDoesNotKnow() {
            assertThatThrownBy(() -> Composition.fromJson(Json.parse("{\"version\": 2}")))
                    .hasMessageContaining("the document is version 2");
        }

        @Test
        void refusesSomethingThatIsNotAComposition() {
            assertThatThrownBy(() -> Composition.toJson(null)).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> Composition.fromJson(null)).hasMessageContaining("must be an object");
        }
    }
}
