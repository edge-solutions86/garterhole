package io.github.rkaushikdev.garterhole.composition;

import static io.github.rkaushikdev.garterhole.composition.Lengths.Kind.PEAL;
import static io.github.rkaushikdev.garterhole.composition.Lengths.Kind.QUARTER_PEAL;
import static io.github.rkaushikdev.garterhole.composition.Lengths.Kind.TOUCH;
import static io.github.rkaushikdev.garterhole.composition.Lengths.checkLength;
import static io.github.rkaushikdev.garterhole.composition.Lengths.classifyLength;
import static io.github.rkaushikdev.garterhole.composition.Lengths.extentLength;
import static io.github.rkaushikdev.garterhole.composition.Lengths.extentsIn;
import static io.github.rkaushikdev.garterhole.composition.Lengths.isDateTouch;
import static io.github.rkaushikdev.garterhole.composition.Lengths.needsWholeExtents;
import static io.github.rkaushikdev.garterhole.composition.Lengths.pealLength;
import static io.github.rkaushikdev.garterhole.composition.Lengths.quarterLength;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LengthsTest {

    @Nested
    class ExtentLength {

        @Test
        void countsTheRowsAtEachStage() {
            assertThat(extentLength(4)).isEqualTo(24);
            assertThat(extentLength(5)).isEqualTo(120);
            assertThat(extentLength(6)).isEqualTo(720);
            assertThat(extentLength(7)).isEqualTo(5040);
            assertThat(extentLength(8)).isEqualTo(40320);
        }

        @Test
        void refusesAStageThatIsNotAWholeNumber() {
            assertThatThrownBy(() -> extentLength(0)).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> extentLength(-6)).hasMessageContaining("has no extent");
        }
    }

    @Nested
    class NeedsWholeExtents {

        @Test
        void appliesBelowMajor() {
            assertThat(needsWholeExtents(5)).isTrue();
            assertThat(needsWholeExtents(6)).isTrue();
            assertThat(needsWholeExtents(7)).isTrue();
        }

        @Test
        void doesNotApplyFromMajorUpwards() {
            assertThat(needsWholeExtents(8)).isFalse();
            assertThat(needsWholeExtents(12)).isFalse();
        }
    }

    @Nested
    class PealLength {

        @Test
        void isFiveThousandAndFortyOnDoublesAndMinor() {
            assertThat(pealLength(5)).isEqualTo(5040);
            assertThat(pealLength(6)).isEqualTo(5040);
        }

        @Test
        void isOneExtentOnTriples() {
            assertThat(pealLength(7)).isEqualTo(5040);
        }

        @Test
        void isFiveThousandFromMajorUpwards() {
            assertThat(pealLength(8)).isEqualTo(Lengths.PEAL_MINIMUM);
            assertThat(pealLength(12)).isEqualTo(5000);
        }

        @Test
        void roundsUpToWholeExtentsOnMinimus() {
            assertThat(pealLength(4)).isEqualTo(5016);
        }
    }

    @Nested
    class QuarterLength {

        @Test
        void doesNotDependOnTheStage() {
            assertThat(quarterLength()).isEqualTo(Lengths.QUARTER_MINIMUM);
            assertThat(quarterLength()).isEqualTo(1250);
        }
    }

    @Nested
    class ClassifyLength {

        @Test
        void callsAFullLengthAPeal() {
            assertThat(classifyLength(5040, 6)).isEqualTo(PEAL);
            assertThat(classifyLength(5056, 8)).isEqualTo(PEAL);
        }

        @Test
        void callsAShorterLengthAQuarterPeal() {
            assertThat(classifyLength(1260, 5)).isEqualTo(QUARTER_PEAL);
            assertThat(classifyLength(1280, 8)).isEqualTo(QUARTER_PEAL);
        }

        @Test
        void callsAnythingShorterATouch() {
            assertThat(classifyLength(720, 6)).isEqualTo(TOUCH);
            assertThat(classifyLength(0, 8)).isEqualTo(TOUCH);
        }

        @Test
        void willNotCallAPartExtentOnMinorAPeal() {
            assertThat(classifyLength(5000, 6)).isEqualTo(QUARTER_PEAL);
        }

        @Test
        void refusesALengthThatIsNotAWholeNumber() {
            assertThatThrownBy(() -> classifyLength(-1, 8)).hasMessageContaining("cannot be -1 changes long");
            assertThatThrownBy(() -> classifyLength(Long.MIN_VALUE, 8)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class CheckLength {

        @Test
        void acceptsALengthThatQualifies() {
            assertThat(checkLength(5040, 6, PEAL)).isEqualTo(new Lengths.Check(true, 5040, Optional.empty()));
        }

        @Test
        void saysHowFarShortALengthIs() {
            assertThat(checkLength(4000, 8, PEAL).reason())
                    .hasValue("a peal on 8 needs at least 5000 changes, not 4000");
        }

        @Test
        void saysWhenALengthIsNotWholeExtents() {
            assertThat(checkLength(5100, 6, PEAL).reason()).hasValue("on 6 a peal must be whole extents of 720");
        }

        @Test
        void doesNotApplyTheExtentRuleToAQuarter() {
            assertThat(checkLength(1260, 6, QUARTER_PEAL).ok()).isTrue();
        }

        @Test
        void refusesALengthItDoesNotKnowHowToCheck() {
            assertThatThrownBy(() -> checkLength(5040, 6, TOUCH))
                    .hasMessageContaining("is not a length to check against");
        }
    }

    @Nested
    class ExtentsIn {

        @Test
        void countsWholeExtents() {
            assertThat(extentsIn(5040, 6)).hasValue(7);
            assertThat(extentsIn(720, 6)).hasValue(1);
        }

        @Test
        void givesNothingWhenTheLengthIsNotWholeExtents() {
            assertThat(extentsIn(5000, 6)).isEmpty();
        }
    }

    @Nested
    class IsDateTouch {

        private final LocalDate date = LocalDate.of(2025, 7, 4);

        @Test
        void acceptsTheDayAndMonthWrittenEitherWayRound() {
            assertThat(isDateTouch(407, date)).isTrue();
            assertThat(isDateTouch(704, date)).isTrue();
        }

        @Test
        void acceptsTheYearOnItsOwn() {
            assertThat(isDateTouch(2025, date)).isTrue();
        }

        @Test
        void rejectsALengthThatIsNotTheDate() {
            assertThat(isDateTouch(1260, date)).isFalse();
        }

        @Test
        void refusesSomethingThatIsNotADate() {
            assertThatThrownBy(() -> isDateTouch(2025, null)).hasMessageContaining("a date is needed");
            assertThatThrownBy(() -> isDateTouch(2025, null)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class KindLabels {

        @Test
        void writesEachKindTheWayARecordDoes() {
            assertThat(PEAL).hasToString("peal");
            assertThat(QUARTER_PEAL.label()).isEqualTo("quarter peal");
            assertThat(Lengths.Kind.fromLabel("touch")).isEqualTo(TOUCH);
            assertThatThrownBy(() -> Lengths.Kind.fromLabel("half peal")).isInstanceOf(ValidationException.class);
        }
    }
}
