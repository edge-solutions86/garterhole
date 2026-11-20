package io.github.rkaushikdev.garterhole.composition;

import static io.github.rkaushikdev.garterhole.composition.Prover.describeProof;
import static io.github.rkaushikdev.garterhole.composition.Prover.extentSize;
import static io.github.rkaushikdev.garterhole.composition.Prover.firstRepeat;
import static io.github.rkaushikdev.garterhole.composition.Prover.isExtent;
import static io.github.rkaushikdev.garterhole.composition.Prover.prove;
import static io.github.rkaushikdev.garterhole.composition.Prover.proveTouch;
import static io.github.rkaushikdev.garterhole.core.RowParse.parseRow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.composition.Prover.Proof;
import io.github.rkaushikdev.garterhole.composition.TouchExpander.Rung;
import io.github.rkaushikdev.garterhole.composition.TruthTracker.Repeat;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.core.RowBlock;
import io.github.rkaushikdev.garterhole.method.Method;
import io.github.rkaushikdev.garterhole.method.PlainCourse;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProverTest {

    private static Method plainBobMinor() {
        return Method.of("Plain", 6, "x1x1x1,2");
    }

    private static Method plainBobMajor() {
        return Method.of("Plain", 8, "x18x18x18x18,12");
    }

    private static Rung ring(Method method, String text) {
        return TouchExpander.expandTouch(method, TouchParser.parseTouch(text));
    }

    @Nested
    class Prove {

        @Test
        void findsAPlainCourseTrue() {
            Proof proof = prove(PlainCourse.plainCourse(plainBobMinor()));
            assertThat(proof.isTrue()).isTrue();
            assertThat(proof.rows()).isEqualTo(60);
            assertThat(proof.distinct()).isEqualTo(60);
            assertThat(proof.repeats()).isEmpty();
        }

        @Test
        void doesNotCountTheRowATouchComesRoundOn() {
            Proof proof = prove(PlainCourse.plainCourse(plainBobMinor()));
            assertThat(proof.comesRound()).isTrue();
            assertThat(proof.rows()).isEqualTo(60);
        }

        @Test
        void countsEveryRowWhenTheBlockDoesNotComeRound() {
            Proof proof = prove(plainBobMinor().plainLead());
            assertThat(proof.comesRound()).isFalse();
            assertThat(proof.rows()).isEqualTo(13);
        }

        @Test
        void findsARepeatedRowAndSaysWhere() {
            RowBlock block = new RowBlock(List.of(Row.rounds(4), parseRow("2143"), parseRow("2413"), parseRow("2143")));
            Proof proof = prove(block);
            assertThat(proof.isTrue()).isFalse();
            assertThat(proof.repeats()).containsExactly(new Repeat("2143", List.of(1, 3)));
        }

        @Test
        void listsRepeatsInTheOrderTheyFirstAppear() {
            RowBlock block = new RowBlock(
                    List.of(Row.rounds(4), parseRow("2143"), Row.rounds(4), parseRow("2143"), parseRow("2413")));
            assertThat(prove(block).repeats()).extracting(Repeat::row).containsExactly("1234", "2143");
        }

        @Test
        void findsAFalseTouchFalse() {
            Rung rung = ring(plainBobMinor(), "3(-..-..)");
            assertThat(prove(rung.block()).isTrue()).isFalse();
        }

        @Test
        void refusesSomethingThatIsNotABlock() {
            assertThatThrownBy(() -> prove(null)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class ProveTouch {

        @Test
        void provesARungTouch() {
            Proof proof = proveTouch(ring(plainBobMajor(), "3(H)"));
            assertThat(proof.isTrue()).isTrue();
            assertThat(proof.rows()).isEqualTo(336);
        }

        @Test
        void refusesSomethingThatIsNotARungTouch() {
            assertThatThrownBy(() -> proveTouch(new Rung(null, List.of(), false)))
                    .hasMessageContaining("a rung touch is needed");
            assertThatThrownBy(() -> proveTouch(null)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class ExtentSize {

        @Test
        void countsTheRowsAtAStage() {
            assertThat(extentSize(4)).isEqualTo(24);
            assertThat(extentSize(6)).isEqualTo(720);
            assertThat(extentSize(8)).isEqualTo(40320);
        }

        @Test
        void countsOneRowOnOneBell() {
            assertThat(extentSize(1)).isEqualTo(1);
        }
    }

    @Nested
    class IsExtent {

        @Test
        void saysAPlainCourseIsNotAnExtent() {
            assertThat(isExtent(PlainCourse.plainCourse(plainBobMinor()))).isFalse();
        }

        @Test
        void saysAFalseBlockIsNotAnExtent() {
            RowBlock block = new RowBlock(List.of(Row.rounds(4), parseRow("2143"), parseRow("2143")));
            assertThat(isExtent(block)).isFalse();
        }

        @Test
        void measuresAnOddStageOverTheChangingBells() {
            RowBlock doubles = PlainCourse.plainCourse(Method.of("Plain", 5, "5.1.5.1.5,125"));
            assertThat(isExtent(doubles)).isFalse();
        }
    }

    @Nested
    class ReportingAProof {

        @Test
        void namesTheFirstRepeat() {
            RowBlock block = new RowBlock(List.of(Row.rounds(4), parseRow("2143"), parseRow("2143")));
            Proof proof = prove(block);
            assertThat(firstRepeat(proof).orElseThrow().row()).isEqualTo("2143");
            assertThat(describeProof(proof)).isEqualTo("false: 2143 is rung 2 times, first at row 2");
        }

        @Test
        void givesNothingWhenThereIsNothingToReport() {
            assertThat(firstRepeat(prove(PlainCourse.plainCourse(plainBobMinor()))))
                    .isEmpty();
        }

        @Test
        void saysATrueTouchIsTrue() {
            assertThat(describeProof(prove(PlainCourse.plainCourse(plainBobMinor()))))
                    .isEqualTo("true, 60 rows");
        }

        @Test
        void mentionsATrueBlockThatDoesNotComeRound() {
            assertThat(describeProof(prove(plainBobMinor().plainLead())))
                    .isEqualTo("true, 13 rows, but it does not come round");
        }
    }

    @Nested
    class TheTruthTracker {

        @Test
        void acceptsARowItHasNotSeen() {
            TruthTracker tracker = new TruthTracker();
            assertThat(tracker.add(Row.rounds(4))).isTrue();
            assertThat(tracker.add(parseRow("2143"))).isTrue();
            assertThat(tracker.isTrue()).isTrue();
        }

        @Test
        void rejectsARowItHasSeenBefore() {
            TruthTracker tracker = new TruthTracker();
            tracker.add(Row.rounds(4));
            assertThat(tracker.add(Row.rounds(4))).isFalse();
            assertThat(tracker.isTrue()).isFalse();
        }

        @Test
        void countsEveryRowOfferedAndEveryDistinctOne() {
            TruthTracker tracker = new TruthTracker();
            tracker.add(Row.rounds(4));
            tracker.add(Row.rounds(4));
            tracker.add(parseRow("2143"));
            assertThat(tracker.count()).isEqualTo(3);
            assertThat(tracker.distinct()).isEqualTo(2);
        }

        @Test
        void saysWhereEachRepeatWas() {
            TruthTracker tracker = new TruthTracker();
            tracker.add(Row.rounds(4));
            tracker.add(parseRow("2143"));
            tracker.add(Row.rounds(4));
            assertThat(tracker.repeats()).containsExactly(new Repeat("1234", List.of(0, 2)));
        }

        @Test
        void startsEmptyAndTrue() {
            TruthTracker tracker = new TruthTracker();
            assertThat(tracker.isTrue()).isTrue();
            assertThat(tracker.distinct()).isZero();
            assertThat(tracker.repeats()).isEmpty();
        }
    }
}
