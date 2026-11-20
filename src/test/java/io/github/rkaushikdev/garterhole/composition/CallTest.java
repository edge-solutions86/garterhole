package io.github.rkaushikdev.garterhole.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.core.Change;
import io.github.rkaushikdev.garterhole.method.Method;
import io.github.rkaushikdev.garterhole.notation.NotationFormat;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CallTest {

    private static Method plainBobMajor() {
        return Method.of("Plain", 8, "x18x18x18x18,12");
    }

    private static Method cambridgeMajor() {
        return Method.of("Cambridge", 8, "x38x14x1258x36x14x58x16x78,12");
    }

    private static Call bob() {
        return Call.of("Bob", "-", 8, "14");
    }

    private static Call single() {
        return Call.of("Single", "s", 8, "1234");
    }

    @Nested
    class CallConstruction {

        @Test
        void keepsTheNameSymbolAndChanges() {
            Call called = bob();
            assertThat(called.name()).isEqualTo("Bob");
            assertThat(called.symbol()).isEqualTo("-");
            assertThat(called.notation()).isEqualTo("14");
            assertThat(called.replaces()).isEqualTo(1);
        }

        @Test
        void isFrozen() {
            assertThat(Modifier.isFinal(Call.class.getModifiers())).isTrue();
            assertThatThrownBy(() -> bob().changes().add(Change.cross(8)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void takesACallThatReplacesMoreThanOneChange() {
            Call longCall = Call.of("Long", "L", 8, "14.16", 2);
            assertThat(longCall.changes()).hasSize(2);
            assertThat(longCall.replaces()).isEqualTo(2);
        }

        @Test
        void defaultsTheReplacedCountToTheNumberOfChangesItSubstitutes() {
            assertThat(Call.of("Long", "L", 8, "14.16").replaces()).isEqualTo(2);
        }

        @Test
        void refusesADefinitionThatIsNotAnObject() {
            assertThatThrownBy(() -> new Call(null)).hasMessage("a call needs a definition object");
        }

        @Test
        void refusesAMissingNameSymbolOrNotation() {
            assertThatThrownBy(() -> Call.of("", "-", 8, "14")).hasMessageContaining("a call name");
            assertThatThrownBy(() -> Call.of("Bob", "", 8, "14")).hasMessageContaining("a call symbol");
            assertThatThrownBy(() -> Call.of("Bob", "-", 8, "")).hasMessageContaining("a call notation");
        }

        @Test
        void refusesAReplacedCountThatIsNotAPositiveWholeNumber() {
            assertThatThrownBy(() -> Call.of("Bob", "-", 8, "14", 0)).hasMessageContaining("cannot replace 0 changes");
            assertThatThrownBy(() -> Call.of("Bob", "-", 8, "14", -2)).isInstanceOf(ValidationException.class);
        }

        @Test
        void passesABadNotationOn() {
            assertThatThrownBy(() -> Call.of("Bob", "-", 8, "13")).hasMessageContaining("would leave");
        }

        @Test
        void printsItself() {
            assertThat(bob()).hasToString("Bob (-) 14");
        }
    }

    @Nested
    class ApplyingACall {

        @Test
        void swapsTheLastChangeOfTheLead() {
            List<Change> changes = bob().applyToLead(plainBobMajor());
            assertThat(changes).hasSize(16);
            assertThat(NotationFormat.writeChanges(changes.subList(14, 16))).isEqualTo("x14");
        }

        @Test
        void leavesTheRestOfTheLeadAlone() {
            List<Change> plainChanges = plainBobMajor().changes();
            List<Change> changes = bob().applyToLead(plainBobMajor());
            assertThat(NotationFormat.writeChanges(changes.subList(0, 15)))
                    .isEqualTo(NotationFormat.writeChanges(plainChanges.subList(0, 15)));
        }

        @Test
        void worksInASurpriseMethodToo() {
            List<Change> changes = single().applyToLead(cambridgeMajor());
            assertThat(changes).hasSize(32);
            assertThat(changes.get(31)).hasToString("1234");
        }

        @Test
        void refusesAMethodAtAnotherStage() {
            assertThatThrownBy(() -> bob().applyToLead(Method.of("Plain", 6, "x1x1x1,2")))
                    .hasMessage("a call on 8 cannot be made in a method on 6");
        }

        @Test
        void refusesToReplaceMoreChangesThanTheLeadHas() {
            Call greedy = Call.of("Greedy", "g", 8, "14.16.14.16.14.16.14.16.14", 9);
            assertThatThrownBy(() -> greedy.applyToLead(Method.of("Short", 8, "x18")))
                    .hasMessageContaining("cannot be made in a lead of 2");
        }

        @Test
        void refusesSomethingThatIsNotAMethod() {
            assertThatThrownBy(() -> bob().applyToLead(null)).hasMessage("a call is applied to a Method");
        }
    }

    @Nested
    class ThePlainCall {

        @Test
        void leavesTheLeadExactlyAsItWas() {
            Call plain = Call.plain(8);
            assertThat(plain.isPlain()).isTrue();
            assertThat(NotationFormat.writeChanges(plain.applyToLead(cambridgeMajor())))
                    .isEqualTo(cambridgeMajor().fullNotation());
        }

        @Test
        void isNotTheSameAsABob() {
            assertThat(bob().isPlain()).isFalse();
        }

        @Test
        void hasANameAndASymbolLikeAnyOtherCall() {
            assertThat(Call.plain(8).name()).isEqualTo("Plain");
            assertThat(Call.plain(8).symbol()).isEqualTo(".");
        }

        @Test
        void stillRefusesSomethingThatIsNotAMethod() {
            assertThatThrownBy(() -> Call.plain(8).applyToLead(null)).hasMessage("a call is applied to a Method");
        }
    }
}
