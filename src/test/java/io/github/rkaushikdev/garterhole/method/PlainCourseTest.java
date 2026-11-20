package io.github.rkaushikdev.garterhole.method;

import static io.github.rkaushikdev.garterhole.method.PlainCourse.courseLeadHeads;
import static io.github.rkaushikdev.garterhole.method.PlainCourse.courseLength;
import static io.github.rkaushikdev.garterhole.method.PlainCourse.isTrueInThePlain;
import static io.github.rkaushikdev.garterhole.method.PlainCourse.leadHeadMovement;
import static io.github.rkaushikdev.garterhole.method.PlainCourse.leadsInCourse;
import static io.github.rkaushikdev.garterhole.method.PlainCourse.plainCourse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.core.RowBlock;
import io.github.rkaushikdev.garterhole.core.RowParse;
import io.github.rkaushikdev.garterhole.method.PlainCourse.Movement;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PlainCourseTest {

    private static Method plainBobMinor() {
        return Method.of("Plain Bob", 6, "x1x1x1,2");
    }

    private static Method plainBobMajor() {
        return Method.of("Plain Bob", 8, "x18x18x18x18,12");
    }

    private static Method cambridgeMinor() {
        return Method.of("Cambridge Surprise", 6, "x36x14x12x36x14x56,12");
    }

    private static Method bristolMajor() {
        return Method.of("Bristol Surprise", 8, "x58x14.58x58.36.14x14.58x14x18,18");
    }

    @Nested
    class LeadsInCourse {

        @Test
        void countsFiveLeadsForPlainBobMinor() {
            assertThat(leadsInCourse(plainBobMinor())).isEqualTo(5);
        }

        @Test
        void countsSevenLeadsForPlainBobMajor() {
            assertThat(leadsInCourse(plainBobMajor())).isEqualTo(7);
            assertThat(leadsInCourse(bristolMajor())).isEqualTo(7);
        }

        @Test
        void countsFiveLeadsForCambridgeSurpriseMinor() {
            assertThat(leadsInCourse(cambridgeMinor())).isEqualTo(5);
        }

        @Test
        void refusesSomethingThatIsNotAMethod() {
            assertThatThrownBy(() -> leadsInCourse(null)).hasMessageContaining("a Method is needed");
        }
    }

    @Nested
    class CourseLength {

        @Test
        void multipliesTheLeadLengthByTheNumberOfLeads() {
            assertThat(courseLength(plainBobMinor())).isEqualTo(60);
            assertThat(courseLength(cambridgeMinor())).isEqualTo(120);
            assertThat(courseLength(plainBobMajor())).isEqualTo(112);
            assertThat(courseLength(bristolMajor())).isEqualTo(224);
        }
    }

    @Nested
    class CourseLeadHeads {

        @Test
        void startsAndFinishesOnRounds() {
            List<Row> heads = courseLeadHeads(plainBobMinor());
            assertThat(heads).hasSize(6);
            assertThat(heads.get(0).isRounds()).isTrue();
            assertThat(heads.get(heads.size() - 1).isRounds()).isTrue();
        }

        @Test
        void givesThePlainBobMinorLeadHeadsInOrder() {
            assertThat(courseLeadHeads(plainBobMinor()))
                    .extracting(Row::toString)
                    .containsExactly("123456", "135264", "156342", "164523", "142635", "123456");
        }

        @Test
        void startsFromAnyRowItIsGiven() {
            List<Row> heads = courseLeadHeads(plainBobMinor(), RowParse.parseRow("135264"));
            assertThat(heads.get(0)).hasToString("135264");
            assertThat(heads.get(heads.size() - 1)).hasToString("135264");
        }
    }

    @Nested
    class PlainCourseOfAMethod {

        @Test
        void ringsTheWholeCourseAndComesRound() {
            RowBlock course = plainCourse(plainBobMinor());
            assertThat(course.changeCount()).isEqualTo(60);
            assertThat(course.comesRound()).isTrue();
        }

        @Test
        void ringsASurpriseCourse() {
            RowBlock course = plainCourse(cambridgeMinor());
            assertThat(course.changeCount()).isEqualTo(120);
            assertThat(course.comesRound()).isTrue();
        }

        @Test
        void holdsEveryRowOnceApartFromTheRepeatOfRounds() {
            RowBlock course = plainCourse(plainBobMinor());
            assertThat(new HashSet<>(course.rows().subList(0, course.length() - 1)))
                    .hasSize(60);
        }
    }

    @Nested
    class IsTrueInThePlain {

        @Test
        void findsTheStandardMethodsTrue() {
            assertThat(isTrueInThePlain(plainBobMinor())).isTrue();
            assertThat(isTrueInThePlain(cambridgeMinor())).isTrue();
            assertThat(isTrueInThePlain(bristolMajor())).isTrue();
        }

        @Test
        void findsAMethodThatRepeatsARowFalse() {
            assertThat(isTrueInThePlain(Method.of("Repeats", 6, "x16x16x16x16x16x16x16")))
                    .isFalse();
        }
    }

    @Nested
    class LeadHeadMovement {

        @Test
        void reportsWhereEachBellGoesOverALead() {
            List<Movement> movement = leadHeadMovement(plainBobMinor());
            assertThat(movement).hasSize(6);
            assertThat(movement.get(0)).isEqualTo(new Movement(1, 1, 1));
            assertThat(movement.get(1)).isEqualTo(new Movement(2, 2, 4));
        }

        @Test
        void leavesTheHuntBellWhereItWas() {
            for (Movement entry : leadHeadMovement(cambridgeMinor())) {
                if (entry.bell() == 1) {
                    assertThat(entry.to()).isEqualTo(1);
                }
            }
        }
    }
}
