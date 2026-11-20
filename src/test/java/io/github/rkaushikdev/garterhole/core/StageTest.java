package io.github.rkaushikdev.garterhole.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StageTest {

    @Nested
    class StageSupport {

        @Test
        void acceptsEveryStageBetweenTheDocumentedBounds() {
            for (int stage = Stage.MIN; stage <= Stage.MAX; stage += 1) {
                assertThat(Stage.isSupported(stage)).isTrue();
            }
        }

        @Test
        void rejectsStagesOutsideTheBounds() {
            assertThat(Stage.isSupported(Stage.MIN - 1)).isFalse();
            assertThat(Stage.isSupported(Stage.MAX + 1)).isFalse();
        }

        @Test
        void rejectsValuesThatAreNotWholeNumbers() {
            assertThat(Stage.isSupported(6.5)).isFalse();
            assertThat(Stage.isSupported(Double.NaN)).isFalse();
            assertThat(Stage.isSupported(8.0)).isTrue();
        }
    }

    @Nested
    class StageNames {

        @Test
        void namesTheCommonStagesTheWayRingersDo() {
            assertThat(Stage.name(6)).isEqualTo("Minor");
            assertThat(Stage.name(8)).isEqualTo("Major");
            assertThat(Stage.name(10)).isEqualTo("Royal");
            assertThat(Stage.name(12)).isEqualTo("Maximus");
        }

        @Test
        void namesTheOddStages() {
            assertThat(Stage.name(5)).isEqualTo("Doubles");
            assertThat(Stage.name(7)).isEqualTo("Triples");
            assertThat(Stage.name(9)).isEqualTo("Caters");
            assertThat(Stage.name(11)).isEqualTo("Cinques");
        }

        @Test
        void throwsForAStageItHasNoNameFor() {
            assertThatThrownBy(() -> Stage.name(2)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Stage.name(17)).hasMessageContaining("no stage name");
        }

        @Test
        void roundTripsANameBackToItsStage() {
            for (int stage : Stage.supported()) {
                assertThat(Stage.fromName(Stage.name(stage))).isEqualTo(stage);
            }
        }

        @Test
        void resolvesANameRegardlessOfCaseOrPadding() {
            assertThat(Stage.fromName("  major ")).isEqualTo(8);
            assertThat(Stage.fromName("MINOR")).isEqualTo(6);
        }

        @Test
        void throwsForANameThatIsNotAStage() {
            assertThatThrownBy(() -> Stage.fromName("Quadruples")).hasMessageContaining("unknown stage name");
        }
    }

    @Nested
    class CoverBells {

        @Test
        void treatsOddStagesAsCovered() {
            assertThat(Stage.isOdd(5)).isTrue();
            assertThat(Stage.isOdd(6)).isFalse();
            assertThat(Stage.hasCover(5)).isTrue();
            assertThat(Stage.hasCover(6)).isFalse();
        }

        @Test
        void countsTheCoverBellAsAnExtraBellInTheTower() {
            assertThat(Stage.bellsInTower(5)).isEqualTo(6);
            assertThat(Stage.bellsInTower(7)).isEqualTo(8);
        }

        @Test
        void leavesAnEvenStageAlone() {
            assertThat(Stage.bellsInTower(8)).isEqualTo(8);
            assertThat(Stage.bellsInTower(12)).isEqualTo(12);
        }
    }

    @Nested
    class SupportedStages {

        @Test
        void listsEveryStageInAscendingOrder() {
            List<Integer> stages = Stage.supported();
            assertThat(stages.get(0)).isEqualTo(Stage.MIN);
            assertThat(stages.get(stages.size() - 1)).isEqualTo(Stage.MAX);
            assertThat(stages).isSorted();
        }
    }
}
