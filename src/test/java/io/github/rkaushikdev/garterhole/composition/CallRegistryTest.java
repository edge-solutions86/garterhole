package io.github.rkaushikdev.garterhole.composition;

import static io.github.rkaushikdev.garterhole.composition.CallRegistry.defaultRegistry;
import static io.github.rkaushikdev.garterhole.composition.CallSet.placeCall;
import static io.github.rkaushikdev.garterhole.composition.CallSet.resetRegistry;
import static io.github.rkaushikdev.garterhole.composition.CallSet.sharedRegistry;
import static io.github.rkaushikdev.garterhole.composition.CallSet.standardCalls;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.NotFoundException;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.method.Method;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CallRegistryTest {

    private static Method plainBobMajor() {
        return Method.of("Plain", 8, "x18x18x18x18,12");
    }

    private static Method grandsireDoubles() {
        return Method.of("Grandsire", 5, "3,1.5.1.5.1");
    }

    private static Map<String, Call> sixthsBob(Method method) {
        return Map.of("bob", placeCall(method.stage(), 6));
    }

    @AfterEach
    void forgetTheSharedRegistry() {
        resetRegistry();
    }

    @Nested
    class DefaultRegistry {

        @Test
        void triesTheThirdsPlaceFamilyFirst() {
            assertThat(defaultRegistry().names()).containsExactly("thirds place", "fourths place");
        }

        @Test
        void givesGrandsireItsThirdsPlaceCalls() {
            assertThat(defaultRegistry().entryFor(grandsireDoubles())).isEqualTo("thirds place");
            assertThat(defaultRegistry().callsFor(grandsireDoubles()).get("bob").notation())
                    .isEqualTo("3");
        }

        @Test
        void givesEverythingElseTheFourthsPlaceCalls() {
            assertThat(defaultRegistry().entryFor(plainBobMajor())).isEqualTo("fourths place");
            assertThat(defaultRegistry().callsFor(plainBobMajor()).get("bob").notation())
                    .isEqualTo("14");
        }

        @Test
        void alwaysIncludesThePlainLead() {
            assertThat(defaultRegistry().callsFor(plainBobMajor()).get("plain").isPlain())
                    .isTrue();
        }
    }

    @Nested
    class Registering {

        @Test
        void startsEmptyAndFindsNothing() {
            CallRegistry registry = new CallRegistry();
            assertThat(registry.names()).isEmpty();
            assertThatThrownBy(() -> registry.entryFor(plainBobMajor())).isInstanceOf(NotFoundException.class);
        }

        @Test
        void triesTheMostRecentlyRegisteredEntryFirst() {
            CallRegistry registry =
                    defaultRegistry().register("tower special", method -> true, CallRegistryTest::sixthsBob);
            assertThat(registry.names().get(0)).isEqualTo("tower special");
            assertThat(registry.callsFor(plainBobMajor()).get("bob").notation()).isEqualTo("16");
        }

        @Test
        void letsAnEntryDeclineSoALaterOneTakesIt() {
            CallRegistry registry = defaultRegistry()
                    .register("minor only", method -> method.stage() == 6, CallRegistryTest::sixthsBob);
            assertThat(registry.entryFor(plainBobMajor())).isEqualTo("fourths place");
        }

        @Test
        void refusesAnEntryWithNoName() {
            assertThatThrownBy(() -> new CallRegistry().register("", method -> true, method -> Map.of()))
                    .hasMessageContaining("needs a name");
        }

        @Test
        void refusesAnEntryWithNoTestOrBuilder() {
            assertThatThrownBy(() -> new CallRegistry().register("bad", null, method -> Map.of()))
                    .hasMessageContaining("needs a test and a builder");
        }

        @Test
        void refusesSomethingThatIsNotAMethod() {
            assertThatThrownBy(() -> defaultRegistry().entryFor(null)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class TheSharedRegistry {

        @Test
        void isUsedWhenStandardCallsIsGivenNone() {
            assertThat(standardCalls(plainBobMajor()).get("bob").notation()).isEqualTo("14");
        }

        @Test
        void isTheSameRegistryEveryTime() {
            assertThat(sharedRegistry()).isSameAs(sharedRegistry());
        }

        @Test
        void keepsAnEntryATowerRegisters() {
            sharedRegistry().register("tower special", method -> method.stage() == 8, CallRegistryTest::sixthsBob);
            assertThat(standardCalls(plainBobMajor()).get("bob").notation()).isEqualTo("16");
        }

        @Test
        void isRebuiltAfterAReset() {
            CallRegistry first = sharedRegistry();
            resetRegistry();
            assertThat(sharedRegistry()).isNotSameAs(first);
        }

        @Test
        void takesARegistryOfItsOwnInstead() {
            CallRegistry registry =
                    new CallRegistry().register("everything", method -> true, CallRegistryTest::sixthsBob);
            assertThat(standardCalls(plainBobMajor(), registry).get("bob").notation())
                    .isEqualTo("16");
        }
    }
}
