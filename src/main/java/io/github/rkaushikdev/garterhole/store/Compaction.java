package io.github.rkaushikdev.garterhole.store;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import java.util.ArrayList;
import java.util.List;

/**
 * Squeezing the journal.
 *
 * <p>A journal that has been running for a few years holds a great many records
 * that no longer matter: every earlier version of a document that has since
 * been replaced, and every document that has since been deleted. Compaction
 * writes a new journal holding one put per document that is still there.
 *
 * <p>The result folds to exactly the same state, which is the property worth
 * testing rather than the size it saves. Anything that changed the state would
 * be data loss dressed up as maintenance.
 */
public final class Compaction {

    private Compaction() {}

    /**
     * How much of a journal is no longer doing any work.
     *
     * @param records how many records the journal holds
     * @param live how many of them a compacted journal would keep
     * @param wasted how many it would drop
     * @param ratio the share that would be dropped, zero for an empty journal
     */
    public record Gain(int records, int live, int wasted, double ratio) {}

    /**
     * When compaction is worth doing: a journal with at least so many records,
     * of which at least so large a share is waste.
     *
     * @param minimumRecords the fewest records worth compacting
     * @param minimumRatio the smallest share of waste worth compacting
     */
    public record Thresholds(int minimumRecords, double minimumRatio) {

        /** A hundred records, half of them waste. */
        public static final Thresholds DEFAULT = new Thresholds(100, 0.5);

        /**
         * @param records the fewest records worth compacting
         * @return these thresholds with that many records
         */
        public Thresholds withMinimumRecords(int records) {
            return new Thresholds(records, minimumRatio);
        }

        /**
         * @param ratio the smallest share of waste worth compacting
         * @return these thresholds with that share
         */
        public Thresholds withMinimumRatio(double ratio) {
            return new Thresholds(minimumRecords, ratio);
        }
    }

    /**
     * How long the journal was and is.
     *
     * @param before records before compacting
     * @param after records after
     */
    public record Result(int before, int after) {}

    /**
     * What {@link #compactIfWorthwhile} did.
     *
     * @param compacted true when the journal was rewritten
     * @param before records before
     * @param after records after, the same as before when nothing was done
     */
    public record Outcome(boolean compacted, int before, int after) {}

    /**
     * @param repository the repository
     * @return how much of its journal is no longer doing any work
     */
    public static Gain compactionGain(Repository repository) {
        requireRepository(repository);
        int records = repository.journal().count();
        int live = liveRecords(repository).size();
        int wasted = records - live;
        return new Gain(records, live, wasted, records == 0 ? 0 : (double) wasted / records);
    }

    /**
     * True when the journal is wasteful enough by the default thresholds to be
     * worth rewriting.
     *
     * @param repository the repository
     * @return true when compaction is worth it
     */
    public static boolean worthCompacting(Repository repository) {
        return worthCompacting(repository, Thresholds.DEFAULT);
    }

    /**
     * True when the journal is wasteful enough to be worth rewriting.
     * Compaction is cheap but not free, and doing it on every start up would
     * rewrite a file that is almost all live records.
     *
     * @param repository the repository
     * @param thresholds when it counts as worth it
     * @return true when compaction is worth it
     */
    public static boolean worthCompacting(Repository repository, Thresholds thresholds) {
        Gain gain = compactionGain(repository);
        return gain.records() >= thresholds.minimumRecords() && gain.ratio() >= thresholds.minimumRatio();
    }

    /**
     * The records a compacted journal would hold: one put per living document,
     * in collection and identifier order so that the file is stable.
     *
     * @param repository the repository
     * @return the records
     */
    public static List<ObjectNode> liveRecords(Repository repository) {
        requireRepository(repository);
        List<ObjectNode> records = new ArrayList<>();
        for (String collection : repository.collectionNames()) {
            for (Repository.Entry entry : repository.all(collection)) {
                records.add(Repository.putRecord(collection, entry.id(), entry.document()));
            }
        }
        return records;
    }

    /**
     * Rewrite the journal so that it holds only what is still there, and throw
     * the snapshot away, since it counts the old records.
     *
     * @param repository the repository
     * @return how long the journal was and is
     */
    public static Result compact(Repository repository) {
        requireRepository(repository);
        int before = repository.journal().count();
        List<ObjectNode> records = liveRecords(repository);
        repository.journal().rewrite(records);
        repository.journalRewritten(records.size());
        repository.snapshot().ifPresent(Snapshot::remove);
        return new Result(before, records.size());
    }

    /**
     * Compact only when it is worth it by the default thresholds.
     *
     * @param repository the repository
     * @return whether anything was done, and the lengths
     */
    public static Outcome compactIfWorthwhile(Repository repository) {
        return compactIfWorthwhile(repository, Thresholds.DEFAULT);
    }

    /**
     * Compact only when it is worth it, and say whether anything was done.
     *
     * @param repository the repository
     * @param thresholds when it counts as worth it
     * @return whether anything was done, and the lengths
     */
    public static Outcome compactIfWorthwhile(Repository repository, Thresholds thresholds) {
        if (!worthCompacting(repository, thresholds)) {
            int records = repository.journal().count();
            return new Outcome(false, records, records);
        }
        Result result = compact(repository);
        return new Outcome(true, result.before(), result.after());
    }

    private static void requireRepository(Repository repository) {
        if (repository == null) {
            throw new ValidationException("a Repository is needed to compact it");
        }
    }
}
