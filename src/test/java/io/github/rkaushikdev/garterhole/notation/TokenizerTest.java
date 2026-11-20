package io.github.rkaushikdev.garterhole.notation;

import static io.github.rkaushikdev.garterhole.notation.Tokenizer.TokenType.CROSS;
import static io.github.rkaushikdev.garterhole.notation.Tokenizer.TokenType.FOLD;
import static io.github.rkaushikdev.garterhole.notation.Tokenizer.TokenType.PLACES;
import static io.github.rkaushikdev.garterhole.notation.Tokenizer.TokenType.SEPARATOR;
import static io.github.rkaushikdev.garterhole.notation.Tokenizer.hasFold;
import static io.github.rkaushikdev.garterhole.notation.Tokenizer.renderTokens;
import static io.github.rkaushikdev.garterhole.notation.Tokenizer.splitAtFolds;
import static io.github.rkaushikdev.garterhole.notation.Tokenizer.tokenize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.garterhole.NotationException;
import io.github.rkaushikdev.garterhole.notation.Tokenizer.Token;
import io.github.rkaushikdev.garterhole.notation.Tokenizer.TokenType;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TokenizerTest {

    private static List<TokenType> types(String text) {
        return tokenize(text).stream().map(Token::type).toList();
    }

    @Nested
    class Tokenize {

        @Test
        void readsACrossChangeWrittenAnyOfTheThreeWays() {
            assertThat(types("x")).containsExactly(CROSS);
            assertThat(types("X")).containsExactly(CROSS);
            assertThat(types("-")).containsExactly(CROSS);
        }

        @Test
        void readsARunOfPlacesAsOneToken() {
            List<Token> tokens = tokenize("1258");
            assertThat(tokens).hasSize(1);
            assertThat(tokens.get(0)).isEqualTo(new Token(PLACES, "1258", 1));
        }

        @Test
        void upperCasesTheLettersUsedForTheHigherBells() {
            assertThat(tokenize("1et").get(0).text()).isEqualTo("1ET");
        }

        @Test
        void readsSeparatorsAndFolds() {
            assertThat(types("3,1.5.1")).containsExactly(PLACES, FOLD, PLACES, SEPARATOR, PLACES, SEPARATOR, PLACES);
        }

        @Test
        void recordsWhereEachTokenStarted() {
            assertThat(tokenize("x18x18")).extracting(Token::position).containsExactly(1, 2, 4, 5);
        }

        @Test
        void skipsWhitespaceWithoutDisturbingThePositions() {
            List<Token> tokens = tokenize("x 18\tx");
            assertThat(tokens).extracting(Token::type).containsExactly(CROSS, PLACES, CROSS);
            assertThat(tokens.get(1).position()).isEqualTo(3);
        }

        @Test
        void givesAnEmptyStreamForEmptyInput() {
            assertThat(tokenize("")).isEmpty();
            assertThat(tokenize("   ")).isEmpty();
        }

        @Test
        void refusesACharacterThatIsNotNotation() {
            assertThatThrownBy(() -> tokenize("x18/x")).isInstanceOf(NotationException.class);
            assertThatThrownBy(() -> tokenize("x18/x")).hasMessage("\"/\" at position 4 is not part of place notation");
        }

        @Test
        void reportsThePositionOfTheFirstBadCharacterOnly() {
            NotationException error = catchThrowableOfType(NotationException.class, () -> tokenize("12?34?"));
            assertThat(error.position()).hasValue(3);
            assertThat(error.source()).hasValue("12?34?");
        }

        @Test
        void refusesInputThatIsNotAString() {
            assertThatThrownBy(() -> tokenize(null)).hasMessageContaining("must be a string");
            assertThatThrownBy(() -> tokenize(null)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class HasFold {

        @Test
        void findsAComma() {
            assertThat(hasFold(tokenize("x18x18,12"))).isTrue();
        }

        @Test
        void reportsNoneWhenTheNotationIsWrittenOutInFull() {
            assertThat(hasFold(tokenize("x18x18x18x12"))).isFalse();
        }
    }

    @Nested
    class SplitAtFolds {

        @Test
        void givesOneSectionWhenThereIsNoFold() {
            List<List<Token>> sections = splitAtFolds(tokenize("x1x1"));
            assertThat(sections).hasSize(1);
            assertThat(sections.get(0)).hasSize(4);
        }

        @Test
        void splitsEitherSideOfTheComma() {
            List<List<Token>> sections = splitAtFolds(tokenize("x18x18,12"));
            assertThat(sections).hasSize(2);
            assertThat(renderTokens(sections.get(0))).isEqualTo("x18x18");
            assertThat(renderTokens(sections.get(1))).isEqualTo("12");
        }

        @Test
        void keepsAnEmptySectionWhenAFoldIsAtTheEnd() {
            List<List<Token>> sections = splitAtFolds(tokenize("x1,"));
            assertThat(sections).hasSize(2);
            assertThat(sections.get(1)).isEmpty();
        }
    }

    @Nested
    class RenderTokens {

        @Test
        void roundTripsNotationThatWasAlreadyCanonical() {
            for (String source : List.of("x18x18x18x12", "3,1.5.1.5.1", "x1x1x1,2", "-38-14-1258,16")) {
                String canonical = source.replace('-', 'x');
                assertThat(renderTokens(tokenize(source))).isEqualTo(canonical);
            }
        }

        @Test
        void dropsWhitespace() {
            assertThat(renderTokens(tokenize(" x 18 x "))).isEqualTo("x18x");
        }

        @Test
        void refusesATokenItDoesNotKnow() {
            // The kinds of token are a closed set, so a token of an unknown kind cannot be made,
            // and every kind there is can be written back out.
            assertThat(TokenType.values()).containsExactly(CROSS, PLACES, SEPARATOR, FOLD);
            for (TokenType type : TokenType.values()) {
                assertThat(renderTokens(List.of(new Token(type, "1", 1)))).isNotEmpty();
            }
        }
    }
}
