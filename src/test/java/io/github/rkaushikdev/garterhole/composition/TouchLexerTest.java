package io.github.rkaushikdev.garterhole.composition;

import static io.github.rkaushikdev.garterhole.composition.TouchLexer.TokenType.CALL;
import static io.github.rkaushikdev.garterhole.composition.TouchLexer.TokenType.CLOSE;
import static io.github.rkaushikdev.garterhole.composition.TouchLexer.TokenType.NUMBER;
import static io.github.rkaushikdev.garterhole.composition.TouchLexer.TokenType.OPEN;
import static io.github.rkaushikdev.garterhole.composition.TouchLexer.TokenType.PART;
import static io.github.rkaushikdev.garterhole.composition.TouchLexer.TokenType.POSITION;
import static io.github.rkaushikdev.garterhole.composition.TouchLexer.checkBracketsBalance;
import static io.github.rkaushikdev.garterhole.composition.TouchLexer.lexTouch;
import static io.github.rkaushikdev.garterhole.composition.TouchLexer.renderTouchTokens;
import static io.github.rkaushikdev.garterhole.composition.TouchLexer.usesCallingPositions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.NotationException;
import io.github.rkaushikdev.garterhole.composition.TouchLexer.TokenType;
import io.github.rkaushikdev.garterhole.composition.TouchLexer.TouchToken;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TouchLexerTest {

    private static List<TokenType> types(String text) {
        return lexTouch(text).stream().map(TouchToken::type).toList();
    }

    @Nested
    class LexTouch {

        @Test
        void readsCallSymbols() {
            assertThat(types("-s.")).containsExactly(CALL, CALL, CALL);
        }

        @Test
        void readsCallingPositions() {
            assertThat(types("WMH")).containsExactly(POSITION, POSITION, POSITION);
        }

        @Test
        void readsASingleAtACallingPositionAsTwoTokens() {
            List<TouchToken> tokens = lexTouch("sH");
            assertThat(tokens).extracting(TouchToken::type).containsExactly(CALL, POSITION);
            assertThat(tokens).extracting(TouchToken::text).containsExactly("s", "H");
        }

        @Test
        void readsRepeatCountsAsWholeNumbers() {
            List<TouchToken> tokens = lexTouch("12(WH)");
            assertThat(tokens.get(0)).isEqualTo(new TouchToken(NUMBER, "12", 1));
            assertThat(tokens.get(1).type()).isEqualTo(OPEN);
        }

        @Test
        void readsBracketsAndCommas() {
            assertThat(types("(W),(H)")).containsExactly(OPEN, POSITION, CLOSE, PART, OPEN, POSITION, CLOSE);
        }

        @Test
        void skipsWhitespaceButKeepsThePositionsHonest() {
            assertThat(lexTouch("W  H")).extracting(TouchToken::position).containsExactly(1, 4);
        }

        @Test
        void givesNothingForEmptyInput() {
            assertThat(lexTouch("")).isEmpty();
            assertThat(lexTouch("   ")).isEmpty();
        }

        @Test
        void refusesACharacterItDoesNotKnow() {
            assertThatThrownBy(() -> lexTouch("W*H")).isInstanceOf(NotationException.class);
            assertThatThrownBy(() -> lexTouch("W*H")).hasMessage("\"*\" at position 2 is not part of a touch");
        }

        @Test
        void refusesInputThatIsNotAString() {
            assertThatThrownBy(() -> lexTouch(null)).hasMessageContaining("must be a string");
        }
    }

    @Nested
    class UsesCallingPositions {

        @Test
        void isTrueWhenAnUpperCaseLetterAppears() {
            assertThat(usesCallingPositions(lexTouch("WsH"))).isTrue();
        }

        @Test
        void isFalseForATouchWrittenAsOneSymbolPerLead() {
            assertThat(usesCallingPositions(lexTouch("-.-.s-"))).isFalse();
        }

        @Test
        void isFalseForAnEmptyTouch() {
            assertThat(usesCallingPositions(List.of())).isFalse();
        }
    }

    @Nested
    class CheckBracketsBalance {

        @Test
        void acceptsBalancedBrackets() {
            assertThatCode(() -> checkBracketsBalance(lexTouch("3(W(H))"))).doesNotThrowAnyException();
        }

        @Test
        void acceptsATouchWithNoBrackets() {
            assertThatCode(() -> checkBracketsBalance(lexTouch("WH"))).doesNotThrowAnyException();
        }

        @Test
        void namesThePositionOfAStrayClosingBracket() {
            assertThatThrownBy(() -> checkBracketsBalance(lexTouch("W)H")))
                    .hasMessage("a bracket closes at position 2 without opening");
        }

        @Test
        void namesThePositionOfABracketThatNeverCloses() {
            assertThatThrownBy(() -> checkBracketsBalance(lexTouch("3(WH")))
                    .hasMessage("the bracket at position 2 is never closed");
        }
    }

    @Nested
    class RenderTouchTokens {

        @Test
        void writesTheTokensBackWithoutTheWhitespace() {
            assertThat(renderTouchTokens(lexTouch(" 3 (W s H) "))).isEqualTo("3(WsH)");
        }

        @Test
        void givesAnEmptyStringForNoTokens() {
            assertThat(renderTouchTokens(List.of())).isEmpty();
        }
    }
}
