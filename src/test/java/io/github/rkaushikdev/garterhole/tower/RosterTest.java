package io.github.rkaushikdev.garterhole.tower;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RosterTest {

    private static Instant day(int month, int date) {
        return LocalDate.of(2025, month, date).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static LocalDate on(int month, int date) {
        return LocalDate.of(2025, month, date);
    }

    private Roster roster;

    @BeforeEach
    void fill() {
        roster = new Roster();
        roster.attended("alice", day(1, 7))
                .attended("alice", day(1, 14))
                .attended("alice", day(1, 21))
                .attended("bob", day(1, 7))
                .attended("bob", day(1, 14))
                .attended("carol", day(1, 7));
    }

    @Nested
    class RecordingAttendance {

        @Test
        void countsTheDaysSomebodyCame() {
            assertThat(roster.countFor("alice")).isEqualTo(3);
            assertThat(roster.countFor("carol")).isEqualTo(1);
        }

        @Test
        void countsNobodyAsNeverHavingBeen() {
            assertThat(roster.countFor("nobody")).isZero();
            assertThat(roster.lastSeen("nobody")).isEmpty();
        }

        @Test
        void countsOneDayOnceHoweverManyTimesItIsRecorded() {
            roster.attended("carol", day(1, 7));
            roster.attended("carol", Instant.parse("2025-01-07T19:30:00Z"));
            assertThat(roster.countFor("carol")).isEqualTo(1);
        }

        @Test
        void refusesARecordItCannotUse() {
            assertThatThrownBy(() -> roster.attended("", day(1, 7))).hasMessageContaining("a ringer id");
            assertThatThrownBy(() -> roster.attended("alice", null)).hasMessageContaining("needs the date");
            assertThatThrownBy(() -> roster.attended("alice", null)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class ReadingTheRoster {

        @Test
        void listsTheDaysSomebodyCameEarliestFirst() {
            assertThat(roster.daysFor("bob")).containsExactly(on(1, 7), on(1, 14));
        }

        @Test
        void givesTheLastDaySomebodyCame() {
            assertThat(roster.lastSeen("alice")).hasValue(on(1, 21));
        }

        @Test
        void saysWhoCameOnADay() {
            assertThat(roster.whoCame(day(1, 7))).containsExactly("alice", "bob", "carol");
            assertThat(roster.whoCame(day(1, 21))).containsExactly("alice");
        }

        @Test
        void saysNobodyCameOnADayNobodyCame() {
            assertThat(roster.whoCame(day(2, 1))).isEmpty();
        }

        @Test
        void listsEveryDayAnybodyCame() {
            assertThat(roster.days()).containsExactly(on(1, 7), on(1, 14), on(1, 21));
        }
    }

    @Nested
    class Attendance {

        @Test
        void listsEverybodyMostRegularFirst() {
            assertThat(roster.attendance()).extracting(Roster.Line::ringerId).containsExactly("alice", "bob", "carol");
        }

        @Test
        void givesEachOfThemTheirCountAndTheirLastDay() {
            assertThat(roster.attendance().get(0)).isEqualTo(new Roster.Line("alice", 3, on(1, 21)));
        }

        @Test
        void breaksATieByName() {
            Roster other = new Roster().attended("zoe", day(1, 7)).attended("adam", day(1, 7));
            assertThat(other.attendance()).extracting(Roster.Line::ringerId).containsExactly("adam", "zoe");
        }

        @Test
        void listsNobodyForAnEmptyRoster() {
            assertThat(new Roster().attendance()).isEmpty();
        }
    }

    @Nested
    class MissingSince {

        @Test
        void listsWhoHasNotBeenSinceADate() {
            assertThat(roster.missingSince(day(1, 20)))
                    .extracting(Roster.Absence::ringerId)
                    .containsExactly("bob", "carol");
        }

        @Test
        void listsNobodyWhenEverybodyHasBeen() {
            assertThat(roster.missingSince(day(1, 1))).isEmpty();
        }

        @Test
        void givesTheirLastDaySoSomebodyKnowsHowLongItHasBeen() {
            assertThat(roster.missingSince(day(1, 20)).get(0).lastSeen()).isEqualTo(on(1, 14));
        }

        @Test
        void refusesSomethingThatIsNotADate() {
            assertThatThrownBy(() -> roster.missingSince(null)).isInstanceOf(ValidationException.class);
        }
    }
}
