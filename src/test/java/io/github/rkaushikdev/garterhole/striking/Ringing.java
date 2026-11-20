package io.github.rkaushikdev.garterhole.striking;

import io.github.rkaushikdev.garterhole.core.Change;
import io.github.rkaushikdev.garterhole.core.Cycles;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.core.RowBlock;
import io.github.rkaushikdev.garterhole.notation.Expansion;
import java.util.ArrayList;
import java.util.List;

/**
 * Plain Bob Minor rung out, and struck to a timing model, for the striking tests.
 *
 * <p>The originals rang this through the method package; the blocks here are the
 * same rows, built from the notation with the core row algebra.
 */
final class Ringing {

    /** The stage every fixture is rung at. */
    static final int STAGE = 6;

    private static final List<Change> PLAIN_BOB_MINOR = Expansion.expandNotation("x1x1x1,2", STAGE);

    private Ringing() {}

    /** How far a bell is moved from where it should have struck. */
    @FunctionalInterface
    interface Shift {
        /** Nobody moved. */
        Shift NONE = (bell, rowIndex) -> 0;

        /**
         * @param bell the bell
         * @param rowIndex the row, from zero
         * @return milliseconds late, negative for early
         */
        double of(int bell, int rowIndex);
    }

    /**
     * @return one plain lead of Plain Bob Minor from rounds: thirteen rows
     */
    static RowBlock plainLead() {
        return RowBlock.fromChanges(Row.rounds(STAGE), PLAIN_BOB_MINOR);
    }

    /**
     * @return the plain course of Plain Bob Minor: five leads, sixty one rows
     */
    static RowBlock plainCourse() {
        RowBlock block = plainLead();
        int leads = Cycles.orderOf(block.last());
        for (int lead = 1; lead < leads; lead += 1) {
            block = block.concat(RowBlock.fromChanges(block.last(), PLAIN_BOB_MINOR));
        }
        return block;
    }

    /**
     * @param block the rows
     * @param model when each blow should fall
     * @param shift how far each blow is moved
     * @return a strike per blow, in the order the rows give
     */
    static List<Strike> strikesTo(RowBlock block, TimingModel model, Shift shift) {
        List<Strike> strikes = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < block.length(); rowIndex += 1) {
            Row row = block.at(rowIndex);
            for (int place = 1; place <= row.stage(); place += 1) {
                int bell = row.bellAt(place);
                strikes.add(new Strike(bell, model.expectedAt(rowIndex, place) + shift.of(bell, rowIndex)));
            }
        }
        return strikes;
    }

    /**
     * @param shift how far each blow is moved
     * @return the plain course struck at 250ms a blow with the usual gap
     */
    static List<Strike> courseStrikes(Shift shift) {
        return strikesTo(plainCourse(), TimingModel.of(STAGE, 250), shift);
    }

    /**
     * @return the plain course struck perfectly at 250ms a blow
     */
    static List<Strike> courseStrikes() {
        return courseStrikes(Shift.NONE);
    }

    /**
     * @param block the rows
     * @param gap milliseconds between every blow, with no handstroke gap
     * @return a perfectly even stream
     */
    static StrikeStream struck(RowBlock block, double gap) {
        List<Strike> strikes = new ArrayList<>();
        double at = 0;
        for (Row row : block) {
            for (int bell : row.bells()) {
                strikes.add(new Strike(bell, at));
                at += gap;
            }
        }
        return new StrikeStream(block.stage(), strikes);
    }
}
