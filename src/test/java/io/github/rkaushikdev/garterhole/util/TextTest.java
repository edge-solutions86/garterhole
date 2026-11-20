package io.github.rkaushikdev.garterhole.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.util.Text.Align;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TextTest {

    @Nested
    class Padding {

        @Test
        void padsToTheWidth() {
            assertThat(Text.padRight("ab", 5)).isEqualTo("ab   ");
            assertThat(Text.padLeft("ab", 5)).isEqualTo("   ab");
        }

        @Test
        void leavesTextThatIsAlreadyWideEnough() {
            assertThat(Text.padRight("abcdef", 3)).isEqualTo("abcdef");
            assertThat(Text.padLeft("abcdef", 3)).isEqualTo("abcdef");
        }
    }

    @Nested
    class Tabulate {

        @Test
        void linesTheColumnsUp() {
            List<String> lines =
                    Text.tabulate(List.of(List.of("Bell", "Weight"), List.of("Treble", "4"), List.of("Tenor", "19")));
            assertThat(lines).containsExactly("Bell    Weight", "Treble  4", "Tenor   19");
        }

        @Test
        void rightAlignsTheColumnsItIsToldTo() {
            List<String> lines =
                    Text.tabulate(List.of(List.of("a", "1"), List.of("bb", "22")), List.of(Align.LEFT, Align.RIGHT));
            assertThat(lines).containsExactly("a    1", "bb  22");
        }

        @Test
        void padsOutAShortRow() {
            assertThat(Text.tabulate(List.of(List.of("a", "b"), List.of("c")))).containsExactly("a  b", "c");
        }

        @Test
        void takesADifferentGap() {
            assertThat(Text.tabulate(List.of(List.of("a", "b")), " | ", List.of()))
                    .containsExactly("a | b");
        }

        @Test
        void givesNothingForNoRows() {
            assertThat(Text.tabulate(List.of())).isEmpty();
        }
    }

    @Nested
    class Plural {

        @Test
        void usesTheSingularForOne() {
            assertThat(Text.plural(1, "bell")).isEqualTo("1 bell");
        }

        @Test
        void addsAnSForAnythingElse() {
            assertThat(Text.plural(0, "bell")).isEqualTo("0 bells");
            assertThat(Text.plural(8, "bell")).isEqualTo("8 bells");
        }

        @Test
        void takesAnIrregularPlural() {
            assertThat(Text.plural(2, "belfry", "belfries")).isEqualTo("2 belfries");
        }
    }

    @Nested
    class JoinWords {

        @Test
        void joinsWithCommasAndAFinalAnd() {
            assertThat(Text.joinWords(List.of("a", "b", "c"))).isEqualTo("a, b and c");
        }

        @Test
        void joinsTwoWithJustAnAnd() {
            assertThat(Text.joinWords(List.of("a", "b"))).isEqualTo("a and b");
        }

        @Test
        void leavesOneAloneAndGivesNothingForNone() {
            assertThat(Text.joinWords(List.of("a"))).isEqualTo("a");
            assertThat(Text.joinWords(List.of())).isEmpty();
        }
    }

    @Nested
    class Truncate {

        @Test
        void leavesShortTextAlone() {
            assertThat(Text.truncate("abc", 5)).isEqualTo("abc");
            assertThat(Text.truncate("abcde", 5)).isEqualTo("abcde");
        }

        @Test
        void cutsAndMarksLongerText() {
            assertThat(Text.truncate("abcdef", 5)).isEqualTo("abcd.");
        }

        @Test
        void handlesAWidthOfOne() {
            assertThat(Text.truncate("abc", 1)).isEqualTo(".");
        }

        @Test
        void refusesAWidthBelowOne() {
            assertThatThrownBy(() -> Text.truncate("abc", 0)).hasMessageContaining("at least 1");
        }
    }

    @Nested
    class Slugify {

        @Test
        void lowerCasesAndJoinsWithHyphens() {
            assertThat(Text.slugify("Cambridge Surprise Major")).isEqualTo("cambridge-surprise-major");
        }

        @Test
        void dropsPunctuationAndLeadingOrTrailingHyphens() {
            assertThat(Text.slugify("St Martin's Bob Doubles!")).isEqualTo("st-martin-s-bob-doubles");
            assertThat(Text.slugify("  spaced  ")).isEqualTo("spaced");
        }

        @Test
        void givesAnEmptyStringWhenNothingSurvives() {
            assertThat(Text.slugify("!!!")).isEmpty();
        }
    }

    @Nested
    class Squash {

        @Test
        void collapsesRunsOfWhitespace() {
            assertThat(Text.squash("  a \n b\tc  ")).isEqualTo("a b c");
        }

        @Test
        void givesAnEmptyStringForWhitespaceOnly() {
            assertThat(Text.squash(" \n ")).isEmpty();
        }
    }

    @Nested
    class Wrap {

        @Test
        void wrapsAtTheWidthWithoutBreakingWords() {
            assertThat(Text.wrap("the quick brown fox jumps", 10)).containsExactly("the quick", "brown fox", "jumps");
        }

        @Test
        void givesALongWordALineOfItsOwn() {
            assertThat(Text.wrap("a supercalifragilistic b", 5)).containsExactly("a", "supercalifragilistic", "b");
        }

        @Test
        void givesNothingForEmptyText() {
            assertThat(Text.wrap("   ", 10)).isEmpty();
        }

        @Test
        void refusesAWidthBelowOne() {
            assertThatThrownBy(() -> Text.wrap("abc", 0)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    /** JavaScript's idea of white space, which is wider than Java's {@code strip()}. */
    @Nested
    class JavaScriptWhitespace {

        @Test
        void trimsTheSpacesJavaScriptTrims() {
            assertThat(Text.trim("\u00A0\uFEFF Cambridge\u3000\u2009")).isEqualTo("Cambridge");
            assertThat(Text.trimEnd(" a\u00A0")).isEqualTo(" a");
        }

        @Test
        void leavesAloneWhatJavaScriptLeavesAlone() {
            assertThat(Text.trim("\u200Ba\u200B")).isEqualTo("\u200Ba\u200B");
            assertThat(Text.isWhitespace('\u200B')).isFalse();
            assertThat(Text.isWhitespace('x')).isFalse();
            assertThat(Text.isWhitespace('\u2028')).isTrue();
        }

        @Test
        void squashesANoBreakSpaceLikeAnyOther() {
            assertThat(Text.squash("Plain\u00A0\u00A0Bob ")).isEqualTo("Plain Bob");
        }
    }
}
