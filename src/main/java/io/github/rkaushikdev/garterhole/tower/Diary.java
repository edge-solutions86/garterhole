package io.github.rkaushikdev.garterhole.tower;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Guards;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Who is free when.
 *
 * <p>A band's diary is a set of intervals per ringer. The questions asked of it
 * are always the same two: who can come to this practice, and when could we
 * hold one that everybody can come to.
 *
 * <p>A ringer with nothing written down is treated as unavailable rather than
 * available. That is the safer way round: a plan built on a ringer who never
 * said they were coming falls apart in the tower, and a plan that leaves
 * somebody out only needs a phone call.
 */
public final class Diary {

    private final Map<String, IntervalSet> byRinger = new LinkedHashMap<>();

    /**
     * The best time to hold a practice, and who could come to it.
     *
     * @param slot the time
     * @param band the ringers free for the whole of it, in id order
     */
    public record Choice(Interval slot, List<String> band) {}

    /**
     * Write down that a ringer is free for a stretch of time.
     *
     * @param ringerId the ringer
     * @param slot the time they are free
     * @return this diary
     * @throws ValidationException when there is no slot
     * @throws IllegalArgumentException when the ringer id is missing or empty
     */
    public Diary free(String ringerId, Interval slot) {
        Guards.nonEmptyString(ringerId, "a ringer id");
        if (slot == null) {
            throw new ValidationException("a diary entry needs an Interval");
        }
        byRinger.computeIfAbsent(ringerId, id -> new IntervalSet()).add(slot);
        return this;
    }

    /**
     * Everything a ringer has said they are free for. The set is a copy, so
     * adding to it leaves the diary alone.
     *
     * @param ringerId the ringer
     * @return their free time, empty when they have said nothing
     */
    public IntervalSet availabilityOf(String ringerId) {
        IntervalSet found = byRinger.get(ringerId);
        return found == null ? new IntervalSet() : new IntervalSet(found);
    }

    /**
     * @param ringerId the ringer
     * @param slot a practice
     * @return true when the ringer is free for the whole of it
     */
    public boolean isFree(String ringerId, Interval slot) {
        IntervalSet found = byRinger.get(ringerId);
        return found != null && found.covers(slot);
    }

    /**
     * @param slot a practice
     * @return who could come to it, in id order
     */
    public List<String> whoIsFree(Interval slot) {
        List<String> free = new ArrayList<>();
        for (String ringerId : byRinger.keySet()) {
            if (isFree(ringerId, slot)) {
                free.add(ringerId);
            }
        }
        free.sort(null);
        return List.copyOf(free);
    }

    /**
     * How many of the band could come, which is the number a captain looks at
     * before deciding whether to cancel.
     *
     * @param slot a practice
     * @return the number free for the whole of it
     */
    public int countFree(Interval slot) {
        return whoIsFree(slot).size();
    }

    /**
     * @param ringerIds the ringers
     * @return the times everybody named is free at once
     * @throws ValidationException when nobody is named
     */
    public IntervalSet commonTime(List<String> ringerIds) {
        if (ringerIds == null || ringerIds.isEmpty()) {
            throw new ValidationException("naming nobody has no common time");
        }
        IntervalSet shared = availabilityOf(ringerIds.get(0));
        for (String ringerId : ringerIds.subList(1, ringerIds.size())) {
            shared = shared.intersect(availabilityOf(ringerId));
        }
        return shared;
    }

    /**
     * The best time to hold a practice, judged by how many of the band could
     * come, with any band at all being worth ringing with.
     *
     * @param candidates the times on offer
     * @return the best of them, when anybody could come to one
     */
    public Optional<Choice> bestSlot(List<Interval> candidates) {
        return bestSlot(candidates, 1);
    }

    /**
     * The best time to hold a practice, judged by how many of the band could
     * come. Ties go to the earliest slot offered, so that a captain asked to
     * explain the choice has an answer.
     *
     * @param candidates the times on offer
     * @param needed the smallest band worth ringing with
     * @return the best of them, when one has a big enough band
     * @throws ValidationException when there are no candidates
     */
    public Optional<Choice> bestSlot(List<Interval> candidates, int needed) {
        if (candidates == null) {
            throw new ValidationException("a list of candidate slots is needed");
        }
        Choice best = null;
        for (Interval slot : candidates) {
            List<String> band = whoIsFree(slot);
            if (band.size() < needed) {
                continue;
            }
            if (best == null || band.size() > best.band().size()) {
                best = new Choice(slot, band);
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * @return every ringer with anything written down, in id order
     */
    public List<String> ringers() {
        return byRinger.keySet().stream().sorted().toList();
    }
}
