package io.github.rkaushikdev.garterhole.composition;

import static io.github.rkaushikdev.garterhole.composition.TouchExpander.MAX_LEADS;
import static io.github.rkaushikdev.garterhole.composition.TouchExpander.expandTouch;
import static io.github.rkaushikdev.garterhole.composition.TouchExpander.touchLength;
import static io.github.rkaushikdev.garterhole.composition.TouchParser.parseTouch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.composition.TouchExpander.Request;
import io.github.rkaushikdev.garterhole.composition.TouchExpander.Rung;
import io.github.rkaushikdev.garterhole.composition.TouchExpander.RungLead;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.core.RowBlock;
import io.github.rkaushikdev.garterhole.method.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TouchExpanderTest {

    private static Method plainBobMajor() {
        return Method.of("Plain", 8, "x18x18x18x18,12");
    }

    private static Method plainBobMinor() {
        return Method.of("Plain", 6, "x1x1x1,2");
    }

    private static Rung ring(Method method, String text) {
        return expandTouch(method, parseTouch(text));
    }

    private static Rung ring(Method method, String text, UnaryOperator<Request> options) {
        return expandTouch(options.apply(Request.of(method, parseTouch(text))));
    }

    @Nested
    class TouchesWrittenAsOneSymbolPerLead {

        @Test
        void ringsAPlainCourse() {
            Rung rung = ring(plainBobMinor(), ".....");
            assertThat(rung.block().changeCount()).isEqualTo(60);
            assertThat(rung.comesRound()).isTrue();
            assertThat(rung.leads()).hasSize(5);
        }

        @Test
        void ringsABobbedTouchThatComesRound() {
            Rung rung = ring(plainBobMinor(), "2(-..)");
            assertThat(rung.block().changeCount()).isEqualTo(72);
            assertThat(rung.comesRound()).isTrue();
        }

        @Test
        void reportsATouchThatDoesNotComeRoundRatherThanRingingOn() {
            Rung rung = ring(plainBobMinor(), "-.-.-.");
            assertThat(rung.comesRound()).isFalse();
            assertThat(rung.block().changeCount()).isEqualTo(72);
        }

        @Test
        void recordsTheCallMadeAtEveryLead() {
            Rung rung = ring(plainBobMinor(), "-..");
            assertThat(rung.leads()).extracting(RungLead::symbol).containsExactly("-", ".", ".");
            assertThat(rung.leads()).allMatch(lead -> lead.position().isEmpty());
        }

        @Test
        void measuresTheLength() {
            assertThat(touchLength(ring(plainBobMinor(), "....."))).isEqualTo(60);
        }
    }

    @Nested
    class TouchesWrittenAsCallingPositions {

        @Test
        void ringsThreeBobsHomeOnMajor() {
            Rung rung = ring(plainBobMajor(), "3(H)");
            assertThat(rung.block().changeCount()).isEqualTo(336);
            assertThat(rung.comesRound()).isTrue();
            assertThat(rung.leads()).hasSize(21);
        }

        @Test
        void ringsThreeBobsHomeOnMinor() {
            Rung rung = ring(plainBobMinor(), "3(H)");
            assertThat(rung.block().changeCount()).isEqualTo(180);
            assertThat(rung.comesRound()).isTrue();
        }

        @Test
        void isTrue() {
            Rung rung = ring(plainBobMajor(), "3(H)");
            RowBlock body = rung.block().slice(0, rung.block().length() - 1);
            assertThat(body.isTrue()).isTrue();
        }

        @Test
        void recordsThePositionOfEveryCall() {
            Rung rung = ring(plainBobMajor(), "3(H)");
            List<RungLead> called = rung.leads().stream()
                    .filter(lead -> lead.symbol().equals("-"))
                    .toList();
            assertThat(called).hasSize(3);
            assertThat(called).allMatch(lead -> lead.position().equals(Optional.of("H")));
        }

        @Test
        void ringsThePlainLeadsInBetweenWithoutAPosition() {
            Rung rung = ring(plainBobMajor(), "3(H)");
            assertThat(rung.leads().stream().filter(lead -> lead.position().isEmpty()))
                    .hasSize(18);
        }

        @Test
        void reportsACallingThatNeverComesRound() {
            assertThatThrownBy(() -> ring(plainBobMinor(), "5(H)")).hasMessageContaining("never comes round");
        }

        @Test
        void canBeToldNotToRingOnToRounds() {
            Rung rung = ring(plainBobMajor(), "H", request -> request.withRingToRounds(false));
            assertThat(rung.comesRound()).isFalse();
            assertThat(rung.leads()).hasSize(7);
        }
    }

    @Nested
    class ChoosingTheCalls {

        @Test
        void usesTheStandardCallsWhenNoneAreGiven() {
            Rung rung = ring(plainBobMajor(), "3(H)");
            assertThat(rung.leads()).anyMatch(lead -> lead.symbol().equals("-"));
        }

        @Test
        void takesACallSet() {
            Map<String, Call> calls = CallSet.standardCalls(plainBobMajor());
            Rung rung = ring(plainBobMajor(), "3(H)", request -> request.withCalls(calls));
            assertThat(rung.comesRound()).isTrue();
        }

        @Test
        void refusesASymbolNoCallUses() {
            assertThatThrownBy(() -> ring(plainBobMinor(), "z..")).hasMessageContaining("no call is written \"z\"");
        }

        @Test
        void takesADifferentObservationBell() {
            Rung rung = ring(plainBobMajor(), "3(H)", request -> request.withObservation(7));
            assertThat(rung.comesRound()).isTrue();
        }

        @Test
        void refusesAPositionTheObservationBellNeverReaches() {
            assertThatThrownBy(() -> ring(plainBobMinor(), "B")).hasMessageContaining("never reaches \"B\"");
        }
    }

    @Nested
    class BadInput {

        @Test
        void refusesSomethingThatIsNotAMethod() {
            assertThatThrownBy(() -> expandTouch(null, parseTouch(""))).isInstanceOf(ValidationException.class);
        }

        @Test
        void refusesSomethingThatIsNotAParsedTouch() {
            assertThatThrownBy(() -> expandTouch(plainBobMinor(), null))
                    .hasMessageContaining("a parsed touch is needed");
        }

        @Test
        void startsFromARowItIsGiven() {
            Rung rung = ring(plainBobMinor(), ".....", request -> request.withStart(Row.rounds(6)));
            assertThat(rung.block().first().isRounds()).isTrue();
        }

        @Test
        void hasACeilingOnHowLongATouchMayRun() {
            assertThat(MAX_LEADS).isGreaterThan(100);
        }
    }
}
