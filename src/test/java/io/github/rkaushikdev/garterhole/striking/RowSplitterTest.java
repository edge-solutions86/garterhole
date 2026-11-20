package io.github.rkaushikdev.garterhole.striking;

import static io.github.rkaushikdev.garterhole.striking.RowSplitter.handstrokes;
import static io.github.rkaushikdev.garterhole.striking.RowSplitter.rowDurations;
import static io.github.rkaushikdev.garterhole.striking.RowSplitter.splitRows;
import static io.github.rkaushikdev.garterhole.striking.RowSplitter.splitRowsLeniently;
import static io.github.rkaushikdev.garterhole.striking.RowSplitter.trailingBlows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.striking.RowSplitter.LenientSplit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RowSplitterTest {

    private static StrikeStream struckLead() {
        return Ringing.struck(Ringing.plainLead(), 250);
    }

    @Nested
    class SplitRows {

        @Test
        void givesOneRowPerRunOfBlows() {
            List<StruckRow> rows = splitRows(Ringing.struck(Ringing.plainCourse(), 250));
            assertThat(rows).hasSize(61);
            assertThat(rows.get(0).row()).hasToString("123456");
            assertThat(rows.get(1).row()).hasToString("214365");
        }

        @Test
        void recordsWhereEachRowStartedAndFinished() {
            List<StruckRow> rows = splitRows(struckLead());
            assertThat(rows.get(0).start()).isZero();
            assertThat(rows.get(0).end()).isEqualTo(1250);
            assertThat(rows.get(1).start()).isEqualTo(1500);
        }

        @Test
        void numbersTheRowsFromZero() {
            List<StruckRow> rows = splitRows(struckLead());
            assertThat(rows.subList(0, 3)).extracting(StruckRow::index).containsExactly(0, 1, 2);
        }

        @Test
        void ignoresAPartRowAtTheEnd() {
            List<Strike> strikes = struckLead().strikes();
            StrikeStream shorter = new StrikeStream(6, strikes.subList(0, strikes.size() - 3));
            assertThat(splitRows(shorter)).hasSize(12);
        }

        @Test
        void refusesARunOfBlowsThatIsNotAPermutation() {
            StrikeStream stream = new StrikeStream(
                    4, List.of(new Strike(1, 0), new Strike(2, 250), new Strike(1, 500), new Strike(4, 750)));
            assertThatThrownBy(() -> splitRows(stream))
                    .hasMessage("row 0 is not a permutation: bell 1 appears more than once");
        }

        @Test
        void refusesSomethingThatIsNotAStream() {
            assertThatThrownBy(() -> splitRows(null)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class SplitRowsLeniently {

        @Test
        void reportsNothingWrongWithACleanStream() {
            LenientSplit result = splitRowsLeniently(struckLead());
            assertThat(result.stoppedAt()).isEmpty();
            assertThat(result.reason()).isEmpty();
            assertThat(result.rows()).hasSize(13);
        }

        @Test
        void keepsTheRowsBeforeTheBadOne() {
            List<Strike> strikes = new ArrayList<>(struckLead().strikes().subList(0, 12));
            strikes.addAll(List.of(
                    new Strike(1, 9000),
                    new Strike(1, 9250),
                    new Strike(3, 9500),
                    new Strike(4, 9750),
                    new Strike(5, 10000),
                    new Strike(6, 10250)));
            LenientSplit result = splitRowsLeniently(new StrikeStream(6, strikes));
            assertThat(result.rows()).hasSize(2);
            assertThat(result.stoppedAt()).hasValue(2);
            assertThat(result.reason()).get().asString().contains("not a permutation");
        }

        @Test
        void refusesSomethingThatIsNotAStream() {
            assertThatThrownBy(() -> splitRowsLeniently(null)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class TrailingBlows {

        @Test
        void countsNothingWhenTheStreamDividesEvenly() {
            assertThat(trailingBlows(struckLead())).isZero();
        }

        @Test
        void countsTheBlowsOfAPartRow() {
            List<Strike> strikes = struckLead().strikes();
            assertThat(trailingBlows(new StrikeStream(6, strikes.subList(0, strikes.size() - 2))))
                    .isEqualTo(4);
        }
    }

    @Nested
    class RowDurations {

        @Test
        void measuresFromOneRowToTheNext() {
            List<StruckRow> rows = splitRows(struckLead());
            assertThat(rowDurations(rows)).hasSize(12);
            assertThat(new HashSet<>(rowDurations(rows))).containsExactly(1500.0);
        }

        @Test
        void givesNothingForASingleRow() {
            StruckRow only = new StruckRow(Row.rounds(6), 0, List.of(new Strike(1, 0)), 0, 0);
            assertThat(rowDurations(List.of(only))).isEmpty();
        }
    }

    @Nested
    class Handstrokes {

        @Test
        void alternatesFromTheFirstRow() {
            List<StruckRow> rows = splitRows(struckLead());
            assertThat(handstrokes(rows).subList(0, 4)).containsExactly(true, false, true, false);
        }
    }
}
