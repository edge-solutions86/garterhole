package io.github.rkaushikdev.garterhole.composition;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.core.Cycles;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.core.Transposition;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Part structure. Most long compositions are one short calling rung several
 * times over, each time starting from a different row. That row is the part
 * head, and the whole composition is the part repeated until the part head
 * comes back to rounds.
 *
 * <p>Knowing the structure is what makes a peal callable. A conductor who knows
 * a composition is five parts has five things to remember rather than a
 * hundred and twenty.
 */
public final class PartStructure {

    private PartStructure() {}

    /**
     * A part end described for a report.
     *
     * @param parts how many parts it gives
     * @param shape its cycle lengths, longest first, joined with full stops
     * @param fixed the bells it leaves alone
     * @param heads every part head, as printed, rounds first
     */
    public record PartEndDescription(int parts, String shape, List<Integer> fixed, List<String> heads) {}

    /**
     * A part end found in a run of lead heads.
     *
     * @param partEnd the lead head that ends the first part
     * @param leadsPerPart how many leads each part has
     */
    public record PartEnd(Row partEnd, int leadsPerPart) {}

    /**
     * The part structure of a run of lead heads.
     *
     * @param parts how many parts there are
     * @param partEnd the lead head that ends the first part
     * @param leadsPerPart how many leads each part has
     */
    public record Parts(int parts, Row partEnd, int leadsPerPart) {}

    /**
     * @param partEnd the part end
     * @return every part head of a composition with that part end, starting at rounds
     */
    public static List<Row> partHeads(Row partEnd) {
        return Transposition.cyclicGroup(partEnd);
    }

    /**
     * @param partEnd the part end
     * @return how many parts a composition with this part end has
     */
    public static int partCount(Row partEnd) {
        return Transposition.order(partEnd);
    }

    /**
     * The bells a part end leaves alone. A conductor calls from one of these,
     * because a bell that moves between the parts is a bell whose work changes.
     *
     * @param partEnd the part end
     * @return the fixed bells, ascending
     */
    public static List<Integer> observationBells(Row partEnd) {
        return Cycles.fixedBells(partEnd);
    }

    /**
     * @param partEnd the part end
     * @return the parts, the shape, the fixed bells and the part heads
     */
    public static PartEndDescription describePartEnd(Row partEnd) {
        return new PartEndDescription(
                partCount(partEnd),
                Cycles.cycleLengths(partEnd).stream().map(String::valueOf).collect(Collectors.joining(".")),
                observationBells(partEnd),
                partHeads(partEnd).stream().map(Row::toString).toList());
    }

    /**
     * Break a list of lead heads into parts of equal length, and say what the
     * part end is.
     *
     * @param leadHeads the lead head after every lead
     * @param parts how many parts to look for, two or more
     * @return the part end, or nothing when the lead heads do not divide into
     *     that many parts, which is the usual answer for a composition that was
     *     not written in parts
     * @throws ValidationException when asked for fewer than two parts
     */
    public static Optional<PartEnd> findPartEnd(List<Row> leadHeads, int parts) {
        if (parts < 2) {
            throw new ValidationException("a composition cannot be in " + parts + " parts");
        }
        if (leadHeads.isEmpty() || leadHeads.size() % parts != 0) {
            return Optional.empty();
        }
        int leadsPerPart = leadHeads.size() / parts;
        Row partEnd = leadHeads.get(leadsPerPart - 1);
        if (Transposition.order(partEnd) != parts) {
            return Optional.empty();
        }
        for (int part = 1; part < parts; part += 1) {
            Row expected =
                    Transposition.transpose(partEnd, leadHeads.get((part - 1) * leadsPerPart + leadsPerPart - 1));
            Row found = leadHeads.get(part * leadsPerPart + leadsPerPart - 1);
            if (!found.equals(expected)) {
                return Optional.empty();
            }
        }
        return Optional.of(new PartEnd(partEnd, leadsPerPart));
    }

    /**
     * Look for any part structure a run of lead heads has, trying the part
     * counts that divide the number of leads. The largest number of parts is
     * reported, because that is the one a conductor would use.
     *
     * @param leadHeads the lead head after every lead
     * @return the structure, when there is one
     */
    public static Optional<Parts> detectParts(List<Row> leadHeads) {
        for (int parts = leadHeads.size(); parts >= 2; parts -= 1) {
            if (leadHeads.size() % parts != 0) {
                continue;
            }
            Optional<PartEnd> found = findPartEnd(leadHeads, parts);
            if (found.isPresent()) {
                return Optional.of(
                        new Parts(parts, found.get().partEnd(), found.get().leadsPerPart()));
            }
        }
        return Optional.empty();
    }

    /**
     * @param row a row
     * @return true when repeating the row brings a composition back to rounds in more than one part
     */
    public static boolean isPartEnd(Row row) {
        return !row.isRounds() && Transposition.order(row) > 1;
    }

    /**
     * The row a part head takes another row to, which is how the second part of
     * a composition is worked out from the first.
     *
     * @param partHead the part head
     * @param row a row of the first part
     * @return the same row in the part the head starts
     */
    public static Row inPart(Row partHead, Row row) {
        return Transposition.transpose(partHead, row);
    }

    /**
     * The part heads for a composition of a given number of parts at a stage,
     * when the part end is a plain cyclic shift of the bells at the back. Used
     * as a starting point when a composer asks for a cyclic composition.
     *
     * @param stage the stage
     * @param parts how many parts, from two up to the stage
     * @return the part heads, rounds first
     * @throws ValidationException when the part count does not fit the stage
     */
    public static List<Row> cyclicPartHeads(int stage, int parts) {
        if (parts < 2 || parts > stage) {
            throw new ValidationException("there is no cyclic part end for " + parts + " parts on " + stage);
        }
        int[] partEnd = Row.rounds(stage).toArray();
        int first = partEnd[stage - parts];
        for (int index = stage - parts; index < stage - 1; index += 1) {
            partEnd[index] = partEnd[index + 1];
        }
        partEnd[stage - 1] = first;
        return partHeads(new Row(partEnd));
    }
}
