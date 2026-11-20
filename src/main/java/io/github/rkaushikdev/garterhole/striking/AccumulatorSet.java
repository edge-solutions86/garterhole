package io.github.rkaushikdev.garterhole.striking;

import io.github.rkaushikdev.garterhole.striking.ErrorAccumulator.Summary;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A set of accumulators keyed on something, which is what every breakdown in
 * the striking analysis is: by bell, by row, by window.
 *
 * @param <K> what the errors are grouped by
 * @param <S> what each error came from
 */
public final class AccumulatorSet<K extends Comparable<? super K>, S> {

    private final Map<K, ErrorAccumulator<S>> byKey = new LinkedHashMap<>();

    /**
     * @param key the group the error belongs to
     * @param error the error in milliseconds
     * @param source what the error came from
     * @return this set, so that adds can be chained
     */
    public AccumulatorSet<K, S> add(K key, double error, S source) {
        byKey.computeIfAbsent(key, ignored -> new ErrorAccumulator<>()).add(error, source);
        return this;
    }

    /**
     * @return the summaries, in the natural order of the keys
     */
    public Map<K, Summary> summaries() {
        return summaries(Comparator.naturalOrder());
    }

    /**
     * @param order the order the keys come out in
     * @return the summaries, in that order
     */
    public Map<K, Summary> summaries(Comparator<? super K> order) {
        List<K> keys = new ArrayList<>(byKey.keySet());
        keys.sort(order);
        Map<K, Summary> summaries = new LinkedHashMap<>();
        for (K key : keys) {
            summaries.put(key, byKey.get(key).summary());
        }
        return Collections.unmodifiableMap(summaries);
    }

    /**
     * @return the key with the worst root mean square, the first added on a tie,
     *     or nothing when nothing was added
     */
    public Optional<K> worstKey() {
        K worst = null;
        double worstRms = -1;
        for (Map.Entry<K, ErrorAccumulator<S>> entry : byKey.entrySet()) {
            double rms = entry.getValue().rms();
            if (rms > worstRms) {
                worst = entry.getKey();
                worstRms = rms;
            }
        }
        return Optional.ofNullable(worst);
    }
}
