package io.github.rkaushikdev.garterhole.core;

import static io.github.rkaushikdev.garterhole.core.RowParse.parseRow;
import static io.github.rkaushikdev.garterhole.core.RowParse.parseRows;
import static io.github.rkaushikdev.garterhole.core.RowParse.tryParseRow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RowParseTest {

    @Nested
    class ParseRow {

        @Test
        void readsARowOfDigits() {
            Row row = parseRow("13527486");
            assertThat(row.stage()).isEqualTo(8);
            assertThat(row).hasToString("13527486");
        }

        @Test
        void readsTheLettersUsedAboveNineBells() {
            assertThat(parseRow("1234567890ET").stage()).isEqualTo(12);
            assertThat(parseRow("1234567890et")).hasToString("1234567890ET");
        }

        @Test
        void ignoresSurroundingWhitespace() {
            assertThat(parseRow("  1324  ")).hasToString("1324");
        }

        @Test
        void namesTheOffendingCharacterAndItsPosition() {
            assertThatThrownBy(() -> parseRow("132z")).hasMessage("\"z\" at position 4 of \"132z\" is not a bell");
        }

        @Test
        void refusesARowThatIsNotAPermutation() {
            assertThatThrownBy(() -> parseRow("1123")).hasMessageContaining("appears more than once");
            assertThatThrownBy(() -> parseRow("1235")).hasMessageContaining("outside 1..4");
        }

        @Test
        void refusesBlankInput() {
            assertThatThrownBy(() -> parseRow("")).hasMessageContaining("a row must be a non empty string");
            assertThatThrownBy(() -> parseRow("   ")).hasMessageContaining("must not be blank");
        }

        @Test
        void checksTheStageWhenOneIsGiven() {
            assertThat(parseRow("123456", 6).stage()).isEqualTo(6);
            assertThatThrownBy(() -> parseRow("123456", 8)).hasMessage("row \"123456\" has 6 bells but the stage is 8");
        }

        @Test
        void refusesAnUnsupportedStage() {
            assertThatThrownBy(() -> parseRow("12", 2)).hasMessageContaining("stage 2 is not supported");
        }
    }

    @Nested
    class ParseRows {

        @Test
        void readsRowsSeparatedByWhitespace() {
            List<Row> rows = parseRows("1234 2143 2413");
            assertThat(rows).extracting(Row::toString).containsExactly("1234", "2143", "2413");
        }

        @Test
        void readsRowsSeparatedByCommasAndNewlines() {
            assertThat(parseRows("1234,\n2143;2413")).hasSize(3);
        }

        @Test
        void readsRowsSeparatedByTheSpacesJavaScriptCountsAsSpace() {
            assertThat(parseRows("\u00A01234\u20032143\uFEFF"))
                    .extracting(Row::toString)
                    .containsExactly("1234", "2143");
        }

        @Test
        void givesAnEmptyListForBlankInput() {
            assertThat(parseRows("")).isEmpty();
            assertThat(parseRows("   \n ")).isEmpty();
        }

        @Test
        void takesTheStageFromTheFirstRowWhenNoneIsGiven() {
            assertThatThrownBy(() -> parseRows("123456 1234")).hasMessageContaining("has 4 bells but the stage is 6");
        }

        @Test
        void checksEveryRowAgainstAnExplicitStage() {
            assertThatThrownBy(() -> parseRows("1234 2143", 6)).hasMessageContaining("the stage is 6");
        }

        @Test
        void refusesInputThatIsNotAString() {
            assertThatThrownBy(() -> parseRows(null)).hasMessageContaining("must be a string");
        }
    }

    @Nested
    class TryParseRow {

        @Test
        void reportsSuccessWithTheRow() {
            RowParse.Attempt result = tryParseRow("2143");
            assertThat(result.ok()).isTrue();
            assertThat(((RowParse.Parsed) result).row()).hasToString("2143");
        }

        @Test
        void reportsFailureWithTheMessageInsteadOfThrowing() {
            RowParse.Attempt result = tryParseRow("21q3");
            assertThat(result.ok()).isFalse();
            assertThat(((RowParse.Refused) result).message()).contains("is not a bell");
        }

        @Test
        void passesTheStageThrough() {
            assertThat(tryParseRow("2143", 6).ok()).isFalse();
        }
    }
}
