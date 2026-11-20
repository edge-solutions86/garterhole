package io.github.rkaushikdev.garterhole.composition;

import static io.github.rkaushikdev.garterhole.composition.Falseness.courseHeadBetween;
import static io.github.rkaushikdev.garterhole.composition.Falseness.falseCourseHeadGroups;
import static io.github.rkaushikdev.garterhole.composition.Falseness.falseCourseHeads;
import static io.github.rkaushikdev.garterhole.composition.Falseness.isFalseAgainstPlainCourse;
import static io.github.rkaushikdev.garterhole.composition.Falseness.sharedRows;
import static io.github.rkaushikdev.garterhole.composition.Falseness.tenorsTogetherFalseCourseHeads;
import static io.github.rkaushikdev.garterhole.composition.Falseness.trueCourseHeads;
import static io.github.rkaushikdev.garterhole.core.RowParse.parseRow;
import static io.github.rkaushikdev.garterhole.core.RowParse.parseRows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.core.Transposition;
import io.github.rkaushikdev.garterhole.method.Method;
import io.github.rkaushikdev.garterhole.method.PlainCourse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FalsenessTest {

    private static Method plainBobMinor() {
        return Method.of("Plain", 6, "x1x1x1,2");
    }

    private static Method cambridgeMinor() {
        return Method.of("Cambridge", 6, "x36x14x12x36x14x56,12");
    }

    private static Method bristolMajor() {
        return Method.of("Bristol", 8, "x58x14.58x58.36.14x14.58x14x18,18");
    }

    @Nested
    class CourseHeadBetween {

        @Test
        void givesTheHeadThatTakesOneRowToAnother() {
            Row from = parseRow("13527486");
            Row to = parseRow("15738264");
            Row head = courseHeadBetween(from, to);
            assertThat(Transposition.transpose(head, from)).isEqualTo(to);
        }

        @Test
        void givesRoundsForARowAndItself() {
            Row row = parseRow("13527486");
            assertThat(courseHeadBetween(row, row).isRounds()).isTrue();
        }

        @Test
        void isTheRowItselfWhenTheCourseStartsAtRounds() {
            Row row = parseRow("13527486");
            assertThat(courseHeadBetween(Row.rounds(8), row)).isEqualTo(row);
        }
    }

    @Nested
    class FalseCourseHeads {

        @Test
        void findsTheFalseCourseHeadsOfPlainBobMinor() {
            assertThat(falseCourseHeads(plainBobMinor()))
                    .extracting(Row::toString)
                    .containsExactly(
                            "124365", "132546", "135264", "142635", "146253", "153624", "156342", "164523", "165432");
        }

        @Test
        void leavesRoundsOut() {
            assertThat(falseCourseHeads(cambridgeMinor())).noneMatch(Row::isRounds);
        }

        @Test
        void keepsOnlyHeadsThatLeaveTheTrebleAtTheFront() {
            for (Row head : falseCourseHeads(cambridgeMinor())) {
                assertThat(head.bellAt(1)).isEqualTo(1);
            }
        }

        @Test
        void findsFewerForAMethodThatIsDesignedToBeClean() {
            assertThat(tenorsTogetherFalseCourseHeads(bristolMajor())).hasSize(4);
        }

        @Test
        void refusesSomethingThatIsNotAMethod() {
            assertThatThrownBy(() -> falseCourseHeads(null)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class TenorsTogetherFalseCourseHeads {

        @Test
        void keepsOnlyTheHeadsThatLeaveTheTenorsAlone() {
            for (Row head : tenorsTogetherFalseCourseHeads(bristolMajor())) {
                assertThat(head.bellAt(7)).isEqualTo(7);
                assertThat(head.bellAt(8)).isEqualTo(8);
            }
        }

        @Test
        void findsNoneInPlainBobMinor() {
            assertThat(tenorsTogetherFalseCourseHeads(plainBobMinor())).isEmpty();
        }

        @Test
        void takesHowManyTenorsToHold() {
            assertThat(tenorsTogetherFalseCourseHeads(bristolMajor(), 3).size()).isLessThanOrEqualTo(4);
        }
    }

    @Nested
    class IsFalseAgainstPlainCourse {

        @Test
        void recognisesAHeadFromTheList() {
            Row head = falseCourseHeads(plainBobMinor()).get(0);
            assertThat(isFalseAgainstPlainCourse(plainBobMinor(), head)).isTrue();
        }

        @Test
        void doesNotCondemnRounds() {
            assertThat(isFalseAgainstPlainCourse(plainBobMinor(), Row.rounds(6)))
                    .isFalse();
        }
    }

    @Nested
    class SharedRows {

        @Test
        void findsTheRowsTwoBlocksHaveInCommon() {
            assertThat(sharedRows(parseRows("1234 2143"), parseRows("2143 2413")))
                    .containsExactly("2143");
        }

        @Test
        void findsNothingWhenTheBlocksAreDisjoint() {
            assertThat(sharedRows(parseRows("1234"), parseRows("2143"))).isEmpty();
        }

        @Test
        void reportsARowOnceHoweverManyTimesItAppears() {
            assertThat(sharedRows(parseRows("1234 1234"), parseRows("1234 1234")))
                    .containsExactly("1234");
        }

        @Test
        void findsThePlainCourseSharesEverythingWithItself() {
            List<Row> course = PlainCourse.plainCourse(plainBobMinor()).rows();
            assertThat(sharedRows(course, course)).hasSize(60);
        }
    }

    @Nested
    class TrueCourseHeads {

        @Test
        void dropsTheHeadsThatClash() {
            List<Row> kept = trueCourseHeads(plainBobMinor(), PlainCourse.courseLeadHeads(plainBobMinor()));
            assertThat(kept).allMatch(Row::isRounds);
        }

        @Test
        void keepsAHeadThatIsNotOnTheList() {
            assertThat(trueCourseHeads(bristolMajor(), List.of(parseRow("12345678"))))
                    .hasSize(1);
        }
    }

    @Nested
    class FalseCourseHeadGroups {

        @Test
        void groupsTheHeadsByTheShapeOfTheTransposition() {
            Map<String, List<Row>> groups = falseCourseHeadGroups(plainBobMinor());
            assertThat(groups).isNotEmpty();
            assertThat(groups.size())
                    .isLessThan(falseCourseHeads(plainBobMinor()).size());
        }

        @Test
        void keysTheGroupsOnTheCycleLengths() {
            for (String key : falseCourseHeadGroups(plainBobMinor()).keySet()) {
                assertThat(key).matches("^[0-9]+(\\.[0-9]+)*$");
            }
        }

        @Test
        void holdsEveryHeadSomewhere() {
            Map<String, List<Row>> groups = falseCourseHeadGroups(cambridgeMinor());
            int total = groups.values().stream().mapToInt(List::size).sum();
            assertThat(total).isEqualTo(falseCourseHeads(cambridgeMinor()).size());
        }
    }
}
