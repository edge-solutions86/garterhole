package io.github.rkaushikdev.garterhole.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CoerceTest {

    @Nested
    class ToNumber {

        @Test
        void readsANumberWrittenAsText() {
            assertThat(Coerce.toNumber("42")).isEqualTo(42);
            assertThat(Coerce.toNumber(" 4.5 ")).isEqualTo(4.5);
            assertThat(Coerce.toNumber("-3")).isEqualTo(-3);
        }

        @Test
        void letsANumberThrough() {
            assertThat(Coerce.toNumber(42)).isEqualTo(42);
        }

        @Test
        void refusesTheEmptyStringRatherThanReadingItAsZero() {
            assertThatThrownBy(() -> Coerce.toNumber("")).hasMessageContaining("must be a number");
            assertThatThrownBy(() -> Coerce.toNumber("   ")).isInstanceOf(ValidationException.class);
        }

        @Test
        void refusesTextThatIsNotANumber() {
            assertThatThrownBy(() -> Coerce.toNumber("eight", "the stage"))
                    .hasMessage("the stage must be a number, not \"eight\"");
        }

        @Test
        void refusesInfinityAndNaN() {
            assertThatThrownBy(() -> Coerce.toNumber(Double.POSITIVE_INFINITY)).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> Coerce.toNumber(Double.NaN)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class ToInteger {

        @Test
        void readsAWholeNumber() {
            assertThat(Coerce.toInteger("8")).isEqualTo(8);
        }

        @Test
        void refusesAFraction() {
            assertThatThrownBy(() -> Coerce.toInteger("4.5", "the stage"))
                    .hasMessage("the stage must be a whole number, not 4.5");
        }
    }

    @Nested
    class ToBoolean {

        @Test
        void readsTheWordsThatMeanTrue() {
            for (String word : List.of("true", "YES", " on ", "1")) {
                assertThat(Coerce.toBoolean(word)).isTrue();
            }
        }

        @Test
        void readsTheWordsThatMeanFalse() {
            for (String word : List.of("false", "no", "OFF", "0")) {
                assertThat(Coerce.toBoolean(word)).isFalse();
            }
        }

        @Test
        void letsABooleanThrough() {
            assertThat(Coerce.toBoolean(false)).isFalse();
        }

        @Test
        void refusesAnythingElseRatherThanCallingItTrue() {
            assertThatThrownBy(() -> Coerce.toBoolean("maybe")).hasMessageContaining("must be one of");
            assertThatThrownBy(() -> Coerce.toBoolean("")).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class ToDate {

        @Test
        void readsADateAFormSends() {
            assertThat(Coerce.toDate("2025-05-18")).isEqualTo(LocalDate.of(2025, 5, 18));
        }

        @Test
        void copiesADateItIsGiven() {
            LocalDate original = LocalDate.of(2025, 5, 18);
            LocalDate copy = Coerce.toDate(original);
            original.withYear(1999);
            assertThat(copy.getYear()).isEqualTo(2025);
        }

        @Test
        void refusesADateWrittenAnyOtherWay() {
            assertThatThrownBy(() -> Coerce.toDate("18/05/2025", "the date"))
                    .hasMessage("the date must be a date like 2025-05-18");
        }

        @Test
        void refusesADayThatDoesNotExist() {
            assertThatThrownBy(() -> Coerce.toDate("2025-02-30")).hasMessageContaining("is not a real date");
        }

        @Test
        void refusesADateThatIsNotADate() {
            assertThatThrownBy(() -> Coerce.toDate(null)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class ToList {

        @Test
        void splitsACommaSeparatedString() {
            assertThat(Coerce.toList("a,b,c")).containsExactly("a", "b", "c");
        }

        @Test
        void trimsAndDropsTheBlanks() {
            assertThat(Coerce.toList(" a , , b ")).containsExactly("a", "b");
        }

        @Test
        void letsAListThrough() {
            assertThat(Coerce.toList(List.of("a", "b"))).containsExactly("a", "b");
            assertThat(Coerce.toList(List.of(1, 2))).containsExactly("1", "2");
        }

        @Test
        void givesAnEmptyListForAnEmptyString() {
            assertThat(Coerce.toList("")).isEmpty();
        }

        @Test
        void refusesSomethingThatIsNeither() {
            assertThatThrownBy(() -> Coerce.toList(7))
                    .hasMessageContaining("must be a list or a comma separated string");
        }
    }

    @Nested
    class Convert {

        private final Map<String, Coerce.Conversion> conversions = conversions();

        private static Map<String, Coerce.Conversion> conversions() {
            Map<String, Coerce.Conversion> conversions = new LinkedHashMap<>();
            conversions.put("stage", Coerce::toInteger);
            conversions.put("cover", Coerce::toBoolean);
            conversions.put("from", Coerce::toDate);
            return conversions;
        }

        @Test
        void convertsWhatWasSent() {
            assertThat(Coerce.convert(Map.of("stage", "8", "cover", "yes"), conversions))
                    .isEqualTo(Map.of("stage", 8L, "cover", true));
        }

        @Test
        void leavesOutWhatWasNotSent() {
            assertThat(Coerce.convert(Map.of(), conversions)).isEmpty();
        }

        @Test
        void treatsAnEmptyValueAsNotSent() {
            assertThat(Coerce.convert(Map.of("stage", ""), conversions)).isEmpty();
        }

        @Test
        void namesTheFieldThatCouldNotBeConverted() {
            assertThatThrownBy(() -> Coerce.convert(Map.of("stage", "eight"), conversions))
                    .hasMessageContaining("stage must be a number");
        }

        @Test
        void refusesSomethingThatIsNotABagOfParameters() {
            assertThatThrownBy(() -> Coerce.convert(null, conversions)).hasMessageContaining("nothing to convert");
        }
    }

    /** Reading text the way JavaScript's {@code Number(text)} does. */
    @Nested
    class ReadNumber {

        @Test
        void readsWhatJavaScriptReads() {
            assertThat(Coerce.readNumber("")).isZero();
            assertThat(Coerce.readNumber(" \u00A012\uFEFF ")).isEqualTo(12);
            assertThat(Coerce.readNumber("1e3")).isEqualTo(1000);
            assertThat(Coerce.readNumber("0x10")).isEqualTo(16);
            assertThat(Coerce.readNumber(".5")).isEqualTo(0.5);
            assertThat(Coerce.readNumber("-Infinity")).isEqualTo(Double.NEGATIVE_INFINITY);
        }

        @Test
        void givesNaNForWhatJavaScriptRefuses() {
            for (String text : List.of("12abc", "--1", "0x", "1,000", "12d", "NaN", "infinity")) {
                assertThat(Coerce.readNumber(text)).as(text).isNaN();
            }
        }
    }
}
