package io.github.rkaushikdev.garterhole.notation;

import static io.github.rkaushikdev.garterhole.notation.Expansion.expandNotation;
import static io.github.rkaushikdev.garterhole.notation.Expansion.expandSections;
import static io.github.rkaushikdev.garterhole.notation.Expansion.findSymmetryPoint;
import static io.github.rkaushikdev.garterhole.notation.Expansion.isPalindromic;
import static io.github.rkaushikdev.garterhole.notation.Expansion.mirrorSection;
import static io.github.rkaushikdev.garterhole.notation.Expansion.rotateToAxis;
import static io.github.rkaushikdev.garterhole.notation.NotationParser.parseChanges;
import static io.github.rkaushikdev.garterhole.notation.NotationParser.parseNotation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.core.Change;
import io.github.rkaushikdev.garterhole.notation.NotationParser.ParsedNotation;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExpansionTest {

    private static final String PLAIN_BOB_MINOR = "x1x1x1,2";
    private static final String CAMBRIDGE_MINOR = "x3x4x2x3x4x5,2";
    private static final String GRANDSIRE_DOUBLES = "3,1.5.1.5.1";

    private static List<String> written(List<Change> changes) {
        return changes.stream().map(Change::toString).toList();
    }

    @Nested
    class MirrorSection {

        @Test
        void writesASectionOutAndThenBackwardsWithoutItsLastChange() {
            List<Change> mirrored = mirrorSection(parseChanges("x1x1x1", 6));
            assertThat(written(mirrored)).containsExactly("x", "16", "x", "16", "x", "16", "x", "16", "x", "16", "x");
        }

        @Test
        void leavesASingleChangeAlone() {
            assertThat(written(mirrorSection(parseChanges("12", 8)))).containsExactly("12");
        }

        @Test
        void givesNothingBackForNothing() {
            assertThat(mirrorSection(List.of())).isEmpty();
        }
    }

    @Nested
    class ExpandNotation {

        @Test
        void expandsPlainBobMinorToAWholeLeadOfTwelveChanges() {
            List<Change> changes = expandNotation(PLAIN_BOB_MINOR, 6);
            assertThat(changes).hasSize(12);
            assertThat(String.join(".", written(changes))).isEqualTo("x.16.x.16.x.16.x.16.x.16.x.12");
        }

        @Test
        void expandsCambridgeSurpriseMinorToTwentyFourChanges() {
            List<Change> changes = expandNotation(CAMBRIDGE_MINOR, 6);
            assertThat(changes).hasSize(24);
            assertThat(String.join(".", written(changes).subList(0, 6))).isEqualTo("x.36.x.14.x.12");
            assertThat(changes.get(23)).hasToString("12");
        }

        @Test
        void expandsGrandsireDoubles() {
            assertThat(String.join(".", written(expandNotation(GRANDSIRE_DOUBLES, 5))))
                    .isEqualTo("3.1.5.1.5.1.5.1.5.1");
        }

        @Test
        void leavesNotationWithNoCommaExactlyAsWritten() {
            assertThat(written(expandNotation("x18x18x18x18", 8)))
                    .containsExactly("x", "18", "x", "18", "x", "18", "x", "18");
        }

        @Test
        void passesABadSymbolOnFromTheParser() {
            assertThatThrownBy(() -> expandNotation("x1?x1", 6)).hasMessageContaining("not part of place notation");
        }
    }

    @Nested
    class ExpandSections {

        @Test
        void takesParsedSectionsDirectly() {
            ParsedNotation parsed = parseNotation(PLAIN_BOB_MINOR, 6);
            assertThat(expandSections(parsed.sections())).hasSize(12);
        }

        @Test
        void copiesRatherThanReturningTheSectionItWasGiven() {
            ParsedNotation parsed = parseNotation("x18x18", 8);
            List<Change> expanded = expandSections(parsed.sections());
            assertThat(expanded).isNotSameAs(parsed.sections().get(0));
            assertThat(expanded).isEqualTo(parsed.sections().get(0));
        }

        @Test
        void refusesNothingToExpand() {
            assertThatThrownBy(() -> expandSections(List.of())).hasMessage("there is nothing to expand");
            assertThatThrownBy(() -> expandSections(null)).hasMessage("there is nothing to expand");
        }
    }

    @Nested
    class Palindromes {

        @Test
        void recognisesAnExpandedLeadAsPalindromic() {
            assertThat(isPalindromic(expandNotation(PLAIN_BOB_MINOR, 6))).isTrue();
            assertThat(isPalindromic(expandNotation(CAMBRIDGE_MINOR, 6))).isTrue();
            assertThat(isPalindromic(expandNotation(GRANDSIRE_DOUBLES, 5))).isTrue();
        }

        @Test
        void rejectsARunOfChangesWithNoAxis() {
            assertThat(isPalindromic(parseChanges("16.14.12", 6))).isFalse();
            assertThat(findSymmetryPoint(parseChanges("16.14.12", 6))).isEmpty();
        }

        @Test
        void saysNothingIsNotAPalindrome() {
            assertThat(findSymmetryPoint(List.of())).isEmpty();
            assertThat(isPalindromic(List.of())).isFalse();
        }

        @Test
        void findsTheFirstAxisOfALead() {
            assertThat(findSymmetryPoint(expandNotation(PLAIN_BOB_MINOR, 6))).hasValue(5);
        }
    }

    @Nested
    class RotateToAxis {

        @Test
        void movesTheAxisChangeToTheEnd() {
            assertThat(written(rotateToAxis(parseChanges("x18x12", 8), 1))).containsExactly("x", "12", "x", "18");
        }

        @Test
        void leavesALeadAloneWhenTheAxisIsAlreadyLast() {
            List<Change> changes = parseChanges("x18x12", 8);
            assertThat(rotateToAxis(changes, 3)).isEqualTo(changes);
        }

        @Test
        void keepsARotatedLeadPalindromic() {
            List<Change> changes = expandNotation(CAMBRIDGE_MINOR, 6);
            int axis = findSymmetryPoint(changes).orElseThrow();
            assertThat(isPalindromic(rotateToAxis(changes, axis))).isTrue();
        }

        @Test
        void refusesAnAxisOutsideTheLead() {
            assertThatThrownBy(() -> rotateToAxis(parseChanges("x18", 8), 5)).hasMessageContaining("outside 0..1");
            assertThatThrownBy(() -> rotateToAxis(parseChanges("x18", 8), -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
