package io.github.rkaushikdev.garterhole.method;

import static io.github.rkaushikdev.garterhole.method.Symmetry.hasRotationalSymmetry;
import static io.github.rkaushikdev.garterhole.method.Symmetry.isDouble;
import static io.github.rkaushikdev.garterhole.method.Symmetry.isPalindromic;
import static io.github.rkaushikdev.garterhole.method.Symmetry.isRotationOf;
import static io.github.rkaushikdev.garterhole.method.Symmetry.reverseChange;
import static io.github.rkaushikdev.garterhole.method.Symmetry.reverseChanges;
import static io.github.rkaushikdev.garterhole.method.Symmetry.symmetryOf;
import static io.github.rkaushikdev.garterhole.notation.NotationParser.parseChanges;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.core.Change;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SymmetryTest {

    private static Method plainBobMinor() {
        return Method.of("Plain Bob", 6, "x1x1x1,2");
    }

    private static Method cambridgeMajor() {
        return Method.of("Cambridge Surprise", 8, "x38x14x1258x36x14x58x16x78,12");
    }

    private static Method bristolMajor() {
        return Method.of("Bristol Surprise", 8, "x58x14.58x58.36.14x14.58x14x18,18");
    }

    private static Method doubleNorwich() {
        return Method.of("Double Norwich Court Bob", 8, "x14x36x58x18,18");
    }

    @Nested
    class ReverseChange {

        @Test
        void turnsAPlaceAtTheFrontIntoAPlaceAtTheBack() {
            assertThat(reverseChange(Change.of(8, 1, 2))).hasToString("78");
        }

        @Test
        void leavesAChangeThatIsAlreadySymmetricAlone() {
            assertThat(reverseChange(Change.of(8, 1, 8))).hasToString("18");
            assertThat(reverseChange(Change.cross(8))).hasToString("x");
        }

        @Test
        void isItsOwnInverse() {
            Change change = Change.of(8, 1, 4);
            assertThat(reverseChange(reverseChange(change))).isEqualTo(change);
        }

        @Test
        void reversesAWholeLead() {
            assertThat(reverseChanges(parseChanges("x12x18", 8)))
                    .extracting(Change::toString)
                    .containsExactly("x", "78", "x", "18");
        }
    }

    @Nested
    class IsRotationOf {

        @Test
        void findsARotation() {
            assertThat(isRotationOf(parseChanges("x18x12", 8), parseChanges("x12x18", 8)))
                    .isTrue();
        }

        @Test
        void findsARunEqualToItself() {
            List<Change> changes = parseChanges("x18x12", 8);
            assertThat(isRotationOf(changes, changes)).isTrue();
        }

        @Test
        void rejectsADifferentRun() {
            assertThat(isRotationOf(parseChanges("x18x12", 8), parseChanges("x18x16", 8)))
                    .isFalse();
        }

        @Test
        void rejectsRunsOfDifferentLengths() {
            assertThat(isRotationOf(parseChanges("x18", 8), parseChanges("x18x12", 8)))
                    .isFalse();
        }
    }

    @Nested
    class IsPalindromic {

        @Test
        void findsTheStandardMethodsPalindromic() {
            assertThat(isPalindromic(plainBobMinor())).isTrue();
            assertThat(isPalindromic(cambridgeMajor())).isTrue();
            assertThat(isPalindromic(bristolMajor())).isTrue();
        }

        @Test
        void rejectsALeadWithNoAxis() {
            assertThat(isPalindromic(Method.of("Asymmetric", 6, "16.14.12"))).isFalse();
        }
    }

    @Nested
    class IsDouble {

        @Test
        void findsTheDoubleMethods() {
            assertThat(isDouble(bristolMajor())).isTrue();
            assertThat(isDouble(doubleNorwich())).isTrue();
        }

        @Test
        void rejectsMethodsThatAreNotDouble() {
            assertThat(isDouble(plainBobMinor())).isFalse();
            assertThat(isDouble(cambridgeMajor())).isFalse();
        }
    }

    @Nested
    class HasRotationalSymmetry {

        @Test
        void findsItInTheDoubleMethods() {
            assertThat(hasRotationalSymmetry(bristolMajor())).isTrue();
            assertThat(hasRotationalSymmetry(doubleNorwich())).isTrue();
        }

        @Test
        void doesNotFindItInPlainBob() {
            assertThat(hasRotationalSymmetry(plainBobMinor())).isFalse();
        }
    }

    @Nested
    class SymmetryOf {

        @Test
        void reportsEverySymmetryAMethodHas() {
            assertThat(symmetryOf(bristolMajor()))
                    .isEqualTo(new Symmetry.Facts(true, true, true, List.of("palindromic", "double", "rotational")));
        }

        @Test
        void reportsOnlyPalindromicForPlainBob() {
            assertThat(symmetryOf(plainBobMinor()).names()).containsExactly("palindromic");
        }

        @Test
        void refusesSomethingThatIsNotAMethod() {
            assertThatThrownBy(() -> symmetryOf(null)).hasMessageContaining("a Method is needed");
        }
    }
}
