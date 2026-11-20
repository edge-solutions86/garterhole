package io.github.rkaushikdev.garterhole.tower;

import static io.github.rkaushikdev.garterhole.tower.Practice.candidates;
import static io.github.rkaushikdev.garterhole.tower.Practice.describeItem;
import static io.github.rkaushikdev.garterhole.tower.Practice.planPractice;
import static io.github.rkaushikdev.garterhole.tower.Practice.readyFor;
import static io.github.rkaushikdev.garterhole.tower.Ringer.ringer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.tower.CompetencyRegister.Level;
import io.github.rkaushikdev.garterhole.tower.Practice.Candidate;
import io.github.rkaushikdev.garterhole.tower.Practice.Item;
import io.github.rkaushikdev.garterhole.tower.Practice.Purpose;
import io.github.rkaushikdev.garterhole.tower.Practice.Setting;
import io.github.rkaushikdev.garterhole.tower.Ringer.Details;
import io.github.rkaushikdev.garterhole.tower.Ringer.Role;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PracticeTest {

    private static final String ROUNDS = "Plain Bob Minor";
    private static final String HARD = "Cambridge Surprise Minor";
    private static final List<String> NAMES = List.of("alice", "bob", "carol", "dan", "erin", "frank");

    private static Tower tower() {
        return new Tower(
                "Test",
                IntStream.rangeClosed(1, 6)
                        .mapToObj(number -> new Bell(number, 200 * number))
                        .toList());
    }

    private static List<Ringer> present() {
        return NAMES.stream()
                .map(name -> ringer(
                        name,
                        Details.NONE
                                .withId(name)
                                .withRoles(name.equals("alice") ? List.of(Role.CONDUCTOR) : List.of())))
                .toList();
    }

    private static CompetencyRegister register() {
        CompetencyRegister competency = new CompetencyRegister();
        for (String name : NAMES) {
            competency.record(name, ROUNDS, Level.CONFIDENT);
        }
        competency.record("alice", HARD, Level.CONDUCTS);
        competency.record("bob", HARD, Level.LEARNING);
        return competency;
    }

    private static Setting setting(CompetencyRegister competency) {
        return Setting.of(tower(), present(), competency);
    }

    @Nested
    class ReadyFor {

        @Test
        void countsTheBandWhoAreUpToAMethod() {
            assertThat(readyFor(register(), present(), ROUNDS, Level.INSIDE)).isEqualTo(6);
            assertThat(readyFor(register(), present(), HARD, Level.INSIDE)).isEqualTo(1);
        }

        @Test
        void countsMoreAtALowerLevel() {
            assertThat(readyFor(register(), present(), HARD, Level.LEARNING)).isEqualTo(2);
        }
    }

    @Nested
    class Candidates {

        @Test
        void listsWhatTheBandCouldRingBestFirst() {
            List<Candidate> found = candidates(setting(register()));
            assertThat(found.get(0).title()).isEqualTo(ROUNDS);
            assertThat(found.get(0).ready()).isEqualTo(6);
        }

        @Test
        void includesAMethodOnlyOneOfThemCanRing() {
            assertThat(candidates(setting(register())))
                    .extracting(Candidate::title)
                    .contains(HARD);
        }

        @Test
        void leavesOutAMethodTheTowerCannotRing() {
            CompetencyRegister competency = register().record("alice", "Cambridge Surprise Major", Level.CONDUCTS);
            assertThat(candidates(setting(competency)))
                    .extracting(Candidate::title)
                    .doesNotContain("Cambridge Surprise Major");
        }

        @Test
        void leavesOutATitleItCannotRead() {
            CompetencyRegister competency = register().record("alice", "not a title", Level.CONDUCTS);
            assertThat(candidates(setting(competency)))
                    .extracting(Candidate::title)
                    .doesNotContain("not a title");
        }

        @Test
        void refusesASettingItCannotUse() {
            assertThatThrownBy(() -> candidates(null)).hasMessageContaining("needs a setting");
            assertThatThrownBy(() -> candidates(Setting.of(null, present(), register())))
                    .hasMessageContaining("needs a Tower");
            assertThatThrownBy(() -> candidates(Setting.of(tower(), List.of(), register())))
                    .hasMessageContaining("somebody to turn up");
            assertThatThrownBy(() -> candidates(Setting.of(tower(), present(), null)))
                    .hasMessageContaining("what the band can ring");
        }
    }

    @Nested
    class PlanPractice {

        @Test
        void fillsTheEveningWithWhatTheBandCanRing() {
            List<Item> plan = planPractice(setting(register()));
            assertThat(plan).isNotEmpty();
            assertThat(plan.get(0).title()).isEqualTo(ROUNDS);
            assertThat(plan.get(0).purpose()).isEqualTo(Purpose.REPERTOIRE);
        }

        @Test
        void putsEverybodyOnABell() {
            List<Item> plan = planPractice(setting(register()));
            assertThat(plan.get(0).assignment()).hasSize(6);
            assertThat(new HashSet<>(plan.get(0).band())).hasSize(6);
        }

        @Test
        void namesTheConductor() {
            assertThat(planPractice(setting(register())).get(0).conductor()).hasValue("alice");
        }

        @Test
        void leavesOutAMethodTheBandCannotFillTheBellsFor() {
            assertThat(planPractice(setting(register())))
                    .extracting(Item::title)
                    .doesNotContain(HARD);
        }

        @Test
        void includesAStretchWhenEnoughOfTheBandAreLearningIt() {
            CompetencyRegister competency = register();
            for (String name : NAMES) {
                competency.record(name, HARD, Level.LEARNING);
            }
            Item stretch = planPractice(setting(competency)).stream()
                    .filter(item -> item.purpose() == Purpose.STRETCH)
                    .findFirst()
                    .orElseThrow();
            assertThat(stretch.title()).isEqualTo(HARD);
        }

        @Test
        void ringsOnlyAsManyStretchesAsItWasToldTo() {
            CompetencyRegister competency = register();
            for (String name : NAMES) {
                competency.record(name, HARD, Level.LEARNING);
                competency.record(name, "London Surprise Minor", Level.LEARNING);
            }
            List<Item> plan = planPractice(setting(competency).withStretches(1));
            assertThat(plan)
                    .filteredOn(item -> item.purpose() == Purpose.STRETCH)
                    .hasSize(1);
        }

        @Test
        void stopsAtTheNumberOfSlots() {
            CompetencyRegister competency = register();
            for (String name : NAMES) {
                competency.record(name, HARD, Level.CONFIDENT);
            }
            assertThat(planPractice(setting(competency).withSlots(1))).hasSize(1);
        }

        @Test
        void refusesAPracticeWithNoSlots() {
            assertThatThrownBy(() -> planPractice(setting(register()).withSlots(0)))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class DescribeItem {

        @Test
        void readsThePlanOut() {
            String line = describeItem(planPractice(setting(register())).get(0));
            assertThat(line).contains("Plain Bob Minor (repertoire)");
            assertThat(line).contains("conducted by alice");
        }

        @Test
        void saysWhenNobodyIsConducting() {
            List<Ringer> quiet = NAMES.stream()
                    .map(name -> ringer(name, Details.NONE.withId(name)))
                    .toList();
            List<Item> plan = planPractice(Setting.of(tower(), quiet, register()));
            assertThat(describeItem(plan.get(0))).contains("nobody conducting");
        }
    }
}
