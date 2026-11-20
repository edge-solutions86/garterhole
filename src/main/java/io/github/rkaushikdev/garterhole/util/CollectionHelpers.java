package io.github.rkaushikdev.garterhole.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Small collection helpers. Nothing here is clever; it exists so that the
 * domain code reads as domain code rather than as index arithmetic, and so that
 * the same fold is not written out eleven times.
 *
 * <p>Every helper leaves its input alone and hands back new collections, and
 * every grouping keeps the order the items arrived in.
 */
public final class CollectionHelpers {

    private CollectionHelpers() {}

    /**
     * Group items by a key, keeping the order they arrived in.
     *
     * @param <T> the item type
     * @param <K> the key type
     * @param items the items
     * @param keyOf the key of an item
     * @return the groups, in the order their keys were first seen
     */
    public static <T, K> Map<K, List<T>> groupBy(Iterable<? extends T> items, Function<? super T, ? extends K> keyOf) {
        Map<K, List<T>> groups = new LinkedHashMap<>();
        for (T item : items) {
            groups.computeIfAbsent(keyOf.apply(item), key -> new ArrayList<>()).add(item);
        }
        return groups;
    }

    /**
     * Count items by a key.
     *
     * @param <T> the item type
     * @param <K> the key type
     * @param items the items
     * @param keyOf the key of an item
     * @return how many items each key has, in the order the keys were first seen
     */
    public static <T, K> Map<K, Integer> countBy(Iterable<? extends T> items, Function<? super T, ? extends K> keyOf) {
        Map<K, Integer> counts = new LinkedHashMap<>();
        for (T item : items) {
            counts.merge(keyOf.apply(item), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Break a list into runs of a fixed size. The last run is short when the
     * list does not divide evenly.
     *
     * @param <T> the item type
     * @param items the items
     * @param size how many items a run holds
     * @return the runs
     * @throws IllegalArgumentException when the size is not positive
     */
    public static <T> List<List<T>> chunk(List<? extends T> items, int size) {
        if (size < 1) {
            throw new IllegalArgumentException("chunk size " + size + " must be a positive whole number");
        }
        List<List<T>> chunks = new ArrayList<>();
        for (int index = 0; index < items.size(); index += size) {
            chunks.add(List.copyOf(items.subList(index, Math.min(items.size(), index + size))));
        }
        return chunks;
    }

    /**
     * Remove repeats, keeping the first of each.
     *
     * @param <T> the item type
     * @param items the items
     * @return the items with repeats removed
     */
    public static <T> List<T> unique(Iterable<? extends T> items) {
        return unique(items, item -> item);
    }

    /**
     * Remove repeats, keeping the first of each, where two items repeat when
     * their keys are equal. That lets value objects be de-duplicated by their
     * printed form.
     *
     * @param <T> the item type
     * @param items the items
     * @param keyOf the identity of an item
     * @return the items with repeats removed
     */
    public static <T> List<T> unique(Iterable<? extends T> items, Function<? super T, ?> keyOf) {
        Set<Object> seen = new HashSet<>();
        List<T> kept = new ArrayList<>();
        for (T item : items) {
            if (seen.add(keyOf.apply(item))) {
                kept.add(item);
            }
        }
        return kept;
    }

    /**
     * Sort without disturbing the input. Ties keep their original order, so a
     * comparator built from several keys falls back to the next on a tie.
     *
     * @param <T> the item type
     * @param items the items
     * @param order how two items compare
     * @return a new sorted list
     */
    public static <T> List<T> sortBy(Collection<? extends T> items, java.util.Comparator<? super T> order) {
        List<T> sorted = new ArrayList<>(items);
        sorted.sort(order);
        return sorted;
    }

    /**
     * Split a list in two by a test.
     *
     * @param <T> the item type
     * @param items the items
     * @param test which side an item belongs on
     * @return the items that passed and the items that did not
     */
    public static <T> Partition<T> partition(Iterable<? extends T> items, Predicate<? super T> test) {
        List<T> passed = new ArrayList<>();
        List<T> failed = new ArrayList<>();
        for (T item : items) {
            (test.test(item) ? passed : failed).add(item);
        }
        return new Partition<>(List.copyOf(passed), List.copyOf(failed));
    }

    /**
     * Pair up two lists. The result is as long as the shorter one, which is what
     * the striking analysis wants when a stream of strikes runs out early.
     *
     * @param <A> the type on the left
     * @param <B> the type on the right
     * @param left the left hand list
     * @param right the right hand list
     * @return the pairs
     */
    public static <A, B> List<Pair<A, B>> zip(List<? extends A> left, List<? extends B> right) {
        int length = Math.min(left.size(), right.size());
        List<Pair<A, B>> pairs = new ArrayList<>(length);
        for (int index = 0; index < length; index += 1) {
            pairs.add(new Pair<>(left.get(index), right.get(index)));
        }
        return pairs;
    }

    /**
     * @param values the numbers
     * @return their sum, or zero for none
     */
    public static double sum(Iterable<? extends Number> values) {
        double total = 0;
        for (Number value : values) {
            total += value.doubleValue();
        }
        return total;
    }

    /**
     * The mean, or nothing for an empty list. Nothing rather than NaN, because a
     * missing answer travels through JSON and a NaN does not.
     *
     * @param values the numbers
     * @return their mean, when there are any
     */
    public static OptionalDouble mean(Collection<? extends Number> values) {
        if (values.isEmpty()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(sum(values) / values.size());
    }

    /**
     * The item with the largest score. Ties go to the first item, which keeps
     * the answer stable across runs.
     *
     * @param <T> the item type
     * @param items the items
     * @param scoreOf the score of an item
     * @return the best item, when there are any
     */
    public static <T> Optional<T> maxBy(Iterable<? extends T> items, ToDoubleFunction<? super T> scoreOf) {
        T best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (T item : items) {
            double score = scoreOf.applyAsDouble(item);
            if (score > bestScore) {
                best = item;
                bestScore = score;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * The two sides of a partition.
     *
     * @param <T> the item type
     * @param passed the items that passed the test
     * @param failed the items that did not
     */
    public record Partition<T>(List<T> passed, List<T> failed) {}

    /**
     * Two things paired up.
     *
     * @param <A> the type on the left
     * @param <B> the type on the right
     * @param left the left hand item
     * @param right the right hand item
     */
    public record Pair<A, B>(A left, B right) {}
}
