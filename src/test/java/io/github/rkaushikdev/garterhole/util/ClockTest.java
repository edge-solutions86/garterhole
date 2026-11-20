package io.github.rkaushikdev.garterhole.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ClockTest {

    @Nested
    class SystemClock {

        @Test
        void readsTheRealTime() {
            Clock clock = Clock.system();
            long before = System.currentTimeMillis();
            long seen = clock.now();
            assertThat(seen).isGreaterThanOrEqualTo(before);
            assertThat(clock.date()).isInstanceOf(Instant.class);
        }
    }

    @Nested
    class FixedClockTest {

        @Test
        void standsStill() {
            FixedClock clock = new FixedClock(1000);
            assertThat(clock.now()).isEqualTo(1000);
            assertThat(clock.now()).isEqualTo(1000);
        }

        @Test
        void startsAtZeroByDefault() {
            assertThat(new FixedClock().now()).isZero();
        }

        @Test
        void takesADateToStartAt() {
            FixedClock clock = new FixedClock(Instant.parse("2025-01-01T00:00:00Z"));
            assertThat(Clock.isoString(clock.date())).isEqualTo("2025-01-01T00:00:00.000Z");
        }

        @Test
        void movesForwardWhenItIsToldTo() {
            FixedClock clock = new FixedClock(1000);
            clock.advance(500).advance(500);
            assertThat(clock.now()).isEqualTo(2000);
        }

        @Test
        void canBePutAtAMoment() {
            FixedClock clock = new FixedClock(1000);
            assertThat(clock.set(5000).now()).isEqualTo(5000);
            assertThat(clock.set(Instant.ofEpochMilli(7000)).now()).isEqualTo(7000);
        }

        @Test
        void refusesToStartOrMoveNowhere() {
            assertThatThrownBy(() -> new FixedClock((Instant) null))
                    .hasMessageContaining("is not a moment to start a clock at");
            assertThatThrownBy(() -> new FixedClock(0).advance(-1)).hasMessageContaining("cannot move forward by -1");
            assertThatThrownBy(() -> new FixedClock(0).advance(Long.MIN_VALUE)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class TheUnits {

        @Test
        void buildOnEachOther() {
            assertThat(Clock.MINUTE).isEqualTo(60 * Clock.SECOND);
            assertThat(Clock.HOUR).isEqualTo(60 * Clock.MINUTE);
            assertThat(Clock.DAY).isEqualTo(24 * Clock.HOUR);
        }
    }

    @Nested
    class DescribeDuration {

        @Test
        void writesMillisecondsUnderASecond() {
            assertThat(Clock.describeDuration(0)).isEqualTo("0ms");
            assertThat(Clock.describeDuration(250)).isEqualTo("250ms");
        }

        @Test
        void writesSecondsUnderAMinute() {
            assertThat(Clock.describeDuration(1500)).isEqualTo("1.5s");
            assertThat(Clock.describeDuration(59 * Clock.SECOND)).isEqualTo("59.0s");
        }

        @Test
        void writesMinutesUnderAnHour() {
            assertThat(Clock.describeDuration(5 * Clock.MINUTE)).isEqualTo("5m");
            assertThat(Clock.describeDuration(59 * Clock.MINUTE)).isEqualTo("59m");
        }

        @Test
        void writesHoursAndMinutesAboveThat() {
            assertThat(Clock.describeDuration(3 * Clock.HOUR + 5 * Clock.MINUTE))
                    .isEqualTo("3h 5m");
            assertThat(Clock.describeDuration(2 * Clock.HOUR)).isEqualTo("2h 0m");
        }

        @Test
        void refusesSomethingThatIsNotALengthOfTime() {
            assertThatThrownBy(() -> Clock.describeDuration(-1)).hasMessageContaining("is not a length of time");
            assertThatThrownBy(() -> Clock.describeDuration(Double.NaN)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class StartOfDay {

        @Test
        void dropsTheTime() {
            Instant moment = Instant.parse("2025-05-18T19:30:15Z");
            assertThat(Clock.isoString(Clock.startOfDay(moment))).isEqualTo("2025-05-18T00:00:00.000Z");
        }

        @Test
        void leavesAMomentAlreadyAtMidnightAlone() {
            Instant midnight = Instant.parse("2025-05-18T00:00:00Z");
            assertThat(Clock.startOfDay(midnight)).isEqualTo(midnight);
        }

        @Test
        void refusesSomethingThatIsNotADate() {
            assertThatThrownBy(() -> Clock.startOfDay(null)).hasMessageContaining("a date is needed");
            assertThatThrownBy(() -> Clock.startOfDay(null)).isInstanceOf(ValidationException.class);
        }
    }
}
