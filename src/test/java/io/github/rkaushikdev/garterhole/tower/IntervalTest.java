package io.github.rkaushikdev.garterhole.tower;

import static io.github.rkaushikdev.garterhole.tower.Interval.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IntervalTest {

    @Nested
    class IntervalItself {

        @Test
        void keepsItsStartAndEndAndMeasuresItself() {
            Interval slot = of(10, 30);
            assertThat(slot.start()).isEqualTo(10);
            assertThat(slot.end()).isEqualTo(30);
            assertThat(slot.length()).isEqualTo(20);
        }

        @Test
        void isFrozen() {
            assertThat(Interval.class.isRecord()).isTrue();
        }

        @Test
        void includesItsStartAndExcludesItsEnd() {
            Interval slot = of(10, 20);
            assertThat(slot.contains(10)).isTrue();
            assertThat(slot.contains(19)).isTrue();
            assertThat(slot.contains(20)).isFalse();
        }

        @Test
        void refusesAnEndThatIsNotAfterTheStart() {
            assertThatThrownBy(() -> of(10, 10)).hasMessageContaining("cannot end at 10 having started at 10");
            assertThatThrownBy(() -> of(10, 5)).isInstanceOf(ValidationException.class);
        }

        @Test
        void refusesAMomentThatIsNotANumber() {
            assertThatThrownBy(() -> of(Double.POSITIVE_INFINITY, 20)).hasMessageContaining("is not a moment in time");
            assertThatThrownBy(() -> of(10, Double.NaN)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class ComparingIntervals {

        @Test
        void findsAnOverlap() {
            assertThat(of(0, 10).overlaps(of(5, 15))).isTrue();
            assertThat(of(0, 10).overlaps(of(10, 20))).isFalse();
        }

        @Test
        void findsOneIntervalInsideAnother() {
            assertThat(of(0, 20).covers(of(5, 15))).isTrue();
            assertThat(of(5, 15).covers(of(0, 20))).isFalse();
            assertThat(of(0, 20).covers(of(0, 20))).isTrue();
        }

        @Test
        void takesThePartTwoShare() {
            assertThat(of(0, 10).intersect(of(5, 15))).get().hasToString("[5, 10)");
        }

        @Test
        void givesNothingWhenTheyShareNothing() {
            assertThat(of(0, 10).intersect(of(10, 20))).isEmpty();
        }

        @Test
        void comparesByContent() {
            assertThat(of(0, 10)).isEqualTo(of(0, 10));
            assertThat(of(0, 10)).isNotEqualTo(of(0, 11));
            assertThat(of(0, 10).equals("0-10")).isFalse();
        }
    }

    @Nested
    class IntervalSetItself {

        @Test
        void startsEmpty() {
            IntervalSet set = new IntervalSet();
            assertThat(set.size()).isZero();
            assertThat(set.total()).isZero();
        }

        @Test
        void keepsIntervalsInOrder() {
            assertThat(new IntervalSet(List.of(of(30, 40), of(0, 10)))).hasToString("[0, 10) [30, 40)");
        }

        @Test
        void mergesIntervalsThatOverlap() {
            IntervalSet set = new IntervalSet(List.of(of(0, 10), of(5, 20)));
            assertThat(set.size()).isEqualTo(1);
            assertThat(set).hasToString("[0, 20)");
        }

        @Test
        void mergesIntervalsThatMeetExactly() {
            assertThat(new IntervalSet(List.of(of(0, 10), of(10, 20)))).hasToString("[0, 20)");
        }

        @Test
        void mergesARunOfIntervalsIntoOne() {
            assertThat(new IntervalSet(List.of(of(0, 10), of(30, 40), of(5, 35))))
                    .hasToString("[0, 40)");
        }

        @Test
        void addsUpTheTimeItHolds() {
            assertThat(new IntervalSet(List.of(of(0, 10), of(20, 25))).total()).isEqualTo(15);
        }

        @Test
        void refusesAnythingThatIsNotAnInterval() {
            assertThatThrownBy(() -> new IntervalSet(Arrays.asList((Interval) null)))
                    .hasMessageContaining("only an Interval");
        }
    }

    @Nested
    class ReadingAnIntervalSet {

        private IntervalSet set() {
            return new IntervalSet(List.of(of(0, 10), of(20, 30)));
        }

        @Test
        void saysWhetherItHoldsAMoment() {
            assertThat(set().contains(5)).isTrue();
            assertThat(set().contains(15)).isFalse();
        }

        @Test
        void saysWhetherItCoversAWholeInterval() {
            assertThat(set().covers(of(2, 8))).isTrue();
            assertThat(set().covers(of(8, 22))).isFalse();
        }

        @Test
        void iteratesInOrder() {
            List<Double> starts = new ArrayList<>();
            set().forEach(slot -> starts.add(slot.start()));
            assertThat(starts).containsExactly(0.0, 20.0);
        }
    }

    @Nested
    class IntersectingSets {

        @Test
        void keepsOnlyWhatBothHold() {
            IntervalSet left = new IntervalSet(List.of(of(0, 10), of(20, 30)));
            IntervalSet right = new IntervalSet(List.of(of(5, 25)));
            assertThat(left.intersect(right)).hasToString("[5, 10) [20, 25)");
        }

        @Test
        void givesNothingWhenTheyShareNothing() {
            IntervalSet left = new IntervalSet(List.of(of(0, 10)));
            IntervalSet right = new IntervalSet(List.of(of(20, 30)));
            assertThat(left.intersect(right).size()).isZero();
        }

        @Test
        void givesEverythingWhenOneCoversTheOther() {
            IntervalSet left = new IntervalSet(List.of(of(0, 30)));
            IntervalSet right = new IntervalSet(List.of(of(5, 10)));
            assertThat(left.intersect(right)).hasToString("[5, 10)");
        }
    }
}
