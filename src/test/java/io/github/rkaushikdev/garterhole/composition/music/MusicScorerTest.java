package io.github.rkaushikdev.garterhole.composition.music;

import static io.github.rkaushikdev.garterhole.composition.music.MusicScorer.compareMusic;
import static io.github.rkaushikdev.garterhole.composition.music.MusicScorer.countRule;
import static io.github.rkaushikdev.garterhole.composition.music.MusicScorer.defaultScheme;
import static io.github.rkaushikdev.garterhole.composition.music.MusicScorer.musicSummary;
import static io.github.rkaushikdev.garterhole.composition.music.MusicScorer.scoreMusic;
import static io.github.rkaushikdev.garterhole.core.RowParse.parseRows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.composition.music.MusicScorer.MusicSummary;
import io.github.rkaushikdev.garterhole.composition.music.MusicScorer.NamedCount;
import io.github.rkaushikdev.garterhole.composition.music.MusicScorer.Score;
import io.github.rkaushikdev.garterhole.composition.music.MusicScorer.ScoreLine;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.method.Method;
import io.github.rkaushikdev.garterhole.method.PlainCourse;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MusicScorerTest {

    private static List<Row> musical() {
        return parseRows("42135678 12346857 13527468 87654321 13572468");
    }

    private static List<Row> dull() {
        return parseRows("13527468 24681357");
    }

    @Nested
    class DefaultScheme {

        @Test
        void weightsLongerRunsMore() {
            List<MusicRule> scheme = defaultScheme(8);
            MusicRule four = scheme.stream()
                    .filter(rule -> rule.runLength().equals(OptionalInt.of(4)))
                    .findFirst()
                    .orElseThrow();
            MusicRule six = scheme.stream()
                    .filter(rule -> rule.runLength().equals(OptionalInt.of(6)))
                    .findFirst()
                    .orElseThrow();
            assertThat(six.weight().getAsDouble()).isGreaterThan(four.weight().getAsDouble());
        }

        @Test
        void leavesOutRunsLongerThanTheStage() {
            assertThat(defaultScheme(5)).noneMatch(rule -> rule.runLength().equals(OptionalInt.of(6)));
        }

        @Test
        void refusesAStageWithNoScheme() {
            assertThatThrownBy(() -> defaultScheme(3)).hasMessageContaining("no default music scheme on 3");
        }
    }

    @Nested
    class ScoreMusic {

        @Test
        void addsTheWeightedCountsUp() {
            Score scored = scoreMusic(musical(), 8);
            assertThat(scored.total()).isGreaterThan(0);
            assertThat(scored.lines()).extracting(ScoreLine::name).contains("four bell runs");
        }

        @Test
        void givesEveryLineItsCountWeightAndScore() {
            ScoreLine line = scoreMusic(musical(), 8).lines().stream()
                    .filter(entry -> entry.name().equals("four bell runs"))
                    .findFirst()
                    .orElseThrow();
            assertThat(line.score()).isEqualTo(line.count() * line.weight());
        }

        @Test
        void scoresAnUnmusicalBlockLow() {
            assertThat(scoreMusic(dull(), 8).total()).isZero();
        }

        @Test
        void scoresAnEmptyBlockZero() {
            assertThat(scoreMusic(List.of(), 8).total()).isZero();
        }

        @Test
        void takesASchemeOfItsOwn() {
            List<MusicRule> scheme = List.of(MusicRule.pattern("tenors together", "*78", 10));
            Score scored = scoreMusic(musical(), 8, scheme);
            assertThat(scored.lines()).hasSize(1);
            assertThat(scored.total()).isEqualTo(scored.lines().get(0).count() * 10.0);
        }
    }

    @Nested
    class CountRule {

        private final List<Row> rows = musical();

        @Test
        void countsAPattern() {
            assertThat(countRule(rows, MusicRule.pattern("back", "*5678", 1))).isEqualTo(1);
        }

        @Test
        void countsARunLength() {
            assertThat(countRule(rows, MusicRule.runs("runs", 4, 1))).isGreaterThan(0);
        }

        @Test
        void countsANamedRow() {
            assertThat(countRule(rows, MusicRule.namedRow("queens", "Queens", 1)))
                    .isEqualTo(1);
            assertThat(countRule(rows, MusicRule.namedRow("backrounds", "Backrounds", 1)))
                    .isEqualTo(1);
        }

        @Test
        void refusesARuleWithNothingToCount() {
            MusicRule empty = new MusicRule(
                    "empty", OptionalDouble.of(1), Optional.empty(), OptionalInt.empty(), Optional.empty());
            assertThatThrownBy(() -> countRule(rows, empty)).hasMessageContaining("has nothing to count");
        }

        @Test
        void refusesARuleWithNoWeight() {
            MusicRule unweighted = new MusicRule(
                    "unweighted", OptionalDouble.empty(), Optional.of("*"), OptionalInt.empty(), Optional.empty());
            assertThatThrownBy(() -> countRule(rows, unweighted)).hasMessageContaining("has no weight");
        }

        @Test
        void refusesSomethingThatIsNotARule() {
            assertThatThrownBy(() -> countRule(rows, null)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class MusicSummaryOfABlock {

        @Test
        void reportsTheScoreTheRunsAndTheNamedRows() {
            MusicSummary summary = musicSummary(musical(), 8);
            assertThat(summary.score()).isGreaterThan(0);
            assertThat(summary.runs().get(0).length()).isEqualTo(4);
            assertThat(summary.named().stream().map(NamedCount::name).sorted()).containsExactly("Backrounds", "Queens");
        }

        @Test
        void leavesRoundsOutOfTheNamedRows() {
            assertThat(musicSummary(parseRows("12345678"), 8).named()).isEmpty();
        }

        @Test
        void summarisesAWholePlainCourse() {
            MusicSummary summary = musicSummary(PlainCourse.plainCourse(Method.of("Plain", 8, "x18x18x18x18,12")), 8);
            assertThat(summary.runs()).hasSize(5);
            assertThat(summary.score()).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    class CompareMusic {

        @Test
        void isPositiveWhenTheFirstBlockIsMoreMusical() {
            assertThat(compareMusic(musical(), dull(), 8)).isGreaterThan(0);
        }

        @Test
        void isZeroForTwoBlocksThatScoreTheSame() {
            assertThat(compareMusic(dull(), dull(), 8)).isZero();
        }
    }
}
