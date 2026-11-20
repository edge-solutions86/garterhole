package io.github.rkaushikdev.garterhole.tower;

import static io.github.rkaushikdev.garterhole.tower.CompetencyRegister.Level.CONDUCTS;
import static io.github.rkaushikdev.garterhole.tower.CompetencyRegister.Level.CONFIDENT;
import static io.github.rkaushikdev.garterhole.tower.CompetencyRegister.Level.INSIDE;
import static io.github.rkaushikdev.garterhole.tower.CompetencyRegister.Level.LEARNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.tower.CompetencyRegister.Entry;
import io.github.rkaushikdev.garterhole.tower.CompetencyRegister.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CompetencyRegisterTest {

    private static final String CAMBRIDGE = "Cambridge Surprise Major";
    private static final String PLAIN_BOB = "Plain Bob Major";

    private CompetencyRegister register;

    @BeforeEach
    void fill() {
        register = new CompetencyRegister();
        register.record("alice", CAMBRIDGE, CONDUCTS)
                .record("alice", PLAIN_BOB, CONDUCTS)
                .record("bob", PLAIN_BOB, CONFIDENT)
                .record("carol", PLAIN_BOB, LEARNING)
                .record("carol", CAMBRIDGE, LEARNING);
    }

    @Nested
    class Levels {

        @Test
        void ranksThemFromLeastToMostAble() {
            assertThat(CompetencyRegister.LEVELS)
                    .extracting(Level::label)
                    .containsExactly("learning", "inside", "confident", "conducts");
            assertThat(LEARNING.rank()).isLessThan(CONDUCTS.rank());
        }

        @Test
        void refusesALevelNobodyUses() {
            assertThatThrownBy(() -> Level.of("expert")).hasMessageContaining("is not a level of competency");
        }

        @Test
        void comparesTwoLevels() {
            assertThat(CONFIDENT.meets(INSIDE)).isTrue();
            assertThat(INSIDE.meets(INSIDE)).isTrue();
            assertThat(LEARNING.meets(INSIDE)).isFalse();
        }
    }

    @Nested
    class RecordingCompetency {

        @Test
        void writesALevelDown() {
            assertThat(register.levelOf("bob", PLAIN_BOB)).hasValue(CONFIDENT);
        }

        @Test
        void givesNothingForAMethodARingerHasNeverRung() {
            assertThat(register.levelOf("bob", CAMBRIDGE)).isEmpty();
            assertThat(register.levelOf("nobody", PLAIN_BOB)).isEmpty();
        }

        @Test
        void replacesAnEarlierRecord() {
            register.record("bob", PLAIN_BOB, CONDUCTS);
            assertThat(register.levelOf("bob", PLAIN_BOB)).hasValue(CONDUCTS);
        }

        @Test
        void refusesARecordItCannotUse() {
            assertThatThrownBy(() -> register.record("", PLAIN_BOB, INSIDE)).hasMessageContaining("a ringer id");
            assertThatThrownBy(() -> register.record("bob", "", INSIDE)).hasMessageContaining("a method title");
            assertThatThrownBy(() -> register.record("bob", PLAIN_BOB, Level.of("expert")))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class Forgetting {

        @Test
        void takesAMethodOut() {
            assertThat(register.forget("bob", PLAIN_BOB)).isTrue();
            assertThat(register.levelOf("bob", PLAIN_BOB)).isEmpty();
        }

        @Test
        void saysSoWhenThereWasNothingToForget() {
            assertThat(register.forget("bob", CAMBRIDGE)).isFalse();
            assertThat(register.forget("nobody", PLAIN_BOB)).isFalse();
        }
    }

    @Nested
    class CanRing {

        @Test
        void acceptsARingerAtOrAboveTheLevelAskedFor() {
            assertThat(register.canRing("bob", PLAIN_BOB)).isTrue();
            assertThat(register.canRing("bob", PLAIN_BOB, CONFIDENT)).isTrue();
        }

        @Test
        void rejectsARingerBelowIt() {
            assertThat(register.canRing("carol", PLAIN_BOB)).isFalse();
            assertThat(register.canRing("bob", PLAIN_BOB, CONDUCTS)).isFalse();
        }

        @Test
        void rejectsARingerWhoHasNeverRungIt() {
            assertThat(register.canRing("bob", CAMBRIDGE)).isFalse();
        }
    }

    @Nested
    class RepertoireOf {

        @Test
        void listsWhatARingerCanRingInTitleOrder() {
            assertThat(register.repertoireOf("alice"))
                    .containsExactly(new Entry(CAMBRIDGE, CONDUCTS), new Entry(PLAIN_BOB, CONDUCTS));
        }

        @Test
        void givesNothingForARingerWithNoRecord() {
            assertThat(register.repertoireOf("nobody")).isEmpty();
        }
    }

    @Nested
    class WhoCanRing {

        @Test
        void listsTheBandMembersWhoAreUpToIt() {
            assertThat(register.whoCanRing(PLAIN_BOB)).containsExactly("alice", "bob");
        }

        @Test
        void takesTheLevelIntoAccount() {
            assertThat(register.whoCanRing(PLAIN_BOB, LEARNING)).containsExactly("alice", "bob", "carol");
            assertThat(register.whoCanRing(PLAIN_BOB, CONDUCTS)).containsExactly("alice");
        }

        @Test
        void givesNobodyForAMethodNobodyRings() {
            assertThat(register.whoCanRing("Bristol Surprise Major")).isEmpty();
        }
    }

    @Nested
    class Coverage {

        @Test
        void countsHowManyCanRingEachMethodMostFirst() {
            assertThat(register.coverage()).containsExactly(entry(PLAIN_BOB, 2), entry(CAMBRIDGE, 1));
        }

        @Test
        void countsMoreAtALowerLevel() {
            assertThat(register.coverage(LEARNING)).containsEntry(CAMBRIDGE, 2);
        }

        @Test
        void countsNothingForAnEmptyRegister() {
            assertThat(new CompetencyRegister().coverage()).isEmpty();
        }
    }

    @Nested
    class Titles {

        @Test
        void listsEveryMethodAnybodyHasRung() {
            assertThat(register.titles()).containsExactly(CAMBRIDGE, PLAIN_BOB);
        }

        @Test
        void listsNothingForAnEmptyRegister() {
            assertThat(new CompetencyRegister().titles()).isEmpty();
        }
    }
}
