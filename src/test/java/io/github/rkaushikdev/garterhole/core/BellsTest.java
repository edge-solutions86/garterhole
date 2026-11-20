package io.github.rkaushikdev.garterhole.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BellsTest {

    @Nested
    class Symbol {

        @Test
        void writesTheSingleDigitBellsAsDigits() {
            assertThat(Bells.symbol(1)).isEqualTo('1');
            assertThat(Bells.symbol(9)).isEqualTo('9');
        }

        @Test
        void writesTheTenthBellAsZero() {
            assertThat(Bells.symbol(10)).isEqualTo('0');
        }

        @Test
        void writesTheEleventhAndTwelfthAsEAndT() {
            assertThat(Bells.symbol(11)).isEqualTo('E');
            assertThat(Bells.symbol(12)).isEqualTo('T');
        }

        @Test
        void carriesOnWithLettersAboveTwelve() {
            assertThat(Bells.symbol(13)).isEqualTo('A');
            assertThat(Bells.symbol(16)).isEqualTo('D');
        }

        @Test
        void refusesABellOutsideTheSupportedRange() {
            assertThatThrownBy(() -> Bells.symbol(0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Bells.symbol(Stage.MAX + 1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Bells.symbol(-3)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class FromSymbol {

        @Test
        void roundTripsEveryBell() {
            for (int bell = 1; bell <= Stage.MAX; bell += 1) {
                assertThat(Bells.fromSymbol(Bells.symbol(bell))).isEqualTo(bell);
            }
        }

        @Test
        void acceptsLowerCaseLetters() {
            assertThat(Bells.fromSymbol('e')).isEqualTo(11);
            assertThat(Bells.fromSymbol('t')).isEqualTo(12);
            assertThat(Bells.fromSymbol('d')).isEqualTo(16);
        }

        @Test
        void refusesACharacterThatIsNotABell() {
            assertThatThrownBy(() -> Bells.fromSymbol('x')).hasMessageContaining("not a bell symbol");
            assertThatThrownBy(() -> Bells.fromSymbol('-')).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class IsSymbol {

        @Test
        void recognisesTheWholeAlphabet() {
            for (char character : "1234567890ETABCD".toCharArray()) {
                assertThat(Bells.isSymbol(character)).isTrue();
            }
        }

        @Test
        void rejectsThePlaceNotationCrossSymbol() {
            assertThat(Bells.isSymbol('x')).isFalse();
            assertThat(Bells.isSymbol('X')).isFalse();
            assertThat(Bells.isSymbol('.')).isFalse();
        }
    }

    @Nested
    class SymbolsForStage {

        @Test
        void givesOneSymbolPerBell() {
            for (int stage : Stage.supported()) {
                assertThat(Bells.symbolsForStage(stage)).hasSize(stage);
            }
        }

        @Test
        void givesRoundsOnTwelve() {
            assertThat(Bells.symbolsForStage(12)).isEqualTo("1234567890ET");
        }

        @Test
        void refusesAnUnwritableStage() {
            assertThatThrownBy(() -> Bells.symbolsForStage(Stage.MAX + 1)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class BellSequences {

        @Test
        void readsARunOfSymbols() {
            assertThat(Bells.fromSymbols("1357")).containsExactly(1, 3, 5, 7);
            assertThat(Bells.fromSymbols("0ET")).containsExactly(10, 11, 12);
        }

        @Test
        void writesARunOfBells() {
            assertThat(Bells.toSymbols(1, 3, 5, 7)).isEqualTo("1357");
            assertThat(Bells.toSymbols(10, 11, 12)).isEqualTo("0ET");
        }

        @Test
        void roundTripsAWholeStage() {
            String rounds = Bells.symbolsForStage(16);
            assertThat(Bells.toSymbols(Bells.fromSymbols(rounds))).isEqualTo(rounds);
        }
    }

    @Nested
    class NamedBells {

        @Test
        void callsBellOneTheTreble() {
            assertThat(Bells.TREBLE).isEqualTo(1);
        }

        @Test
        void callsTheHighestNumberedBellTheTenor() {
            assertThat(Bells.tenorOf(8)).isEqualTo(8);
            assertThat(Bells.tenorOf(12)).isEqualTo(12);
        }
    }
}
