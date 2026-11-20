package io.github.rkaushikdev.garterhole.tower;

import static io.github.rkaushikdev.garterhole.tower.Ringer.Role.CONDUCTOR;
import static io.github.rkaushikdev.garterhole.tower.Ringer.Role.STEEPLE_KEEPER;
import static io.github.rkaushikdev.garterhole.tower.Ringer.Role.TEACHER;
import static io.github.rkaushikdev.garterhole.tower.Ringer.Role.TOWER_CAPTAIN;
import static io.github.rkaushikdev.garterhole.tower.Ringer.ringer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.tower.Ringer.Details;
import io.github.rkaushikdev.garterhole.tower.Ringer.Role;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RingerTest {

    private static Bell light() {
        return Bell.weighing(1, "4-0-0");
    }

    private static Bell heavy() {
        return Bell.weighing(8, "23-2-11");
    }

    private static Details roles(Role... roles) {
        return Details.NONE.withRoles(roles);
    }

    private static Details limit(double pounds) {
        return Details.NONE.withHeaviestBellPounds(pounds);
    }

    @Nested
    class RingerItself {

        @Test
        void keepsTheNameAndMakesAnIdFromIt() {
            Ringer member = ringer("Alice Vane");
            assertThat(member.name()).isEqualTo("Alice Vane");
            assertThat(member.id()).isEqualTo("alice-vane");
        }

        @Test
        void takesAnIdOfItsOwn() {
            assertThat(ringer("Alice Vane", Details.NONE.withId("av1")).id()).isEqualTo("av1");
        }

        @Test
        void isFrozen() {
            assertThat(Modifier.isFinal(Ringer.class.getModifiers())).isTrue();
            assertThatThrownBy(
                            () -> ringer("Alice Vane", roles(TEACHER)).roles().add(CONDUCTOR))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void squashesWhitespaceInTheName() {
            assertThat(ringer("  Alice   Vane ").name()).isEqualTo("Alice Vane");
        }

        @Test
        void sortsAndDeDuplicatesTheRoles() {
            Ringer member = ringer("Alice Vane", roles(TEACHER, CONDUCTOR, TEACHER));
            assertThat(member.roles()).containsExactly(CONDUCTOR, TEACHER);
        }

        @Test
        void hasNoRolesAndNoWeightLimitByDefault() {
            Ringer member = ringer("Alice Vane");
            assertThat(member.roles()).isEmpty();
            assertThat(member.heaviestBellPounds()).isEmpty();
        }

        @Test
        void refusesADefinitionItCannotUse() {
            assertThatThrownBy(() -> new Ringer("Alice", null)).hasMessageContaining("needs a definition object");
            assertThatThrownBy(() -> ringer("")).hasMessageContaining("a ringer name");
        }

        @Test
        void refusesAWeightThatIsNotAWeight() {
            assertThatThrownBy(() -> ringer("Alice", limit(0)))
                    .hasMessageContaining("is not a bell weight a ringer can handle");
        }

        @Test
        void refusesARoleNobodyHoldsInATower() {
            assertThatThrownBy(() -> ringer("Alice", roles(Role.of("organist"))))
                    .hasMessageContaining("is not a role in a tower");
            assertThatThrownBy(() -> ringer("Alice", Details.NONE.withRoles(Arrays.asList((Role) null))))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class Roles {

        @Test
        void saysWhetherARingerHoldsOne() {
            Ringer member = ringer("Alice", roles(CONDUCTOR));
            assertThat(member.hasRole(CONDUCTOR)).isTrue();
            assertThat(member.hasRole(TEACHER)).isFalse();
        }

        @Test
        void hasAShortcutForConducting() {
            assertThat(ringer("Alice", roles(CONDUCTOR)).conducts()).isTrue();
            assertThat(ringer("Bob").conducts()).isFalse();
        }

        @Test
        void listsTheRolesATowerHas() {
            assertThat(Ringer.ROLES).contains(TOWER_CAPTAIN);
            assertThat(TOWER_CAPTAIN.label()).isEqualTo("tower captain");
            assertThatThrownBy(() -> Ringer.ROLES.add(STEEPLE_KEEPER))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class HandlingABell {

        @Test
        void letsARingerWithNoRecordedLimitTakeAnything() {
            assertThat(ringer("Alice").canHandle(heavy())).isTrue();
        }

        @Test
        void keepsARingerOffABellThatIsTooHeavy() {
            Ringer member = ringer("Bob", limit(900));
            assertThat(member.canHandle(light())).isTrue();
            assertThat(member.canHandle(heavy())).isFalse();
        }

        @Test
        void allowsABellExactlyAtTheLimit() {
            assertThat(ringer("Bob", limit(448)).canHandle(light())).isTrue();
        }
    }

    @Nested
    class SearchingABand {

        private List<Ringer> band() {
            return List.of(
                    ringer("Alice", roles(CONDUCTOR)),
                    ringer("Bob", limit(500)),
                    ringer("Carol", roles(CONDUCTOR, TEACHER)));
        }

        @Test
        void findsTheRingersWhoHoldARole() {
            assertThat(Ringer.withRole(band(), CONDUCTOR))
                    .extracting(Ringer::name)
                    .containsExactly("Alice", "Carol");
        }

        @Test
        void findsNobodyWhenNobodyHoldsIt() {
            assertThat(Ringer.withRole(band(), STEEPLE_KEEPER)).isEmpty();
        }

        @Test
        void refusesARoleThatIsNotOne() {
            assertThatThrownBy(() -> Ringer.withRole(band(), Role.of("organist")))
                    .hasMessageContaining("is not a role in a tower");
        }

        @Test
        void findsTheRingersWhoCouldTakeABell() {
            assertThat(Ringer.whoCanHandle(band(), heavy()))
                    .extracting(Ringer::name)
                    .containsExactly("Alice", "Carol");
            assertThat(Ringer.whoCanHandle(band(), light())).hasSize(3);
        }
    }

    @Nested
    class PrintingARinger {

        @Test
        void givesTheNameAloneWhenThereAreNoRoles() {
            assertThat(ringer("Alice Vane")).hasToString("Alice Vane");
        }

        @Test
        void listsTheRolesAfterTheName() {
            assertThat(ringer("Alice Vane", roles(CONDUCTOR, TEACHER))).hasToString("Alice Vane (conductor, teacher)");
        }
    }
}
