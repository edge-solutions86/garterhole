package io.github.rkaushikdev.garterhole.core;

import static io.github.rkaushikdev.garterhole.core.RowBlock.changeBetween;
import static io.github.rkaushikdev.garterhole.core.RowParse.parseRow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.notation.Expansion;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RowBlockTest {

    private static final List<Change> PLAIN_BOB_MINOR = Expansion.expandNotation("x1x1x1,2", 6);

    @Nested
    class RowBlockConstruction {

        @Test
        void ringsALeadFromAStartingRow() {
            RowBlock block = RowBlock.fromChanges(Row.rounds(6), PLAIN_BOB_MINOR);
            assertThat(block.length()).isEqualTo(13);
            assertThat(block.changeCount()).isEqualTo(12);
            assertThat(block.first()).hasToString("123456");
            assertThat(block.last()).hasToString("135264");
        }

        @Test
        void isFrozen() {
            RowBlock block = RowBlock.fromChanges(Row.rounds(6), PLAIN_BOB_MINOR);
            assertThat(Modifier.isFinal(RowBlock.class.getModifiers())).isTrue();
            assertThatThrownBy(() -> block.rows().add(Row.rounds(6))).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void acceptsASingleRow() {
            RowBlock block = new RowBlock(List.of(Row.rounds(8)));
            assertThat(block.length()).isEqualTo(1);
            assertThat(block.changeCount()).isZero();
        }

        @Test
        void refusesAnEmptyBlock() {
            assertThatThrownBy(() -> new RowBlock(List.of())).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> new RowBlock(List.of())).hasMessage("a block must contain at least one row");
        }

        @Test
        void refusesRowsAtMixedStages() {
            assertThatThrownBy(() -> new RowBlock(List.of(Row.rounds(6), Row.rounds(8))))
                    .hasMessage("row 1 is on 8 bells but the block is on 6");
        }

        @Test
        void refusesAnEntryThatIsNotARow() {
            assertThatThrownBy(() -> new RowBlock(Arrays.asList(Row.rounds(6), null)))
                    .hasMessage("entry 1 of the block is not a row");
        }

        @Test
        void refusesToStartFromSomethingThatIsNotARow() {
            assertThatThrownBy(() -> RowBlock.fromChanges(null, PLAIN_BOB_MINOR))
                    .hasMessage("a block starts from a row");
        }
    }

    @Nested
    class ReadingABlock {

        private final RowBlock block = RowBlock.fromChanges(Row.rounds(6), PLAIN_BOB_MINOR);

        @Test
        void readsARowByIndex() {
            assertThat(block.at(0)).hasToString("123456");
            assertThat(block.at(1)).hasToString("214365");
            assertThat(block.at(12)).hasToString("135264");
        }

        @Test
        void refusesAnIndexOutsideTheBlock() {
            assertThatThrownBy(() -> block.at(13)).hasMessageContaining("outside 0..12");
            assertThatThrownBy(() -> block.at(-1)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void iterates() {
            List<Row> rows = new ArrayList<>();
            block.forEach(rows::add);
            assertThat(rows.get(1)).hasToString("214365");
            assertThat(rows).hasSize(13);
        }

        @Test
        void saysWhetherItHoldsARow() {
            assertThat(block.contains(parseRow("214365"))).isTrue();
            assertThat(block.contains(parseRow("123465"))).isFalse();
        }

        @Test
        void slicesIntoASmallerBlock() {
            RowBlock half = block.slice(0, 7);
            assertThat(half.length()).isEqualTo(7);
            assertThat(half.first()).hasToString("123456");
        }
    }

    @Nested
    class BlockProperties {

        @Test
        void findsAPlainLeadTrue() {
            assertThat(RowBlock.fromChanges(Row.rounds(6), PLAIN_BOB_MINOR).isTrue())
                    .isTrue();
        }

        @Test
        void findsARepeatedRow() {
            RowBlock block = new RowBlock(List.of(Row.rounds(4), parseRow("2143"), Row.rounds(4)));
            assertThat(block.isTrue()).isFalse();
        }

        @Test
        void saysWhetherTheBlockComesRound() {
            RowBlock block = RowBlock.fromChanges(Row.rounds(6), PLAIN_BOB_MINOR);
            assertThat(block.comesRound()).isFalse();
            for (int lead = 1; lead < 5; lead += 1) {
                block = block.concat(RowBlock.fromChanges(block.last(), PLAIN_BOB_MINOR));
            }
            assertThat(block.comesRound()).isTrue();
            assertThat(block.length()).isEqualTo(61);
            assertThat(block.isTrue()).isFalse();
        }

        @Test
        void recoversTheChangesItWasRungFrom() {
            RowBlock block = RowBlock.fromChanges(Row.rounds(6), PLAIN_BOB_MINOR);
            assertThat(block.changes()).isEqualTo(PLAIN_BOB_MINOR);
        }
    }

    @Nested
    class JoiningBlocks {

        @Test
        void joinsOnTheSharedRow() {
            RowBlock first = RowBlock.fromChanges(Row.rounds(6), PLAIN_BOB_MINOR);
            RowBlock second = RowBlock.fromChanges(first.last(), PLAIN_BOB_MINOR);
            RowBlock joined = first.concat(second);
            assertThat(joined.length()).isEqualTo(25);
            assertThat(joined.at(12)).isEqualTo(first.last());
        }

        @Test
        void refusesToJoinBlocksThatDoNotMeet() {
            RowBlock first = RowBlock.fromChanges(Row.rounds(6), PLAIN_BOB_MINOR);
            RowBlock second = RowBlock.fromChanges(Row.rounds(6), PLAIN_BOB_MINOR);
            assertThatThrownBy(() -> first.concat(second))
                    .hasMessageContaining("block ends on 135264 but the next one starts on");
        }

        @Test
        void refusesSomethingThatIsNotABlock() {
            RowBlock first = RowBlock.fromChanges(Row.rounds(6), PLAIN_BOB_MINOR);
            assertThatThrownBy(() -> first.concat(null)).hasMessage("a block is joined to another block");
        }
    }

    @Nested
    class ChangeBetween {

        @Test
        void findsACrossChange() {
            assertThat(changeBetween(Row.rounds(6), parseRow("214365"))).hasToString("x");
        }

        @Test
        void findsThePlacesMade() {
            assertThat(changeBetween(parseRow("214365"), parseRow("241635"))).hasToString("16");
        }

        @Test
        void givesEveryPlaceForTwoIdenticalRows() {
            assertThat(changeBetween(Row.rounds(6), Row.rounds(6))).hasToString("123456");
        }

        @Test
        void refusesTwoRowsThatAreNotOneChangeApart() {
            assertThatThrownBy(() -> changeBetween(Row.rounds(6), parseRow("231456")))
                    .hasMessageContaining("are not one change apart");
        }

        @Test
        void refusesRowsAtDifferentStages() {
            assertThatThrownBy(() -> changeBetween(Row.rounds(6), Row.rounds(8)))
                    .hasMessage("cannot change from 6 bells to 8");
        }
    }
}
