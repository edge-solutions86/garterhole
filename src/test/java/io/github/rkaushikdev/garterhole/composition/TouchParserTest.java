package io.github.rkaushikdev.garterhole.composition;

import static io.github.rkaushikdev.garterhole.composition.TouchParser.DEFAULT_CALL_SYMBOL;
import static io.github.rkaushikdev.garterhole.composition.TouchParser.PLAIN_SYMBOL;
import static io.github.rkaushikdev.garterhole.composition.TouchParser.callsIn;
import static io.github.rkaushikdev.garterhole.composition.TouchParser.leadCount;
import static io.github.rkaushikdev.garterhole.composition.TouchParser.parseTouch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.NotationException;
import io.github.rkaushikdev.garterhole.composition.TouchParser.Instruction;
import io.github.rkaushikdev.garterhole.composition.TouchParser.Style;
import io.github.rkaushikdev.garterhole.composition.TouchParser.Touch;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TouchParserTest {

    private static List<String> shape(String text) {
        return parseTouch(text).instructions().stream()
                .map(instruction ->
                        instruction.symbol() + instruction.position().orElse(""))
                .toList();
    }

    @Nested
    class TouchesWrittenAsOneSymbolPerLead {

        @Test
        void readsASymbolForEveryLead() {
            Touch touch = parseTouch("-.-.s-");
            assertThat(touch.style()).isEqualTo(Style.SYMBOLS);
            assertThat(shape("-.-.s-")).containsExactly("-", ".", "-", ".", "s", "-");
        }

        @Test
        void countsTheLeads() {
            assertThat(leadCount(parseTouch("-.-.s-"))).hasValue(6);
        }

        @Test
        void leavesThePlainLeadsOutOfTheCalls() {
            assertThat(callsIn(parseTouch("-.-.s-"))).hasSize(4);
        }

        @Test
        void keepsTheSource() {
            assertThat(parseTouch("-.-").source()).isEqualTo("-.-");
        }

        @Test
        void readsAnEmptyTouch() {
            Touch touch = parseTouch("");
            assertThat(touch.instructions()).isEmpty();
            assertThat(leadCount(touch)).hasValue(0);
        }
    }

    @Nested
    class TouchesWrittenAsCallingPositions {

        @Test
        void readsAPositionWithNoSymbolAsABob() {
            assertThat(shape("WMH")).containsExactly(DEFAULT_CALL_SYMBOL + "W", "-M", "-H");
        }

        @Test
        void readsASymbolInFrontOfAPosition() {
            assertThat(shape("WsH")).containsExactly("-W", "sH");
        }

        @Test
        void saysTheStyleIsPositions() {
            assertThat(parseTouch("WsH").style()).isEqualTo(Style.POSITIONS);
            assertThat(parseTouch("WsH").style()).hasToString("positions");
        }

        @Test
        void hasNoLeadCountUntilItIsRung() {
            assertThat(leadCount(parseTouch("WsH"))).isEmpty();
        }

        @Test
        void refusesACallWithNoPositionAfterIt() {
            assertThatThrownBy(() -> parseTouch("Ws")).hasMessageContaining("has no calling position after it");
            assertThatThrownBy(() -> parseTouch("s-H")).isInstanceOf(NotationException.class);
        }
    }

    @Nested
    class Repeats {

        @Test
        void expandsACountInFrontOfAGroup() {
            assertThat(shape("3(WH)")).containsExactly("-W", "-H", "-W", "-H", "-W", "-H");
        }

        @Test
        void expandsACountAfterAGroup() {
            assertThat(shape("(WH)2")).containsExactly("-W", "-H", "-W", "-H");
        }

        @Test
        void expandsAGroupWithNoCountOnce() {
            assertThat(shape("(WH)")).containsExactly("-W", "-H");
        }

        @Test
        void expandsNestedGroups() {
            assertThat(shape("2(W2(H))")).containsExactly("-W", "-H", "-H", "-W", "-H", "-H");
        }

        @Test
        void expandsRepeatsInTheSymbolStyleToo() {
            assertThat(shape("3(-.)")).containsExactly("-", ".", "-", ".", "-", ".");
        }

        @Test
        void copiesRatherThanSharingTheRepeatedInstructions() {
            Touch touch = parseTouch("2(W)");
            List<Instruction> instructions = touch.instructions();
            assertThat(instructions.get(0)).isNotSameAs(instructions.get(1));
            assertThat(instructions.get(0)).isEqualTo(instructions.get(1));
        }

        @Test
        void refusesARepeatWithNothingToRepeat() {
            assertThatThrownBy(() -> parseTouch("3W")).hasMessageContaining("has nothing to repeat");
        }

        @Test
        void refusesARepeatCountOfZero() {
            assertThatThrownBy(() -> parseTouch("0(W)")).hasMessageContaining("rings nothing");
        }
    }

    @Nested
    class PartsAndBrackets {

        @Test
        void treatsACommaAsASeparatorAndNothingMore() {
            assertThat(shape("W,H")).containsExactly("-W", "-H");
        }

        @Test
        void allowsACommaInsideAGroup() {
            assertThat(shape("2(W,H)")).containsExactly("-W", "-H", "-W", "-H");
        }

        @Test
        void refusesAStrayClosingBracket() {
            assertThatThrownBy(() -> parseTouch("W)")).hasMessageContaining("without opening");
        }

        @Test
        void refusesABracketThatNeverCloses() {
            assertThatThrownBy(() -> parseTouch("2(WH")).hasMessageContaining("never closed");
        }
    }

    @Nested
    class ThePlainSymbol {

        @Test
        void isTheFullStop() {
            assertThat(PLAIN_SYMBOL).isEqualTo(".");
            assertThat(callsIn(parseTouch("..-..")))
                    .extracting(Instruction::symbol)
                    .containsExactly("-");
        }
    }
}
