package io.github.rkaushikdev.garterhole.composition;

import static io.github.rkaushikdev.garterhole.composition.PartStructure.cyclicPartHeads;
import static io.github.rkaushikdev.garterhole.composition.PartStructure.describePartEnd;
import static io.github.rkaushikdev.garterhole.composition.PartStructure.detectParts;
import static io.github.rkaushikdev.garterhole.composition.PartStructure.findPartEnd;
import static io.github.rkaushikdev.garterhole.composition.PartStructure.inPart;
import static io.github.rkaushikdev.garterhole.composition.PartStructure.isPartEnd;
import static io.github.rkaushikdev.garterhole.composition.PartStructure.observationBells;
import static io.github.rkaushikdev.garterhole.composition.PartStructure.partCount;
import static io.github.rkaushikdev.garterhole.composition.PartStructure.partHeads;
import static io.github.rkaushikdev.garterhole.core.RowParse.parseRow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.composition.PartStructure.PartEnd;
import io.github.rkaushikdev.garterhole.composition.PartStructure.PartEndDescription;
import io.github.rkaushikdev.garterhole.composition.PartStructure.Parts;
import io.github.rkaushikdev.garterhole.composition.TouchExpander.RungLead;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.method.Method;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PartStructureTest {

    private static Method plainBobMajor() {
        return Method.of("Plain", 8, "x18x18x18x18,12");
    }

    private static List<Row> leadHeadsOf(String touch) {
        return TouchExpander.expandTouch(plainBobMajor(), TouchParser.parseTouch(touch)).leads().stream()
                .map(RungLead::leadHead)
                .toList();
    }

    @Nested
    class PartHeads {

        @Test
        void startsAtRounds() {
            assertThat(partHeads(parseRow("13527486")).get(0).isRounds()).isTrue();
        }

        @Test
        void hasOneHeadPerPart() {
            Row partEnd = parseRow("13527486");
            assertThat(partHeads(partEnd)).hasSize(partCount(partEnd));
        }

        @Test
        void givesJustRoundsForAPartEndThatIsRounds() {
            assertThat(partHeads(Row.rounds(8))).hasSize(1);
        }
    }

    @Nested
    class PartCount {

        @Test
        void countsASevenPartComposition() {
            assertThat(partCount(parseRow("13527486"))).isEqualTo(7);
        }

        @Test
        void countsAThreePartComposition() {
            assertThat(partCount(parseRow("14235678"))).isEqualTo(3);
        }
    }

    @Nested
    class ObservationBells {

        @Test
        void findsTheBellsThePartEndLeavesAlone() {
            assertThat(observationBells(parseRow("14235678"))).containsExactly(1, 5, 6, 7, 8);
        }

        @Test
        void findsOnlyTheTrebleInAPlainBobPartEnd() {
            assertThat(observationBells(parseRow("13527486"))).containsExactly(1);
        }
    }

    @Nested
    class DescribePartEnd {

        @Test
        void reportsThePartsTheShapeAndTheFixedBells() {
            PartEndDescription described = describePartEnd(parseRow("13527486"));
            assertThat(described.parts()).isEqualTo(7);
            assertThat(described.shape()).isEqualTo("7.1");
            assertThat(described.fixed()).containsExactly(1);
            assertThat(described.heads().get(0)).isEqualTo("12345678");
        }
    }

    @Nested
    class FindPartEnd {

        @Test
        void findsAThreePartStructure() {
            PartEnd found = findPartEnd(leadHeadsOf("3(H)"), 3).orElseThrow();
            assertThat(found.leadsPerPart()).isEqualTo(7);
            assertThat(found.partEnd()).hasToString("14235678");
        }

        @Test
        void givesNothingWhenTheLeadsDoNotDivideEvenly() {
            assertThat(findPartEnd(leadHeadsOf("3(H)"), 2)).isEmpty();
        }

        @Test
        void givesNothingForNoLeads() {
            assertThat(findPartEnd(List.of(), 3)).isEmpty();
        }

        @Test
        void refusesAPartCountBelowTwo() {
            assertThatThrownBy(() -> findPartEnd(List.of(), 1)).hasMessageContaining("cannot be in 1 parts");
            assertThatThrownBy(() -> findPartEnd(List.of(), 0)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class DetectParts {

        @Test
        void findsTheStructureOfAThreePartTouch() {
            Parts found = detectParts(leadHeadsOf("3(H)")).orElseThrow();
            assertThat(found.parts()).isEqualTo(3);
            assertThat(found.leadsPerPart()).isEqualTo(7);
        }

        @Test
        void readsAPlainCourseAsOneLeadRungOverAndOver() {
            Parts found = detectParts(leadHeadsOf(".......")).orElseThrow();
            assertThat(found.parts()).isEqualTo(7);
            assertThat(found.leadsPerPart()).isEqualTo(1);
        }

        @Test
        void givesNothingWhenTheCallingHasNoRepeatingStructure() {
            assertThat(detectParts(leadHeadsOf("-..."))).isEmpty();
        }

        @Test
        void givesNothingForNothing() {
            assertThat(detectParts(List.of())).isEmpty();
        }
    }

    @Nested
    class IsPartEnd {

        @Test
        void rejectsRounds() {
            assertThat(isPartEnd(Row.rounds(8))).isFalse();
        }

        @Test
        void acceptsARowThatComesBackToRoundsInSeveralParts() {
            assertThat(isPartEnd(parseRow("14235678"))).isTrue();
        }
    }

    @Nested
    class InPart {

        @Test
        void takesARowIntoALaterPart() {
            assertThat(inPart(parseRow("14235678"), parseRow("12345678"))).hasToString("14235678");
        }

        @Test
        void leavesARowAloneInTheFirstPart() {
            assertThat(inPart(Row.rounds(8), parseRow("13527486"))).hasToString("13527486");
        }
    }

    @Nested
    class CyclicPartHeads {

        @Test
        void rotatesTheBackBells() {
            assertThat(cyclicPartHeads(8, 3))
                    .extracting(Row::toString)
                    .containsExactly("12345678", "12345786", "12345867");
        }

        @Test
        void hasOneHeadPerPart() {
            assertThat(cyclicPartHeads(8, 5)).hasSize(5);
        }

        @Test
        void refusesAPartCountThatDoesNotFitTheStage() {
            assertThatThrownBy(() -> cyclicPartHeads(8, 9)).hasMessageContaining("no cyclic part end for 9 parts on 8");
            assertThatThrownBy(() -> cyclicPartHeads(8, 1)).isInstanceOf(ValidationException.class);
        }
    }
}
