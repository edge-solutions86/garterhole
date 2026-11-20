package io.github.rkaushikdev.garterhole.method;

import static io.github.rkaushikdev.garterhole.method.Stats.blowsOverCourse;
import static io.github.rkaushikdev.garterhole.method.Stats.methodStats;
import static io.github.rkaushikdev.garterhole.method.Stats.pathIsEven;
import static io.github.rkaushikdev.garterhole.method.Stats.placeBellOrder;
import static io.github.rkaushikdev.garterhole.method.Stats.workCounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StatsTest {

    private static Method plainBobMinor() {
        return Method.of("Plain", 6, "x1x1x1,2");
    }

    private static Method plainBobMajor() {
        return Method.of("Plain", 8, "x18x18x18x18,12");
    }

    private static Method cambridge() {
        return Method.of("Cambridge", 8, "x38x14x1258x36x14x58x16x78,12");
    }

    private static Method principle() {
        return Method.of("Test Principle", 5, "3.1.5.3.1.3.5.1.3.1");
    }

    @Nested
    class PlaceBellOrder {

        @Test
        void startsAtTheFirstWorkingPlaceBell() {
            assertThat(placeBellOrder(plainBobMinor()).get(0)).isEqualTo(2);
        }

        @Test
        void comesBackToWhereItStarted() {
            List<Integer> order = placeBellOrder(plainBobMinor());
            assertThat(order).hasSize(5);
            assertThat(new HashSet<>(order)).hasSize(5);
        }

        @Test
        void hasOneEntryPerWorkingBell() {
            assertThat(placeBellOrder(plainBobMajor())).hasSize(7);
            assertThat(placeBellOrder(cambridge())).hasSize(7);
        }

        @Test
        void givesThePlainBobMinorOrder() {
            assertThat(placeBellOrder(plainBobMinor())).containsExactly(2, 4, 6, 5, 3);
        }

        @Test
        void givesNothingWhenEveryBellHunts() {
            assertThat(placeBellOrder(Method.of("Test", 8, "x18x18x18x18,18"))).isEmpty();
        }

        @Test
        void worksForAPrincipleWhereEveryBellIsWorking() {
            assertThat(placeBellOrder(principle()).get(0)).isEqualTo(1);
        }
    }

    @Nested
    class BlowsOverCourse {

        @Test
        void countsAWholeCourse() {
            List<Integer> counts = blowsOverCourse(plainBobMinor(), 2);
            assertThat(counts.stream().mapToInt(Integer::intValue).sum()).isEqualTo(60);
        }

        @Test
        void spreadsAWorkingBellOverEveryPlace() {
            assertThat(blowsOverCourse(plainBobMinor(), 2)).allMatch(count -> count > 0);
        }

        @Test
        void keepsTheTrebleEven() {
            assertThat(new HashSet<>(blowsOverCourse(plainBobMinor(), 1))).hasSize(1);
        }
    }

    @Nested
    class WorkCounts {

        @Test
        void countsEachKindOfWork() {
            Map<String, Integer> counts = workCounts(plainBobMinor(), 2);
            assertThat(counts.get("lead")).isEqualTo(5);
            assertThat(counts.get("dodge")).isEqualTo(4);
            assertThat(counts.get("place")).isEqualTo(1);
        }

        @Test
        void givesTheTrebleOnlyLeadsAndLies() {
            assertThat(workCounts(plainBobMinor(), 1).keySet()).containsExactly("lead", "lie");
        }
    }

    @Nested
    class MethodStatsOfAMethod {

        @Test
        void gathersEverythingACollectionPrints() {
            MethodStats stats = methodStats(cambridge());
            assertThat(stats.title()).isEqualTo("Cambridge (Major)");
            assertThat(stats.leadLength()).isEqualTo(32);
            assertThat(stats.leadsInCourse()).isEqualTo(7);
            assertThat(stats.courseLength()).isEqualTo(224);
            assertThat(stats.className()).isEqualTo("Surprise");
        }

        @Test
        void namesTheHuntAndWorkingBells() {
            MethodStats stats = methodStats(plainBobMajor());
            assertThat(stats.huntBells()).containsExactly(1);
            assertThat(stats.workingBells()).containsExactly(2, 3, 4, 5, 6, 7, 8);
        }

        @Test
        void refusesSomethingThatIsNotAMethod() {
            assertThatThrownBy(() -> methodStats(null)).hasMessageContaining("a Method is needed");
        }
    }

    @Nested
    class PathIsEven {

        @Test
        void isTrueForAHuntBell() {
            assertThat(pathIsEven(plainBobMinor(), 1)).isTrue();
            assertThat(pathIsEven(cambridge(), 1)).isTrue();
        }

        @Test
        void isTrueForAWorkingBellInPlainBobWhichPlainHuntsAllLead() {
            assertThat(pathIsEven(plainBobMinor(), 2)).isTrue();
        }

        @Test
        void isFalseForABellThatSpendsLongerInSomePlacesThanOthers() {
            Method surprise = cambridge();
            boolean uneven = List.of(2, 3, 4, 5, 6, 7, 8).stream().anyMatch(bell -> !pathIsEven(surprise, bell));
            assertThat(uneven).isTrue();
        }
    }
}
