package io.github.rkaushikdev.garterhole.tower;

import static io.github.rkaushikdev.garterhole.tower.Interval.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DiaryTest {

    private Diary diary;

    @BeforeEach
    void fill() {
        diary = new Diary();
        diary.free("alice", of(0, 100))
                .free("bob", of(0, 50))
                .free("bob", of(60, 100))
                .free("carol", of(40, 100));
    }

    @Nested
    class WritingTheDiary {

        @Test
        void recordsAStretchOfFreeTime() {
            assertThat(diary.availabilityOf("alice").total()).isEqualTo(100);
        }

        @Test
        void mergesTwoStretchesThatTouch() {
            diary.free("alice", of(100, 150));
            assertThat(diary.availabilityOf("alice").size()).isEqualTo(1);
            assertThat(diary.availabilityOf("alice").total()).isEqualTo(150);
        }

        @Test
        void keepsTwoStretchesThatDoNotTouchApart() {
            assertThat(diary.availabilityOf("bob").size()).isEqualTo(2);
        }

        @Test
        void givesAnEmptySetForARingerWhoHasSaidNothing() {
            assertThat(diary.availabilityOf("nobody").size()).isZero();
        }

        @Test
        void refusesAnEntryItCannotUse() {
            assertThatThrownBy(() -> diary.free("", of(0, 10))).hasMessageContaining("a ringer id");
            assertThatThrownBy(() -> diary.free("alice", null)).hasMessageContaining("needs an Interval");
        }

        @Test
        void listsTheRingersItKnowsAbout() {
            assertThat(diary.ringers()).containsExactly("alice", "bob", "carol");
        }
    }

    @Nested
    class IsFree {

        @Test
        void needsTheWholeSlotToBeCovered() {
            assertThat(diary.isFree("alice", of(10, 20))).isTrue();
            assertThat(diary.isFree("bob", of(45, 65))).isFalse();
        }

        @Test
        void saysARingerWhoHasWrittenNothingDownIsNotFree() {
            assertThat(diary.isFree("nobody", of(0, 1))).isFalse();
        }
    }

    @Nested
    class WhoIsFree {

        @Test
        void listsTheBandWhoCouldComeInOrder() {
            assertThat(diary.whoIsFree(of(70, 80))).containsExactly("alice", "bob", "carol");
        }

        @Test
        void leavesOutAnybodyWithAClash() {
            assertThat(diary.whoIsFree(of(10, 20))).containsExactly("alice", "bob");
            assertThat(diary.whoIsFree(of(50, 60))).containsExactly("alice", "carol");
        }

        @Test
        void countsThem() {
            assertThat(diary.countFree(of(70, 80))).isEqualTo(3);
            assertThat(diary.countFree(of(200, 210))).isZero();
        }
    }

    @Nested
    class CommonTime {

        @Test
        void findsWhenEverybodyNamedIsFreeAtOnce() {
            assertThat(diary.commonTime(List.of("alice", "carol"))).hasToString("[40, 100)");
        }

        @Test
        void breaksTheAnswerUpWhenSomebodyHasAGap() {
            assertThat(diary.commonTime(List.of("alice", "bob"))).hasToString("[0, 50) [60, 100)");
        }

        @Test
        void givesOneRingerTheirOwnDiaryBack() {
            assertThat(diary.commonTime(List.of("bob")).total()).isEqualTo(90);
        }

        @Test
        void givesNothingWhenSomebodyNamedHasSaidNothing() {
            assertThat(diary.commonTime(List.of("alice", "nobody")).size()).isZero();
        }

        @Test
        void refusesToAnswerForNobody() {
            assertThatThrownBy(() -> diary.commonTime(List.of())).hasMessageContaining("naming nobody");
            assertThatThrownBy(() -> diary.commonTime(null)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class BestSlot {

        private List<Interval> candidates() {
            return List.of(of(10, 20), of(50, 60), of(70, 80));
        }

        @Test
        void picksTheSlotTheMostOfTheBandCanMake() {
            Diary.Choice best = diary.bestSlot(candidates()).orElseThrow();
            assertThat(best.slot().start()).isEqualTo(70);
            assertThat(best.band()).containsExactly("alice", "bob", "carol");
        }

        @Test
        void breaksATieInFavourOfTheEarlierSlot() {
            assertThat(diary.bestSlot(List.of(of(10, 20), of(50, 60)))
                            .orElseThrow()
                            .slot()
                            .start())
                    .isEqualTo(10);
        }

        @Test
        void ignoresASlotWithTooSmallABand() {
            assertThat(diary.bestSlot(candidates(), 3).orElseThrow().slot().start())
                    .isEqualTo(70);
        }

        @Test
        void givesNothingWhenNothingIsGoodEnough() {
            assertThat(diary.bestSlot(candidates(), 4)).isEmpty();
            assertThat(diary.bestSlot(List.of())).isEmpty();
        }

        @Test
        void refusesSomethingThatIsNotAListOfSlots() {
            assertThatThrownBy(() -> diary.bestSlot(null)).hasMessageContaining("list of candidate slots");
        }
    }
}
