package io.github.rkaushikdev.garterhole.notation;

import static io.github.rkaushikdev.garterhole.notation.Expansion.expandNotation;
import static io.github.rkaushikdev.garterhole.notation.Expansion.symmetryPoints;
import static io.github.rkaushikdev.garterhole.notation.NotationFormat.foldChanges;
import static io.github.rkaushikdev.garterhole.notation.NotationFormat.writeChanges;
import static io.github.rkaushikdev.garterhole.notation.NotationFormat.writeFolded;
import static io.github.rkaushikdev.garterhole.notation.NotationFormat.writeLeadLines;
import static io.github.rkaushikdev.garterhole.notation.NotationParser.parseChanges;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.core.Change;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.notation.NotationFormat.Folded;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NotationFormatTest {

    private static List<Row> ring(List<Change> changes, int stage) {
        List<Row> rows = new ArrayList<>();
        rows.add(Row.rounds(stage));
        for (Change change : changes) {
            rows.add(change.apply(rows.get(rows.size() - 1)));
        }
        return rows;
    }

    @Nested
    class WriteChanges {

        @Test
        void putsNoSeparatorNextToACrossChange() {
            assertThat(writeChanges(parseChanges("x18x18", 8))).isEqualTo("x18x18");
        }

        @Test
        void separatesTwoRunsOfPlacesWithAFullStop() {
            assertThat(writeChanges(parseChanges("5.1.5.1", 5))).isEqualTo("5.1.5.1");
        }

        @Test
        void writesPlacesInFullEvenWhereTheReaderWouldHaveImpliedThem() {
            assertThat(writeChanges(parseChanges("x1x1", 6))).isEqualTo("x16x16");
        }

        @Test
        void givesAnEmptyStringForNoChanges() {
            assertThat(writeChanges(List.of())).isEmpty();
        }

        @Test
        void roundTripsThroughTheReader() {
            for (Map.Entry<String, Integer> entry : Map.of("x18x18x18x18", 8, "5.1.5.1.5.1", 5, "x36x14x12x36", 6)
                    .entrySet()) {
                List<Change> changes = parseChanges(entry.getKey(), entry.getValue());
                assertThat(writeChanges(parseChanges(writeChanges(changes), entry.getValue())))
                        .isEqualTo(writeChanges(changes));
            }
        }
    }

    @Nested
    class FoldChanges {

        @Test
        void splitsPlainBobMinorAtItsTwoAxes() {
            List<Change> changes = expandNotation("x1x1x1,2", 6);
            assertThat(symmetryPoints(changes)).containsExactly(5, 11);
            Folded folded = foldChanges(changes).orElseThrow();
            assertThat(writeChanges(folded.first())).isEqualTo("x16x16x16");
            assertThat(writeChanges(folded.second())).isEqualTo("12");
        }

        @Test
        void splitsGrandsireDoublesWhereTheShortSectionComesFirst() {
            Folded folded = foldChanges(expandNotation("3,1.5.1.5.1", 5)).orElseThrow();
            assertThat(writeChanges(folded.first())).isEqualTo("3");
            assertThat(writeChanges(folded.second())).isEqualTo("1.5.1.5.1");
        }

        @Test
        void givesNothingForALeadWithNoAxis() {
            assertThat(foldChanges(parseChanges("16.14.12", 6))).isEmpty();
        }

        @Test
        void givesNothingForAnEmptyLead() {
            assertThat(foldChanges(List.of())).isEmpty();
        }
    }

    @Nested
    class WriteFolded {

        @Test
        void reproducesTheNotationAMethodCollectionPrints() {
            assertThat(writeFolded(expandNotation("x1x1x1,2", 6))).isEqualTo("x16x16x16,12");
            assertThat(writeFolded(expandNotation("3,1.5.1.5.1", 5))).isEqualTo("3,1.5.1.5.1");
            assertThat(writeFolded(expandNotation("x3x4x2x3x4x5,2", 6))).isEqualTo("x36x14x12x36x14x56,12");
        }

        @Test
        void fallsBackToTheLongFormWhenThereIsNoFold() {
            assertThat(writeFolded(parseChanges("16.14.12", 6))).isEqualTo("16.14.12");
        }

        @Test
        void survivesARoundTripBackThroughTheExpander() {
            for (Map.Entry<String, Integer> entry :
                    Map.of("x1x1x1,2", 6, "3,1.5.1.5.1", 5, "x3x4x2x3x4x5,2", 6).entrySet()) {
                List<Change> first = expandNotation(entry.getKey(), entry.getValue());
                List<Change> second = expandNotation(writeFolded(first), entry.getValue());
                assertThat(writeChanges(second)).isEqualTo(writeChanges(first));
            }
        }
    }

    @Nested
    class WriteLeadLines {

        @Test
        void printsTheStartingRowAndThenOneLinePerChange() {
            List<Change> changes = parseChanges("x16x16", 6);
            assertThat(writeLeadLines(changes, ring(changes, 6)))
                    .containsExactly("    123456", " x  214365", "16  241635", " x  426153", "16  462513");
        }

        @Test
        void padsTheNotationColumnToTheWidestChange() {
            List<Change> changes = parseChanges("x1258x", 8);
            List<String> lines = writeLeadLines(changes, ring(changes, 8));
            assertThat(lines.get(0)).startsWith("     ");
            assertThat(lines.get(1)).startsWith("   x");
        }

        @Test
        void refusesARowCountThatDoesNotMatchTheChanges() {
            List<Change> changes = parseChanges("x16x16", 6);
            assertThatThrownBy(() -> writeLeadLines(changes, List.of(Row.rounds(6))))
                    .hasMessage("expected 5 rows for 4 changes but got 1");
        }
    }
}
