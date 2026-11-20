package io.github.rkaushikdev.garterhole.method;

import static io.github.rkaushikdev.garterhole.method.HuntBells.blowsInEachPlace;
import static io.github.rkaushikdev.garterhole.method.HuntBells.dodgePlaces;
import static io.github.rkaushikdev.garterhole.method.HuntBells.huntBells;
import static io.github.rkaushikdev.garterhole.method.HuntBells.huntPathKind;
import static io.github.rkaushikdev.garterhole.method.HuntBells.isPrinciple;
import static io.github.rkaushikdev.garterhole.method.HuntBells.pathOf;
import static io.github.rkaushikdev.garterhole.method.HuntBells.placesMade;
import static io.github.rkaushikdev.garterhole.method.HuntBells.workingBells;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HuntBellsTest {

    private static Method plainBobMinor() {
        return Method.of("Plain Bob", 6, "x1x1x1,2");
    }

    private static Method cambridgeMinor() {
        return Method.of("Cambridge Surprise", 6, "x36x14x12x36x14x56,12");
    }

    private static Method doubleNorwich() {
        return Method.of("Double Norwich Court Bob", 8, "x14x36x58x18,18");
    }

    private static Method grandsireDoubles() {
        return Method.of("Grandsire", 5, "3,1.5.1.5.1");
    }

    /** No bell is left in its own place by the lead head, so nothing hunts. */
    private static Method principle() {
        return Method.of("Test Principle", 5, "3.1.5.3.1.3.5.1.3.1");
    }

    /** Written for the tests: the treble makes a place in thirds. */
    private static Method treblePlace() {
        return Method.of("Test Treble Place", 6, "x16.34.16x16x16x16x12");
    }

    @Nested
    class HuntBellsOfAMethod {

        @Test
        void findsTheTrebleInPlainBob() {
            assertThat(huntBells(plainBobMinor())).containsExactly(1);
        }

        @Test
        void findsTwoHuntBellsInGrandsire() {
            assertThat(huntBells(grandsireDoubles())).containsExactly(1, 2);
        }

        @Test
        void findsNoneInAPrinciple() {
            assertThat(huntBells(principle())).isEmpty();
            assertThat(isPrinciple(principle())).isTrue();
        }

        @Test
        void saysATrebleDominatedMethodIsNotAPrinciple() {
            assertThat(isPrinciple(plainBobMinor())).isFalse();
            assertThat(isPrinciple(cambridgeMinor())).isFalse();
        }

        @Test
        void refusesSomethingThatIsNotAMethod() {
            assertThatThrownBy(() -> huntBells(null)).hasMessageContaining("a Method is needed");
            assertThatThrownBy(() -> pathOf(null, 1)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class WorkingBells {

        @Test
        void listsEverythingThatIsNotHunting() {
            assertThat(workingBells(plainBobMinor())).containsExactly(2, 3, 4, 5, 6);
            assertThat(workingBells(grandsireDoubles())).containsExactly(3, 4, 5);
        }

        @Test
        void listsEveryBellInAPrinciple() {
            assertThat(workingBells(principle())).containsExactly(1, 2, 3, 4, 5);
        }
    }

    @Nested
    class PathOf {

        @Test
        void tracesPlainHuntForTheTrebleInPlainBobMinor() {
            assertThat(pathOf(plainBobMinor(), 1)).containsExactly(1, 2, 3, 4, 5, 6, 6, 5, 4, 3, 2, 1, 1);
        }

        @Test
        void tracesTheTrebleHuntingUpToEighthsInDoubleNorwich() {
            assertThat(pathOf(doubleNorwich(), 1)).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 8, 7, 6, 5, 4, 3, 2, 1, 1);
        }

        @Test
        void tracesTrebleBobHuntingInCambridge() {
            assertThat(pathOf(cambridgeMinor(), 1).subList(0, 8)).containsExactly(1, 2, 1, 2, 3, 4, 3, 4);
        }

        @Test
        void tracesAWorkingBellOverOneLead() {
            List<Integer> path = pathOf(plainBobMinor(), 2);
            assertThat(path).hasSize(13);
            assertThat(path.get(0)).isEqualTo(2);
            assertThat(path.get(path.size() - 1)).isEqualTo(4);
        }
    }

    @Nested
    class DodgePlaces {

        @Test
        void findsNothingInPlainHunt() {
            assertThat(dodgePlaces(pathOf(plainBobMinor(), 1), 6)).isEmpty();
            assertThat(dodgePlaces(pathOf(doubleNorwich(), 1), 8)).isEmpty();
        }

        @Test
        void findsEveryTurnInTrebleBobHunting() {
            assertThat(dodgePlaces(pathOf(cambridgeMinor(), 1), 6)).containsExactly(2, 4, 3, 5, 5, 3, 4, 2);
        }

        @Test
        void ignoresTheTurnsAtTheFrontAndTheBack() {
            assertThat(dodgePlaces(List.of(1, 2, 3, 2, 1), 3)).isEmpty();
        }
    }

    @Nested
    class PlacesMade {

        @Test
        void findsNothingForATrebleThatNeverMakesAPlace() {
            assertThat(placesMade(pathOf(plainBobMinor(), 1), 6)).isEmpty();
            assertThat(placesMade(pathOf(cambridgeMinor(), 1), 6)).isEmpty();
        }

        @Test
        void findsAPlaceMadeInTheMiddleOfAPath() {
            assertThat(placesMade(List.of(1, 2, 3, 3, 2, 1), 6)).containsExactly(3);
        }

        @Test
        void ignoresTheTwoBlowsAtTheFrontAndTheBack() {
            assertThat(placesMade(List.of(1, 1, 2, 3, 3), 3)).isEmpty();
        }
    }

    @Nested
    class HuntPathKind {

        @Test
        void callsTheTrebleInPlainBobPlainHunting() {
            assertThat(huntPathKind(plainBobMinor(), 1)).isEqualTo(HuntPath.PLAIN);
            assertThat(huntPathKind(doubleNorwich(), 1)).hasToString("plain");
        }

        @Test
        void callsTheTrebleInASurpriseMethodTrebleBob() {
            assertThat(huntPathKind(cambridgeMinor(), 1)).hasToString("treble bob");
        }

        @Test
        void callsAPathWithAPlaceInItTreblePlace() {
            assertThat(huntPathKind(treblePlace(), 1)).hasToString("treble place");
        }

        @Test
        void callsBothGrandsireHuntBellsPlain() {
            assertThat(huntPathKind(grandsireDoubles(), 1)).isEqualTo(HuntPath.PLAIN);
            assertThat(huntPathKind(grandsireDoubles(), 2)).isEqualTo(HuntPath.PLAIN);
        }
    }

    @Nested
    class BlowsInEachPlace {

        @Test
        void spreadsAPlainHuntingTrebleEvenly() {
            assertThat(blowsInEachPlace(plainBobMinor(), 1)).containsExactly(2, 2, 2, 2, 2, 2);
        }

        @Test
        void spreadsATrebleBobTrebleEvenlyToo() {
            assertThat(blowsInEachPlace(cambridgeMinor(), 1)).containsExactly(4, 4, 4, 4, 4, 4);
        }

        @Test
        void countsOneLeadOfAWorkingBell() {
            List<Integer> counts = blowsInEachPlace(plainBobMinor(), 5);
            assertThat(counts.stream().mapToInt(Integer::intValue).sum()).isEqualTo(12);
        }
    }
}
