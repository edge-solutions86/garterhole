package io.github.rkaushikdev.garterhole.method;

import static io.github.rkaushikdev.garterhole.method.PathTrace.coursePath;
import static io.github.rkaushikdev.garterhole.method.PathTrace.describePath;
import static io.github.rkaushikdev.garterhole.method.PathTrace.pathByLead;
import static io.github.rkaushikdev.garterhole.method.PathTrace.samePathRotated;
import static io.github.rkaushikdev.garterhole.method.PathTrace.turnPoints;
import static io.github.rkaushikdev.garterhole.method.PathTrace.workOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.method.PathTrace.Work;
import io.github.rkaushikdev.garterhole.method.PathTrace.WorkPiece;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PathTraceTest {

    private static Method plainBobMinor() {
        return Method.of("Plain Bob", 6, "x1x1x1,2");
    }

    private static Method cambridgeMinor() {
        return Method.of("Cambridge Surprise", 6, "x36x14x12x36x14x56,12");
    }

    private static List<String> shape(List<WorkPiece> pieces) {
        return pieces.stream().map(piece -> piece.kind() + "@" + piece.place()).toList();
    }

    private static List<Integer> withoutLast(List<Integer> path) {
        return path.subList(0, path.size() - 1);
    }

    @Nested
    class CoursePath {

        @Test
        void hasOneEntryPerRowOfTheCourse() {
            assertThat(coursePath(plainBobMinor(), 2)).hasSize(61);
            assertThat(coursePath(cambridgeMinor(), 2)).hasSize(121);
        }

        @Test
        void startsAndFinishesInTheSamePlace() {
            List<Integer> path = coursePath(plainBobMinor(), 4);
            assertThat(path.get(0)).isEqualTo(4);
            assertThat(path.get(path.size() - 1)).isEqualTo(4);
        }

        @Test
        void refusesSomethingThatIsNotAMethod() {
            assertThatThrownBy(() -> coursePath(null, 2)).hasMessageContaining("a Method is needed");
        }
    }

    @Nested
    class TurnPoints {

        @Test
        void marksAReversal() {
            assertThat(turnPoints(List.of(1, 2, 1, 2))).containsExactly(false, true, true, false);
        }

        @Test
        void marksNothingInAStraightRun() {
            assertThat(turnPoints(List.of(1, 2, 3, 4))).containsExactly(false, false, false, false);
        }

        @Test
        void doesNotTreatARepeatedBlowAsAReversal() {
            assertThat(turnPoints(List.of(1, 2, 2, 1))).containsExactly(false, false, false, false);
        }
    }

    @Nested
    class DescribePath {

        @Test
        void callsTwoBlowsAtTheFrontALead() {
            assertThat(shape(describePath(List.of(2, 1, 1, 2), 6))).containsExactly("hunt@2", "lead@1");
        }

        @Test
        void callsTwoBlowsAtTheBackALie() {
            assertThat(shape(describePath(List.of(5, 6, 6, 5), 6))).containsExactly("hunt@5", "lie@6");
        }

        @Test
        void callsTwoBlowsAnywhereElseAPlace() {
            assertThat(shape(describePath(List.of(2, 3, 3, 2), 6))).containsExactly("hunt@2", "place@3");
        }

        @Test
        void callsAPairOfTurnsOnePlaceApartADodge() {
            assertThat(shape(describePath(List.of(5, 4, 3, 4, 3, 2), 6)))
                    .containsExactly("hunt@5", "hunt@4", "dodge@3", "hunt@3");
        }

        @Test
        void callsALoneTurnAPoint() {
            assertThat(shape(describePath(List.of(3, 4, 3, 2, 1), 6)))
                    .containsExactly("hunt@3", "point@4", "hunt@3", "hunt@2");
        }
    }

    @Nested
    class WorkOf {

        @Test
        void readsThePlainBobMinorBlueLine() {
            assertThat(shape(workOf(plainBobMinor(), 2)))
                    .containsExactly(
                            "lead@1", "lie@6", "dodge@3", "lead@1", "lie@6", "dodge@5", "lead@1", "dodge@5", "lie@6",
                            "lead@1", "dodge@3", "lie@6", "lead@1", "place@2");
        }

        @Test
        void givesTheTrebleNothingButLeadingAndLying() {
            TreeSet<String> kinds = workOf(plainBobMinor(), 1).stream()
                    .map(piece -> piece.kind().toString())
                    .collect(Collectors.toCollection(TreeSet::new));
            assertThat(kinds).containsExactly("lead", "lie");
        }

        @Test
        void findsTheTrebleBobFrontWorkInCambridge() {
            assertThat(shape(workOf(cambridgeMinor(), 1)).subList(0, 4))
                    .containsExactly("dodge@1", "dodge@3", "dodge@5", "lie@6");
        }

        @Test
        void leavesOutThePlainHunting() {
            assertThat(workOf(plainBobMinor(), 3)).allMatch(piece -> piece.kind() != Work.HUNT);
        }
    }

    @Nested
    class PathByLead {

        @Test
        void givesOneLinePerLead() {
            List<List<Integer>> lines = pathByLead(plainBobMinor(), 2);
            assertThat(lines).hasSize(5);
            assertThat(lines.get(0)).hasSize(12);
            assertThat(lines.get(0)).containsExactly(2, 1, 1, 2, 3, 4, 5, 6, 6, 5, 4, 3);
        }

        @Test
        void givesOneLinePerLeadOfASurpriseMethod() {
            List<List<Integer>> lines = pathByLead(cambridgeMinor(), 2);
            assertThat(lines).hasSize(5);
            assertThat(lines.get(0)).hasSize(24);
        }
    }

    @Nested
    class SamePathRotated {

        @Test
        void findsThatEveryWorkingBellRingsTheSameLineInPlainBob() {
            Method bob = plainBobMinor();
            List<Integer> second = withoutLast(coursePath(bob, 2));
            for (int bell : List.of(3, 4, 5, 6)) {
                assertThat(samePathRotated(second, withoutLast(coursePath(bob, bell))))
                        .isTrue();
            }
        }

        @Test
        void doesNotConfuseTheTrebleWithAWorkingBell() {
            Method bob = plainBobMinor();
            assertThat(samePathRotated(withoutLast(coursePath(bob, 1)), withoutLast(coursePath(bob, 2))))
                    .isFalse();
        }

        @Test
        void refusesToComparePathsOfDifferentLengths() {
            assertThat(samePathRotated(List.of(1, 2), List.of(1, 2, 3))).isFalse();
        }
    }
}
