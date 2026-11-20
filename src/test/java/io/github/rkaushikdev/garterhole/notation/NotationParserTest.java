package io.github.rkaushikdev.garterhole.notation;

import static io.github.rkaushikdev.garterhole.notation.NotationParser.parseChanges;
import static io.github.rkaushikdev.garterhole.notation.NotationParser.parseNotation;
import static io.github.rkaushikdev.garterhole.notation.NotationParser.parseSection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.garterhole.NotationException;
import io.github.rkaushikdev.garterhole.core.Change;
import io.github.rkaushikdev.garterhole.notation.NotationParser.ParsedNotation;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NotationParserTest {

    private static List<String> written(List<Change> changes) {
        return changes.stream().map(Change::toString).toList();
    }

    @Nested
    class ParseChanges {

        @Test
        void readsARunOfCrossChangesAndPlaces() {
            assertThat(written(parseChanges("x18x18", 8))).containsExactly("x", "18", "x", "18");
        }

        @Test
        void fillsInThePlacesTheNotationLeavesOut() {
            assertThat(written(parseChanges("x1x1", 6))).containsExactly("x", "16", "x", "16");
            assertThat(written(parseChanges("x2x2", 8))).containsExactly("x", "12", "x", "12");
        }

        @Test
        void readsPlacesSeparatedByFullStops() {
            assertThat(written(parseChanges("5.1.5.1", 5))).containsExactly("5", "1", "5", "1");
        }

        @Test
        void readsTheLettersUsedAboveNineBells() {
            assertThat(written(parseChanges("x1Tx1T", 12))).containsExactly("x", "1T", "x", "1T");
        }

        @Test
        void acceptsADashForACrossChange() {
            assertThat(written(parseChanges("-18-18", 8))).containsExactly("x", "18", "x", "18");
        }

        @Test
        void refusesNotationThatStillHasAFoldInIt() {
            assertThatThrownBy(() -> parseChanges("x18x18,12", 8))
                    .hasMessageContaining("has a fold and has to be expanded");
        }

        @Test
        void refusesAnUnsupportedStage() {
            assertThatThrownBy(() -> parseChanges("x1x1", 2)).hasMessageContaining("stage 2 is not supported");
        }
    }

    @Nested
    class ParseNotation {

        @Test
        void keepsTheSectionsACommaDivides() {
            ParsedNotation parsed = parseNotation("x18x18,12", 8);
            assertThat(parsed.folded()).isTrue();
            assertThat(parsed.sections()).hasSize(2);
            assertThat(written(parsed.sections().get(0))).containsExactly("x", "18", "x", "18");
            assertThat(written(parsed.sections().get(1))).containsExactly("12");
        }

        @Test
        void reportsOneSectionWhenThereIsNoComma() {
            ParsedNotation parsed = parseNotation("x1x1x1", 6);
            assertThat(parsed.folded()).isFalse();
            assertThat(parsed.sections()).hasSize(1);
        }

        @Test
        void keepsTheSourceForTheErrorMessages() {
            assertThat(parseNotation("x1x1", 6).source()).isEqualTo("x1x1");
        }

        @Test
        void refusesAnEmptySection() {
            assertThatThrownBy(() -> parseNotation("x1,", 6)).hasMessage("section 2 of the notation is empty");
            assertThatThrownBy(() -> parseNotation(",x1", 6)).hasMessage("section 1 of the notation is empty");
            assertThatThrownBy(() -> parseNotation("", 6)).hasMessage("section 1 of the notation is empty");
        }
    }

    @Nested
    class SeparatorRules {

        @Test
        void refusesALeadingSeparator() {
            assertThatThrownBy(() -> parseChanges(".x1", 6)).hasMessage("place notation cannot start with a separator");
        }

        @Test
        void refusesATrailingSeparator() {
            assertThatThrownBy(() -> parseChanges("x1.", 6)).hasMessage("place notation cannot end with a separator");
        }

        @Test
        void refusesTwoSeparatorsInARow() {
            assertThatThrownBy(() -> parseChanges("5..1", 5)).hasMessage("place notation has two separators in a row");
        }

        @Test
        void reportsWhereTheOffendingSeparatorIs() {
            NotationException error = catchThrowableOfType(NotationException.class, () -> parseChanges("5..1", 5));
            assertThat(error).isInstanceOf(NotationException.class);
            assertThat(error.position()).hasValue(3);
        }

        @Test
        void allowsASeparatorBetweenACrossAndARunOfPlaces() {
            assertThat(written(parseChanges("x.18.x.18", 8))).containsExactly("x", "18", "x", "18");
        }
    }

    @Nested
    class BadPlaces {

        @Test
        void namesTheRunThatCouldNotBeMadeIntoAChange() {
            assertThatThrownBy(() -> parseChanges("x13x", 8)).hasMessageContaining("\"13\"");
            assertThatThrownBy(() -> parseChanges("x13x", 8)).hasMessageContaining("would leave");
        }

        @Test
        void refusesAPlaceAboveTheStage() {
            assertThatThrownBy(() -> parseChanges("x18x", 6)).hasMessageContaining("place 8 is outside 1..6");
        }

        @Test
        void reportsThePositionOfTheBadRun() {
            NotationException error =
                    catchThrowableOfType(NotationException.class, () -> parseChanges("x18x18x13x18", 8));
            assertThat(error.position()).hasValue(8);
        }
    }

    @Nested
    class ParseSection {

        @Test
        void takesTokensDirectly() {
            assertThat(written(parseSection(Tokenizer.tokenize("x18"), 8))).containsExactly("x", "18");
        }

        @Test
        void givesNothingForAnEmptyTokenStream() {
            assertThat(parseSection(List.of(), 8)).isEmpty();
        }
    }
}
