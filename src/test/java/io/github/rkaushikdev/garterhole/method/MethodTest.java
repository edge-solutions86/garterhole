package io.github.rkaushikdev.garterhole.method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.core.Change;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.core.RowBlock;
import io.github.rkaushikdev.garterhole.core.RowParse;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MethodTest {

    private static Method plainBobMinor() {
        return Method.of("Plain Bob", 6, "x1x1x1,2");
    }

    private static Method cambridgeMinor() {
        return Method.of("Cambridge Surprise", 6, "x36x14x12x36x14x56,12");
    }

    private static Method grandsireDoubles() {
        return Method.of("Grandsire", 5, "3,1.5.1.5.1");
    }

    @Nested
    class MethodConstruction {

        @Test
        void expandsTheNotationIntoALead() {
            Method bob = plainBobMinor();
            assertThat(bob.leadLength()).isEqualTo(12);
            assertThat(bob.stage()).isEqualTo(6);
            assertThat(bob.name()).isEqualTo("Plain Bob");
        }

        @Test
        void isFrozen() {
            Method bob = plainBobMinor();
            assertThat(Modifier.isFinal(Method.class.getModifiers())).isTrue();
            assertThatThrownBy(() -> bob.changes().add(Change.cross(6)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void trimsTheName() {
            assertThat(Method.of("  Plain Bob  ", 6, "x1x1x1,2").name()).isEqualTo("Plain Bob");
        }

        @Test
        void keepsTheNotationTheAuthorWrote() {
            assertThat(plainBobMinor().source()).isEqualTo("x1x1x1,2");
        }

        @Test
        void refusesADefinitionThatIsNotAnObject() {
            assertThatThrownBy(() -> new Method(null)).hasMessage("a method needs a definition object");
            assertThatThrownBy(() -> new Method(null)).isInstanceOf(ValidationException.class);
        }

        @Test
        void refusesAMissingNameOrNotation() {
            assertThatThrownBy(() -> Method.of("", 6, "x1x1x1,2"))
                    .hasMessageContaining("a method name must be a non empty string");
            assertThatThrownBy(() -> Method.of("Plain Bob", 6, ""))
                    .hasMessageContaining("place notation must be a non empty string");
        }

        @Test
        void refusesAnUnsupportedStage() {
            assertThatThrownBy(() -> Method.of("Plain Bob", 2, "x1")).hasMessage("stage 2 is not supported");
        }

        @Test
        void passesANotationErrorStraightThrough() {
            assertThatThrownBy(() -> Method.of("Nonsense", 6, "x1?x1"))
                    .hasMessageContaining("not part of place notation");
        }
    }

    @Nested
    class MethodNotation {

        @Test
        void writesTheNotationBackInTheHouseStyle() {
            assertThat(plainBobMinor().notation()).isEqualTo("x16x16x16,12");
            assertThat(grandsireDoubles().notation()).isEqualTo("3,1.5.1.5.1");
        }

        @Test
        void writesTheNotationOutInFullWhenAsked() {
            assertThat(plainBobMinor().fullNotation()).isEqualTo("x16x16x16x16x16x12");
        }

        @Test
        void namesTheStage() {
            assertThat(plainBobMinor().stageName()).isEqualTo("Minor");
            assertThat(grandsireDoubles().stageName()).isEqualTo("Doubles");
        }

        @Test
        void printsItself() {
            assertThat(plainBobMinor()).hasToString("Plain Bob (Minor) x16x16x16,12");
        }
    }

    @Nested
    class RingingALead {

        @Test
        void ringsAPlainLeadFromRounds() {
            RowBlock lead = plainBobMinor().plainLead();
            assertThat(lead.length()).isEqualTo(13);
            assertThat(lead.first()).hasToString("123456");
            assertThat(lead.last()).hasToString("135264");
        }

        @Test
        void ringsAPlainLeadFromAnywhere() {
            RowBlock lead = plainBobMinor().plainLead(RowParse.parseRow("135264"));
            assertThat(lead.first()).hasToString("135264");
            assertThat(lead.last()).hasToString("156342");
        }

        @Test
        void givesTheLeadHeadAndTheLeadEnd() {
            Method bob = plainBobMinor();
            assertThat(bob.leadHead()).hasToString("135264");
            assertThat(bob.leadEnd()).hasToString("132546");
        }

        @Test
        void givesTheLeadHeadOfCambridgeSurpriseMinor() {
            assertThat(cambridgeMinor().leadHead()).hasToString("156342");
            assertThat(cambridgeMinor().leadLength()).isEqualTo(24);
        }

        @Test
        void givesTheLeadHeadOfGrandsireDoubles() {
            assertThat(grandsireDoubles().leadHead()).hasToString("12534");
            assertThat(grandsireDoubles().leadLength()).isEqualTo(10);
        }

        @Test
        void namesTheChangeACallWouldReplace() {
            assertThat(plainBobMinor().leadEndChange()).hasToString("12");
            assertThat(cambridgeMinor().leadEndChange()).hasToString("12");
        }

        @Test
        void refusesAStartingRowAtTheWrongStage() {
            assertThatThrownBy(() -> plainBobMinor().plainLead(Row.rounds(8)))
                    .hasMessage("a method on 6 cannot start from a row of 8");
        }
    }

    @Nested
    class RingsTheSameAs {

        @Test
        void seesThroughADifferentName() {
            assertThat(plainBobMinor().ringsTheSameAs(Method.of("Anything", 6, "x1x1x1,2")))
                    .isTrue();
        }

        @Test
        void seesThroughADifferentSpellingOfTheSameNotation() {
            assertThat(plainBobMinor().ringsTheSameAs(Method.of("Plain Bob", 6, "-16-16-16-16-16-12")))
                    .isTrue();
        }

        @Test
        void tellsTwoDifferentMethodsApart() {
            assertThat(plainBobMinor().ringsTheSameAs(cambridgeMinor())).isFalse();
        }

        @Test
        void tellsTwoStagesApart() {
            assertThat(plainBobMinor().ringsTheSameAs(Method.of("Plain Bob", 8, "x18x18x18x18,12")))
                    .isFalse();
        }

        @Test
        void isFalseForAnythingThatIsNotAMethod() {
            assertThat(plainBobMinor().ringsTheSameAs(null)).isFalse();
        }
    }
}
