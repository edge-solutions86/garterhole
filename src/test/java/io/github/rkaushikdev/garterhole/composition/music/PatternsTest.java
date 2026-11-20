package io.github.rkaushikdev.garterhole.composition.music;

import static io.github.rkaushikdev.garterhole.composition.music.Patterns.commonPatterns;
import static io.github.rkaushikdev.garterhole.composition.music.Patterns.countPattern;
import static io.github.rkaushikdev.garterhole.composition.music.Patterns.findPattern;
import static io.github.rkaushikdev.garterhole.composition.music.Patterns.matchesPattern;
import static io.github.rkaushikdev.garterhole.composition.music.Patterns.parsePattern;
import static io.github.rkaushikdev.garterhole.core.RowParse.parseRow;
import static io.github.rkaushikdev.garterhole.core.RowParse.parseRows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.NotationException;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.composition.music.Patterns.Any;
import io.github.rkaushikdev.garterhole.composition.music.Patterns.Bell;
import io.github.rkaushikdev.garterhole.composition.music.Patterns.Match;
import io.github.rkaushikdev.garterhole.composition.music.Patterns.PatternPart;
import io.github.rkaushikdev.garterhole.composition.music.Patterns.Run;
import io.github.rkaushikdev.garterhole.core.Row;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PatternsTest {

    @Nested
    class ParsePattern {

        @Test
        void readsBellsWildcardsAndRuns() {
            assertThat(parsePattern("1?*8")).containsExactly(new Bell(1), new Any(), new Run(), new Bell(8));
        }

        @Test
        void refusesAnEmptyPattern() {
            assertThatThrownBy(() -> parsePattern("")).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> parsePattern(null)).hasMessageContaining("must be a non empty string");
        }

        @Test
        void refusesACharacterItDoesNotKnow() {
            assertThatThrownBy(() -> parsePattern("*56/8")).isInstanceOf(NotationException.class);
            assertThatThrownBy(() -> parsePattern("*56/8")).hasMessage("\"/\" at position 4 is not part of a pattern");
        }
    }

    @Nested
    class MatchesPattern {

        @Test
        void matchesARunOfBellsAtTheBack() {
            assertThat(matchesPattern(parseRow("42135678"), "*5678")).isTrue();
            assertThat(matchesPattern(parseRow("42136578"), "*5678")).isFalse();
        }

        @Test
        void matchesARunOfBellsAtTheFront() {
            assertThat(matchesPattern(parseRow("12346857"), "1234*")).isTrue();
            assertThat(matchesPattern(parseRow("21346857"), "1234*")).isFalse();
        }

        @Test
        void matchesAnExactRowWithNoWildcards() {
            assertThat(matchesPattern(parseRow("12345678"), "12345678")).isTrue();
            assertThat(matchesPattern(parseRow("12345678"), "1234567")).isFalse();
        }

        @Test
        void matchesASingleBellWildcard() {
            assertThat(matchesPattern(parseRow("12345678"), "1?345678")).isTrue();
            assertThat(matchesPattern(parseRow("12345678"), "?2345678")).isTrue();
            assertThat(matchesPattern(parseRow("12345678"), "1?45678")).isFalse();
        }

        @Test
        void letsARunSwallowNothingAtAll() {
            assertThat(matchesPattern(parseRow("12345678"), "*12345678")).isTrue();
            assertThat(matchesPattern(parseRow("12345678"), "12345678*")).isTrue();
        }

        @Test
        void matchesARunInTheMiddle() {
            assertThat(matchesPattern(parseRow("12345678"), "12*78")).isTrue();
            assertThat(matchesPattern(parseRow("12345678"), "12*76")).isFalse();
        }

        @Test
        void takesAPatternThatHasAlreadyBeenRead() {
            List<PatternPart> parts = parsePattern("*5678");
            assertThat(matchesPattern(parseRow("42135678"), parts)).isTrue();
        }

        @Test
        void refusesSomethingThatIsNotARow() {
            assertThatThrownBy(() -> matchesPattern(null, "*5678")).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class CountPattern {

        private final List<Row> rows = parseRows("42135678 12346857 13527468 87654321");

        @Test
        void countsTheRowsThatMatch() {
            assertThat(countPattern(rows, "*5678")).isEqualTo(1);
            assertThat(countPattern(rows, "1234*")).isEqualTo(1);
        }

        @Test
        void countsNothingWhenNothingMatches() {
            assertThat(countPattern(rows, "*1234")).isZero();
        }

        @Test
        void countsEverythingForAPatternThatMatchesAnything() {
            assertThat(countPattern(rows, "*")).isEqualTo(4);
        }
    }

    @Nested
    class FindPattern {

        private final List<Row> rows = parseRows("42135678 12346857 13527468 87654321");

        @Test
        void saysWhereEachMatchWas() {
            List<Match> found = findPattern(rows, "*5678");
            assertThat(found).hasSize(1);
            assertThat(found.get(0).index()).isZero();
            assertThat(found.get(0).row()).hasToString("42135678");
        }

        @Test
        void givesNothingWhenNothingMatches() {
            assertThat(findPattern(rows, "*1234")).isEmpty();
        }
    }

    @Nested
    class CommonPatterns {

        @Test
        void namesTheFourBellRunsAtEachEnd() {
            Map<String, String> patterns = commonPatterns(8);
            assertThat(patterns.get("5678 at the back")).isEqualTo("*5678");
            assertThat(patterns.get("8765 at the back")).isEqualTo("*8765");
            assertThat(patterns.get("1234 at the front")).isEqualTo("1234*");
        }

        @Test
        void namesTheTenorsTogether() {
            assertThat(commonPatterns(8).get("tenors together")).isEqualTo("*78");
            assertThat(commonPatterns(12).get("tenors together")).isEqualTo("*ET");
        }

        @Test
        void worksAtRoyal() {
            assertThat(commonPatterns(10).get("7890 at the back")).isEqualTo("*7890");
        }

        @Test
        void refusesAStageTooSmallToHaveThem() {
            assertThatThrownBy(() -> commonPatterns(3)).hasMessageContaining("no common patterns on 3");
        }
    }
}
