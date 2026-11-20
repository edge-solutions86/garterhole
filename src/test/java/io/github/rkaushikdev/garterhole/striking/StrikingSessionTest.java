package io.github.rkaushikdev.garterhole.striking;

import static io.github.rkaushikdev.garterhole.striking.Ringing.courseStrikes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.striking.StrikingSession.Options;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StrikingSessionTest {

    private static StrikingSession analyse(List<Strike> strikes) {
        return StrikingSession.analyse(6, strikes);
    }

    @Nested
    class Sessions {

        @Test
        void splitsTheStreamAndFitsAModel() {
            StrikingSession session = analyse(courseStrikes());
            assertThat(session.rowCount()).isEqualTo(61);
            assertThat(session.model().interval()).isCloseTo(250, byLessThan(0.000005));
        }

        @Test
        void isFrozen() {
            assertThat(Modifier.isFinal(StrikingSession.class.getModifiers())).isTrue();
            assertThat(Arrays.stream(StrikingSession.class.getDeclaredFields())
                            .filter(field -> !field.isSynthetic())
                            .map(Field::getModifiers))
                    .allMatch(Modifier::isFinal);
            assertThatThrownBy(() -> analyse(courseStrikes()).errors().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void findsPerfectRingingPerfect() {
            StrikingSession session = analyse(courseStrikes());
            assertThat(session.accuracy().rms()).isCloseTo(0, byLessThan(0.0000005));
            assertThat(session.faults()).isEmpty();
        }

        @Test
        void blamesTheBellThatStruckBadly() {
            StrikingSession session = analyse(courseStrikes((bell, row) -> bell == 4 ? 90 : 0));
            assertThat(session.byBell().get(4).rms())
                    .isGreaterThan(session.byBell().get(2).rms());
            assertThat(session.faults()).anyMatch(fault -> fault.bell() == 4);
        }

        @Test
        void countsTheFaultsByKind() {
            StrikingSession session = analyse(courseStrikes((bell, row) -> bell == 4 ? 90 : 0));
            assertThat(session.faultCounts().get(Fault.Kind.GAP)).isGreaterThan(0);
        }

        @Test
        void listsTheRowsWorthListeningToAgain() {
            StrikingSession session = analyse(courseStrikes((bell, row) -> row == 10 ? 200 : 0));
            assertThat(session.worstRows(50)).contains(10);
        }

        @Test
        void saysWhichRowsWereHandstrokes() {
            assertThat(analyse(courseStrikes()).strokes().subList(0, 3)).containsExactly(true, false, true);
        }

        @Test
        void givesBackTheRowsThatWereRung() {
            StrikingSession session = analyse(courseStrikes());
            assertThat(session.rungRows().get(0)).hasToString("123456");
            assertThat(session.rungRows()).hasSize(61);
        }

        @Test
        void takesAModelOfItsOwnInsteadOfFittingOne() {
            TimingModel model = TimingModel.of(6, 300);
            StrikingSession session = StrikingSession.analyse(
                    6, courseStrikes(), Options.defaults().withModel(model));
            assertThat(session.model()).isSameAs(model);
            assertThat(session.accuracy().rms()).isGreaterThan(0);
        }

        @Test
        void refusesOptionsItCannotUse() {
            StrikeStream stream = StrikeStream.from(6, courseStrikes());
            assertThatThrownBy(() -> new StrikingSession(stream, null)).hasMessageContaining("needs its options");
            assertThatThrownBy(() -> new StrikingSession(null)).hasMessageContaining("needs a StrikeStream");
        }

        @Test
        void refusesARecordingTooShortToSayAnythingAbout() {
            List<Strike> strikes = courseStrikes().subList(0, 6);
            assertThatThrownBy(() -> analyse(strikes)).hasMessageContaining("at least two rows");
        }
    }

    @Nested
    class LenientSessions {

        @Test
        void stopsAtTheFirstRowThatIsNotAPermutation() {
            List<Strike> strikes = new ArrayList<>(courseStrikes().subList(0, 12));
            strikes.addAll(List.of(
                    new Strike(1, 20000),
                    new Strike(1, 20250),
                    new Strike(3, 20500),
                    new Strike(4, 20750),
                    new Strike(5, 21000),
                    new Strike(6, 21250)));
            StrikingSession session = new StrikingSession(
                    new StrikeStream(6, strikes), Options.defaults().withLenient(true));
            assertThat(session.rowCount()).isEqualTo(2);
            assertThat(session.stoppedAt()).hasValue(2);
            assertThat(session.stoppedReason()).get().asString().contains("not a permutation");
        }

        @Test
        void reportsNothingStoppedItWhenTheStreamIsClean() {
            StrikingSession session = StrikingSession.analyse(
                    6, courseStrikes(), Options.defaults().withLenient(true));
            assertThat(session.stoppedAt()).isEmpty();
            assertThat(session.stoppedReason()).isEmpty();
        }

        @Test
        void stillRefusesABadRowWhenItIsNotLenient() {
            List<Strike> row = List.of(
                    new Strike(1, 0),
                    new Strike(1, 250),
                    new Strike(3, 500),
                    new Strike(4, 750),
                    new Strike(5, 1000),
                    new Strike(6, 1250));
            List<Strike> strikes = new ArrayList<>(row);
            strikes.addAll(row);
            assertThatThrownBy(() -> analyse(strikes)).isInstanceOf(ValidationException.class);
        }
    }
}
