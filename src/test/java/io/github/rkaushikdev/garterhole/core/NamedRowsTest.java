package io.github.rkaushikdev.garterhole.core;

import static io.github.rkaushikdev.garterhole.core.NamedRows.backrounds;
import static io.github.rkaushikdev.garterhole.core.NamedRows.kings;
import static io.github.rkaushikdev.garterhole.core.NamedRows.nameOfRow;
import static io.github.rkaushikdev.garterhole.core.NamedRows.namedRows;
import static io.github.rkaushikdev.garterhole.core.NamedRows.queens;
import static io.github.rkaushikdev.garterhole.core.NamedRows.tittums;
import static io.github.rkaushikdev.garterhole.core.NamedRows.whittingtons;
import static io.github.rkaushikdev.garterhole.core.RowParse.parseRow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NamedRowsTest {

    @Nested
    class Queens {

        @Test
        void putsTheOddBellsFirstOnMajor() {
            assertThat(queens(8)).hasToString("13572468");
        }

        @Test
        void worksOnMaximus() {
            assertThat(queens(12)).hasToString("13579E24680T");
        }

        @Test
        void usesEveryBellOfAnOddStageSinceTheCoverIsNotOneOfThem() {
            assertThat(queens(5)).hasToString("13524");
        }
    }

    @Nested
    class Kings {

        @Test
        void runsTheOddBellsDownAndTheEvenBellsUp() {
            assertThat(kings(8)).hasToString("75312468");
        }

        @Test
        void isRoundsTurnedAroundOnlyInPart() {
            assertThat(kings(6)).hasToString("531246");
        }
    }

    @Nested
    class Tittums {

        @Test
        void interleavesTheTwoHalves() {
            assertThat(tittums(8)).hasToString("15263748");
            assertThat(tittums(6)).hasToString("142536");
        }

        @Test
        void interleavesAnOddStageAsEvenlyAsItCan() {
            assertThat(tittums(7)).hasToString("1526374");
            assertThat(tittums(5)).hasToString("14253");
        }
    }

    @Nested
    class Backrounds {

        @Test
        void reversesTheChangingBells() {
            assertThat(backrounds(8)).hasToString("87654321");
        }

        @Test
        void reversesAnOddStageAsWell() {
            assertThat(backrounds(5)).hasToString("54321");
        }
    }

    @Nested
    class Whittingtons {

        @Test
        void isTheRowOnEight() {
            assertThat(whittingtons(8)).hasToString("12753468");
        }

        @Test
        void doesNotExistAtAnyOtherStage() {
            assertThatThrownBy(() -> whittingtons(6)).hasMessage("Whittingtons is only rung on eight bells");
            assertThatThrownBy(() -> whittingtons(12)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class NamedRowsAtAStage {

        @Test
        void listsTheRowsThatHaveNames() {
            Map<String, Row> named = namedRows(8);
            assertThat(named.keySet())
                    .containsExactly("Rounds", "Backrounds", "Queens", "Kings", "Tittums", "Whittingtons");
        }

        @Test
        void leavesWhittingtonsOutAtOtherStages() {
            assertThat(namedRows(6)).doesNotContainKey("Whittingtons");
        }

        @Test
        void givesRowsAtTheStageThatWasAskedFor() {
            for (Row row : namedRows(10).values()) {
                assertThat(row.stage()).isEqualTo(10);
            }
        }

        @Test
        void refusesAnUnsupportedStage() {
            assertThatThrownBy(() -> namedRows(2)).hasMessageContaining("stage 2 is not supported");
        }
    }

    @Nested
    class NameOfRow {

        @Test
        void namesARowThatHasOne() {
            assertThat(nameOfRow(Row.rounds(8))).hasValue("Rounds");
            assertThat(nameOfRow(parseRow("13572468"))).hasValue("Queens");
            assertThat(nameOfRow(parseRow("87654321"))).hasValue("Backrounds");
        }

        @Test
        void givesNothingForAnOrdinaryRow() {
            assertThat(nameOfRow(parseRow("13527486"))).isEmpty();
        }

        @Test
        void refusesSomethingThatIsNotARow() {
            assertThatThrownBy(() -> nameOfRow(null)).hasMessageContaining("a row is needed");
        }
    }
}
