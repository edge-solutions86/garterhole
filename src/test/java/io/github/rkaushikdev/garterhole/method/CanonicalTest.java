package io.github.rkaushikdev.garterhole.method;

import static io.github.rkaushikdev.garterhole.method.Canonical.canonicalForm;
import static io.github.rkaushikdev.garterhole.method.Canonical.canonicalNotation;
import static io.github.rkaushikdev.garterhole.method.Canonical.duplicateSpellings;
import static io.github.rkaushikdev.garterhole.method.Canonical.groupBySameLead;
import static io.github.rkaushikdev.garterhole.method.Canonical.isReverseOfMethod;
import static io.github.rkaushikdev.garterhole.method.Canonical.isRotationOfMethod;
import static io.github.rkaushikdev.garterhole.method.Canonical.reversedForm;
import static io.github.rkaushikdev.garterhole.method.Canonical.rotations;
import static io.github.rkaushikdev.garterhole.notation.NotationParser.parseChanges;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.core.Change;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CanonicalTest {

    private static Method plainBobMajor() {
        return Method.of("Plain", 8, "x18x18x18x18,12");
    }

    /** The same lead, written starting one change further on. */
    private static Method rotated() {
        return Method.of("Rotated", 8, rotations(plainBobMajor().changes()).get(1));
    }

    private static Method cambridge() {
        return Method.of("Cambridge", 8, "x38x14x1258x36x14x58x16x78,12");
    }

    private static Method bristol() {
        return Method.of("Bristol", 8, "x58x14.58x58.36.14x14.58x14x18,18");
    }

    @Nested
    class Rotations {

        @Test
        void givesOneRotationPerChange() {
            assertThat(rotations(parseChanges("x18x12", 8))).hasSize(4);
        }

        @Test
        void startsEachRotationAtADifferentChange() {
            assertThat(rotations(parseChanges("x18x12", 8))).containsExactly("x18x12", "18x12x", "x12x18", "12x18x");
        }

        @Test
        void givesNothingForNothing() {
            assertThat(rotations(List.of())).isEmpty();
        }
    }

    @Nested
    class CanonicalNotation {

        @Test
        void isTheSameForEveryRotation() {
            List<Change> changes = parseChanges("x18x12", 8);
            for (String rotation : rotations(changes)) {
                assertThat(canonicalNotation(parseChanges(rotation, 8))).isEqualTo(canonicalNotation(changes));
            }
        }

        @Test
        void isEmptyForNothing() {
            assertThat(canonicalNotation(List.of())).isEmpty();
        }
    }

    @Nested
    class CanonicalForm {

        @Test
        void keepsTheStageSoTwoStagesNeverCollide() {
            assertThat(canonicalForm(plainBobMajor())).startsWith("8:");
        }

        @Test
        void isTheSameForAMethodWrittenFromAnotherStartingPoint() {
            assertThat(canonicalForm(plainBobMajor())).isEqualTo(canonicalForm(rotated()));
        }

        @Test
        void isDifferentForTwoDifferentMethods() {
            assertThat(canonicalForm(plainBobMajor())).isNotEqualTo(canonicalForm(cambridge()));
        }

        @Test
        void refusesSomethingThatIsNotAMethod() {
            assertThatThrownBy(() -> canonicalForm(null)).hasMessageContaining("a Method is needed");
        }
    }

    @Nested
    class ComparingMethods {

        @Test
        void recognisesARotation() {
            assertThat(isRotationOfMethod(plainBobMajor(), rotated())).isTrue();
            assertThat(isRotationOfMethod(plainBobMajor(), cambridge())).isFalse();
        }

        @Test
        void recognisesAMethodAsItsOwnReverseWhenItIsDouble() {
            assertThat(isReverseOfMethod(bristol(), bristol())).isTrue();
        }

        @Test
        void doesNotCallAnOrdinaryMethodItsOwnReverse() {
            assertThat(isReverseOfMethod(cambridge(), cambridge())).isFalse();
        }

        @Test
        void givesTheReversedFormOfAMethod() {
            assertThat(reversedForm(plainBobMajor())).isNotEqualTo(canonicalForm(plainBobMajor()));
        }
    }

    @Nested
    class Grouping {

        @Test
        void putsEverySpellingOfOneMethodTogether() {
            Map<String, List<Method>> groups = groupBySameLead(List.of(plainBobMajor(), rotated(), cambridge()));
            assertThat(groups).hasSize(2);
            assertThat(groups.values().stream()
                            .filter(bucket -> bucket.size() == 2)
                            .findFirst()
                            .orElseThrow())
                    .hasSize(2);
        }

        @Test
        void givesAnEmptyMapForNothing() {
            assertThat(groupBySameLead(List.of())).isEmpty();
        }

        @Test
        void findsTheSpellingsOfSomethingAlreadyInTheList() {
            List<Method> duplicates = duplicateSpellings(List.of(plainBobMajor(), rotated(), cambridge()));
            assertThat(duplicates).extracting(Method::name).containsExactly("Rotated");
        }

        @Test
        void findsNoneInAListOfDifferentMethods() {
            assertThat(duplicateSpellings(List.of(plainBobMajor(), cambridge(), bristol())))
                    .isEmpty();
        }
    }
}
