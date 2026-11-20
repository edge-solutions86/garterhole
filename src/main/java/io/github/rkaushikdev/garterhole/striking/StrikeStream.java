package io.github.rkaushikdev.garterhole.striking;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.core.Stage;
import io.github.rkaushikdev.garterhole.util.CollectionHelpers;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A strike stream is what comes out of a recorder in the tower: one entry per
 * blow, saying which bell struck and when, in milliseconds from the start of
 * the recording.
 *
 * <p>Everything downstream assumes the stream is sorted and that every bell of
 * the stage appears. Both are checked here rather than being taken on trust,
 * because a dropped blow shifts every row after it and the analysis would
 * quietly report the whole touch as badly struck.
 */
public final class StrikeStream implements Iterable<Strike> {

    private final int stage;
    private final List<Strike> strikes;

    /**
     * @param stage the stage the bells were rung at
     * @param strikes the blows, already in time order
     * @throws ValidationException when the stage is unsupported, a blow is not
     *     on the stage or has no time, or the blows are out of order
     */
    public StrikeStream(int stage, List<Strike> strikes) {
        if (!Stage.isSupported(stage)) {
            throw new ValidationException("stage " + stage + " is not supported");
        }
        if (strikes == null) {
            throw new ValidationException("a strike stream needs an array of strikes");
        }
        List<Strike> kept = new ArrayList<>(strikes.size());
        for (int index = 0; index < strikes.size(); index += 1) {
            kept.add(readStrike(strikes.get(index), index, stage));
        }
        for (int index = 1; index < kept.size(); index += 1) {
            if (kept.get(index).at() < kept.get(index - 1).at()) {
                throw new ValidationException(
                        "strike " + index + " is at "
                                + Json.number(kept.get(index).at()) + " which is before strike " + (index - 1),
                        Map.of("index", index));
            }
        }
        this.stage = stage;
        this.strikes = Collections.unmodifiableList(kept);
    }

    /**
     * Build a stream from whatever the recorder produced, sorting it into time
     * order first. Sorting rather than refusing is right here: two bells can be
     * recorded in either order when they strike within a millisecond of each
     * other, and that is not an error. Blows at the same moment keep the order
     * they arrived in.
     *
     * @param stage the stage the bells were rung at
     * @param strikes the blows, in any order
     * @return the stream
     * @throws ValidationException when there are no strikes or one of them is unusable
     */
    public static StrikeStream from(int stage, List<Strike> strikes) {
        if (strikes == null) {
            throw new ValidationException("a strike stream needs an array of strikes");
        }
        List<Strike> sorted = new ArrayList<>(strikes);
        sorted.sort(Comparator.nullsFirst(Comparator.comparingDouble(Strike::at)));
        return new StrikeStream(stage, sorted);
    }

    /**
     * @return the stage the bells were rung at
     */
    public int stage() {
        return stage;
    }

    /**
     * @return the blows, in time order, as a list that cannot be changed
     */
    public List<Strike> strikes() {
        return strikes;
    }

    /**
     * @return how many blows the stream holds
     */
    public int length() {
        return strikes.size();
    }

    /**
     * @return how long the recording runs, in milliseconds, from the first blow to the last
     */
    public double duration() {
        if (strikes.isEmpty()) {
            return 0;
        }
        return strikes.get(strikes.size() - 1).at() - strikes.get(0).at();
    }

    /**
     * @return the gaps between one blow and the next, one fewer than the blows
     */
    public List<Double> gaps() {
        List<Double> gaps = new ArrayList<>(Math.max(0, strikes.size() - 1));
        for (int index = 1; index < strikes.size(); index += 1) {
            gaps.add(strikes.get(index).at() - strikes.get(index - 1).at());
        }
        return gaps;
    }

    /**
     * @return how many blows each bell struck, in the order the bells first struck
     */
    public Map<Integer, Integer> blowsPerBell() {
        return CollectionHelpers.countBy(strikes, Strike::bell);
    }

    /**
     * The bells that struck fewer blows than the rest, which is how a dropped
     * blow or a bell that stopped ringing shows up.
     *
     * @return the bells, ascending
     */
    public List<Integer> bellsWithMissingBlows() {
        Map<Integer, Integer> counts = blowsPerBell();
        int expected =
                counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<Integer> missing = new ArrayList<>();
        for (int bell = 1; bell <= stage; bell += 1) {
            if (counts.getOrDefault(bell, 0) < expected) {
                missing.add(bell);
            }
        }
        return missing;
    }

    /**
     * The strikes between two times, the start included and the end not.
     *
     * @param from the first moment included
     * @param to the first moment left out
     * @return the strikes in that span
     */
    public List<Strike> between(double from, double to) {
        return strikes.stream()
                .filter(strike -> strike.at() >= from && strike.at() < to)
                .toList();
    }

    @Override
    public Iterator<Strike> iterator() {
        return strikes.iterator();
    }

    private static Strike readStrike(Strike strike, int index, int stage) {
        if (strike == null) {
            throw new ValidationException("strike " + index + " is not an object");
        }
        if (strike.bell() < 1 || strike.bell() > stage) {
            throw new ValidationException(
                    "strike " + index + " names bell " + strike.bell() + ", which is not on " + stage);
        }
        if (!Double.isFinite(strike.at())) {
            throw new ValidationException("strike " + index + " has no time");
        }
        return strike;
    }
}
