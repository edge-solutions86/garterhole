package io.github.rkaushikdev.garterhole.composition;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.composition.TouchExpander.Rung;
import io.github.rkaushikdev.garterhole.composition.TruthTracker.Repeat;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.core.RowBlock;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.List;
import java.util.Optional;

/**
 * Proving a touch. A touch is true when no row is rung twice, and the only way
 * to be sure of that is to look at every row.
 *
 * <p>The row a touch finishes on is the row it started on, and that repeat does
 * not count. Everything else does, including a repeat that a composer meant to
 * be there, because a touch with a deliberate repeat is still false.
 */
public final class Prover {

    private Prover() {}

    /**
     * What proving a block found.
     *
     * @param isTrue true when no row is rung twice
     * @param rows how many rows were looked at, the closing rounds left out
     * @param distinct how many different rows there are
     * @param repeats every row rung more than once, in the order they started
     * @param comesRound true when the block finishes where it started
     */
    public record Proof(boolean isTrue, int rows, int distinct, List<Repeat> repeats, boolean comesRound) {

        /**
         * @return the proof as a document, with the field names a report expects
         */
        public ObjectNode toJson() {
            ObjectNode json = Json.object();
            json.put("isTrue", isTrue);
            json.put("rows", rows);
            json.put("distinct", distinct);
            ArrayNode repeated = json.putArray("repeats");
            for (Repeat repeat : repeats) {
                ObjectNode entry = repeated.addObject().put("row", repeat.row());
                ArrayNode at = entry.putArray("at");
                repeat.at().forEach(at::add);
            }
            json.put("comesRound", comesRound);
            return json;
        }
    }

    /**
     * Prove a block of rows.
     *
     * @param block the rows
     * @return the proof
     * @throws ValidationException when there is no block
     */
    public static Proof prove(RowBlock block) {
        if (block == null) {
            throw new ValidationException("a RowBlock is needed to prove it");
        }
        boolean comesRound = block.comesRound();
        TruthTracker tracker = new TruthTracker();
        int upTo = comesRound && block.length() > 1 ? block.length() - 1 : block.length();
        List<Row> rows = block.rows();
        for (int index = 0; index < upTo; index += 1) {
            tracker.add(rows.get(index));
        }
        return new Proof(tracker.isTrue(), tracker.count(), tracker.distinct(), tracker.repeats(), comesRound);
    }

    /**
     * Prove a rung touch.
     *
     * @param rung the touch, rung
     * @return the proof
     * @throws ValidationException when there is no rung touch
     */
    public static Proof proveTouch(Rung rung) {
        if (rung == null || rung.block() == null) {
            throw new ValidationException("a rung touch is needed to prove it");
        }
        return prove(rung.block());
    }

    /**
     * How many rows there are at a stage, which is what an extent has to contain.
     *
     * @param stage a number of bells
     * @return the factorial of the stage
     */
    public static long extentSize(int stage) {
        long total = 1;
        for (int bell = 2; bell <= stage; bell += 1) {
            total *= bell;
        }
        return total;
    }

    /**
     * True when the block contains every row at its stage exactly once. On an
     * odd stage the cover bell never moves, so the extent is over the changing
     * bells.
     *
     * @param block the rows
     * @return true when the block is an extent
     */
    public static boolean isExtent(RowBlock block) {
        Proof proof = prove(block);
        if (!proof.isTrue()) {
            return false;
        }
        int changing = block.stage() % 2 == 1 ? block.stage() - 1 : block.stage();
        return proof.distinct() == extentSize(changing);
    }

    /**
     * @param proof a proof
     * @return the first repeat in a false touch, which is what a composer wants
     *     to see before the whole list
     */
    public static Optional<Repeat> firstRepeat(Proof proof) {
        return proof.repeats().isEmpty()
                ? Optional.empty()
                : Optional.of(proof.repeats().get(0));
    }

    /**
     * @param proof a proof
     * @return a one line summary, for example {@code true, 336 rows}
     */
    public static String describeProof(Proof proof) {
        if (proof.isTrue()) {
            String ending = proof.comesRound() ? "" : ", but it does not come round";
            return "true, " + proof.rows() + " rows" + ending;
        }
        Repeat first = proof.repeats().get(0);
        return "false: " + first.row() + " is rung " + first.at().size() + " times, first at row "
                + (first.at().get(0) + 1);
    }
}
