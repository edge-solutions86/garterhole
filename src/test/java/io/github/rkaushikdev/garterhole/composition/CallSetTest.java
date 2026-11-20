package io.github.rkaushikdev.garterhole.composition;

import static io.github.rkaushikdev.garterhole.composition.CallSet.callBySymbol;
import static io.github.rkaushikdev.garterhole.composition.CallSet.fourthsPlaceCalls;
import static io.github.rkaushikdev.garterhole.composition.CallSet.placeCall;
import static io.github.rkaushikdev.garterhole.composition.CallSet.standardCalls;
import static io.github.rkaushikdev.garterhole.composition.CallSet.symbolsOf;
import static io.github.rkaushikdev.garterhole.composition.CallSet.thirdsPlaceCalls;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.method.Method;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CallSetTest {

    private static Method plainBobMajor() {
        return Method.of("Plain", 8, "x18x18x18x18,12");
    }

    private static Method grandsireDoubles() {
        return Method.of("Grandsire", 5, "3,1.5.1.5.1");
    }

    private static Method cambridgeMajor() {
        return Method.of("Cambridge", 8, "x38x14x1258x36x14x58x16x78,12");
    }

    @AfterEach
    void forgetTheSharedRegistry() {
        CallSet.resetRegistry();
    }

    @Nested
    class FourthsPlaceCalls {

        @Test
        void makesABobInFourthsAndASingleUnderIt() {
            Map<String, Call> calls = fourthsPlaceCalls(8);
            assertThat(calls.get("bob").notation()).isEqualTo("14");
            assertThat(calls.get("single").notation()).isEqualTo("1234");
        }

        @Test
        void fillsInTheImpliedPlacesAtAStageThatNeedsThem() {
            assertThat(fourthsPlaceCalls(6).get("bob").notation()).isEqualTo("14");
            assertThat(fourthsPlaceCalls(5).get("bob").notation()).isEqualTo("145");
        }

        @Test
        void refusesAStageTooSmallToMakeFourths() {
            assertThatThrownBy(() -> fourthsPlaceCalls(4)).hasMessageContaining("at least five bells, not 4");
        }
    }

    @Nested
    class ThirdsPlaceCalls {

        @Test
        void makesABobInThirds() {
            assertThat(thirdsPlaceCalls(5).get("bob").notation()).isEqualTo("3");
            assertThat(thirdsPlaceCalls(7).get("bob").notation()).isEqualTo("3");
        }

        @Test
        void holdsTwoBellsUnderTheSingle() {
            assertThat(thirdsPlaceCalls(5).get("single").notation()).isEqualTo("123");
        }

        @Test
        void refusesTooSmallAStage() {
            assertThatThrownBy(() -> thirdsPlaceCalls(4)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class StandardCalls {

        @Test
        void givesTheFourthsPlacePairToAMethodWithOneHuntBell() {
            assertThat(standardCalls(plainBobMajor()).get("bob").notation()).isEqualTo("14");
            assertThat(standardCalls(cambridgeMajor()).get("bob").notation()).isEqualTo("14");
        }

        @Test
        void givesTheThirdsPlacePairToGrandsire() {
            assertThat(standardCalls(grandsireDoubles()).get("bob").notation()).isEqualTo("3");
        }

        @Test
        void includesThePlainLead() {
            assertThat(standardCalls(plainBobMajor()).get("plain").isPlain()).isTrue();
        }

        @Test
        void refusesSomethingThatIsNotAMethod() {
            assertThatThrownBy(() -> standardCalls(null)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class PlaceCall {

        @Test
        void buildsACallThatMakesANamedPlace() {
            assertThat(placeCall(8, 6).notation()).isEqualTo("16");
            assertThat(placeCall(8, 6).name()).isEqualTo("6ths place bob");
        }

        @Test
        void takesANameAndASymbol() {
            Call named = placeCall(8, 6, "Middle", "m");
            assertThat(named.name()).isEqualTo("Middle");
            assertThat(named.symbol()).isEqualTo("m");
        }

        @Test
        void refusesAPlaceOutsideTheStage() {
            assertThatThrownBy(() -> placeCall(6, 9)).hasMessageContaining("place 9 is outside");
        }
    }

    @Nested
    class CallBySymbol {

        private final Map<String, Call> calls = standardCalls(plainBobMajor());

        @Test
        void findsACallByItsSymbol() {
            assertThat(callBySymbol(calls, "-").orElseThrow().name()).isEqualTo("Bob");
            assertThat(callBySymbol(calls, "s").orElseThrow().name()).isEqualTo("Single");
            assertThat(callBySymbol(calls, ".").orElseThrow().name()).isEqualTo("Plain");
        }

        @Test
        void givesNothingForASymbolNobodyUses() {
            assertThat(callBySymbol(calls, "z")).isEmpty();
        }
    }

    @Nested
    class SymbolsOf {

        @Test
        void listsEverySymbol() {
            assertThat(symbolsOf(standardCalls(plainBobMajor()))).containsExactlyInAnyOrder("-", ".", "s");
        }

        @Test
        void givesNothingForAnEmptySet() {
            assertThat(symbolsOf(Map.of())).isEmpty();
        }
    }
}
