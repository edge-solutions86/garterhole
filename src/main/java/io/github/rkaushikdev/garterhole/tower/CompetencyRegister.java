package io.github.rkaushikdev.garterhole.tower;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Guards;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.LocaleOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Who can ring what.
 *
 * <p>A band's repertoire is the single most useful thing a practice planner
 * has, and the single thing most towers keep only in somebody's head. The
 * register writes it down as a level per ringer per method, so that a plan can
 * be made without ringing round the band first.
 *
 * <p>The levels are ordered, and the order is the point: anything a ringer can
 * do at a higher level they can do at a lower one, which is what lets a query
 * ask for "at least confident" rather than listing the levels it will accept.
 */
public final class CompetencyRegister {

    /** How well a ringer can ring a method, from least to most able. */
    public enum Level {
        /** Still learning it. */
        LEARNING,
        /** Can ring an inside bell to it. */
        INSIDE,
        /** Can ring any bell to it. */
        CONFIDENT,
        /** Can call it. */
        CONDUCTS;

        /**
         * @return the level the way a register writes it, for example {@code confident}
         */
        public String label() {
            return name().toLowerCase(Locale.ROOT);
        }

        /**
         * @return the rank of the level, for comparing two of them
         */
        public int rank() {
            return ordinal();
        }

        /**
         * @param atLeast another level
         * @return true when this level is at least as good as the other
         */
        public boolean meets(Level atLeast) {
            return rank() >= atLeast.rank();
        }

        /**
         * @param label the level as a register writes it
         * @return the level
         * @throws ValidationException when no level has that name
         */
        public static Level of(String label) {
            for (Level level : values()) {
                if (level.label().equals(label)) {
                    return level;
                }
            }
            throw new ValidationException(Json.quote(label) + " is not a level of competency");
        }

        @Override
        public String toString() {
            return label();
        }
    }

    /** The levels, from least to most able. */
    public static final List<Level> LEVELS = List.of(Level.values());

    /** The level a query asks for when it does not say. */
    public static final Level DEFAULT_LEVEL = Level.INSIDE;

    /**
     * One method in a ringer's repertoire.
     *
     * @param title the method title
     * @param level how well they ring it
     */
    public record Entry(String title, Level level) {}

    private final Map<String, Map<String, Level>> byRinger = new LinkedHashMap<>();

    /**
     * Write down what a ringer can do, replacing whatever was there before.
     *
     * @param ringerId the ringer
     * @param title the method title
     * @param level how well they ring it
     * @return this register, so that records can be chained
     * @throws ValidationException when there is no level
     * @throws IllegalArgumentException when the ringer id or the title is missing or empty
     */
    public CompetencyRegister record(String ringerId, String title, Level level) {
        Guards.nonEmptyString(ringerId, "a ringer id");
        Guards.nonEmptyString(title, "a method title");
        if (level == null) {
            throw new ValidationException("null is not a level of competency");
        }
        byRinger.computeIfAbsent(ringerId, id -> new LinkedHashMap<>()).put(title, level);
        return this;
    }

    /**
     * Take a method out of a ringer's repertoire.
     *
     * @param ringerId the ringer
     * @param title the method title
     * @return true when there was something to forget
     */
    public boolean forget(String ringerId, String title) {
        Map<String, Level> repertoire = byRinger.get(ringerId);
        return repertoire != null && repertoire.remove(title) != null;
    }

    /**
     * @param ringerId the ringer
     * @param title the method title
     * @return their level in the method, when they have ever rung it
     */
    public Optional<Level> levelOf(String ringerId, String title) {
        Map<String, Level> repertoire = byRinger.get(ringerId);
        return repertoire == null ? Optional.empty() : Optional.ofNullable(repertoire.get(title));
    }

    /**
     * @param ringerId the ringer
     * @param title the method title
     * @return true when the ringer can at least ring an inside bell to the method
     */
    public boolean canRing(String ringerId, String title) {
        return canRing(ringerId, title, DEFAULT_LEVEL);
    }

    /**
     * @param ringerId the ringer
     * @param title the method title
     * @param atLeast the level asked for
     * @return true when the ringer is at least at that level in the method
     */
    public boolean canRing(String ringerId, String title, Level atLeast) {
        return levelOf(ringerId, title).map(level -> level.meets(atLeast)).orElse(false);
    }

    /**
     * @param ringerId the ringer
     * @return everything the ringer can ring, in title order
     */
    public List<Entry> repertoireOf(String ringerId) {
        Map<String, Level> repertoire = byRinger.get(ringerId);
        if (repertoire == null) {
            return List.of();
        }
        List<Entry> entries = new ArrayList<>();
        repertoire.forEach((title, level) -> entries.add(new Entry(title, level)));
        entries.sort(Comparator.comparing(Entry::title, LocaleOrder.ORDER));
        return List.copyOf(entries);
    }

    /**
     * @param title the method title
     * @return the ringers who can at least ring an inside bell to it, in id order
     */
    public List<String> whoCanRing(String title) {
        return whoCanRing(title, DEFAULT_LEVEL);
    }

    /**
     * @param title the method title
     * @param atLeast the level asked for
     * @return the ringers at least at that level in the method, in id order
     */
    public List<String> whoCanRing(String title, Level atLeast) {
        List<String> found = new ArrayList<>();
        for (String ringerId : byRinger.keySet()) {
            if (canRing(ringerId, title, atLeast)) {
                found.add(ringerId);
            }
        }
        found.sort(null);
        return List.copyOf(found);
    }

    /**
     * How many of the band can ring each method at an inside bell, which is the
     * table a tower captain uses to decide what to teach next.
     *
     * @return the count for each method, most first
     */
    public Map<String, Integer> coverage() {
        return coverage(DEFAULT_LEVEL);
    }

    /**
     * How many of the band can ring each method at a level.
     *
     * @param atLeast the level asked for
     * @return the count for each method, most first, ties in the order the methods were first recorded
     */
    public Map<String, Integer> coverage(Level atLeast) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map<String, Level> repertoire : byRinger.values()) {
            repertoire.forEach((title, level) -> {
                if (level.meets(atLeast)) {
                    counts.merge(title, 1, Integer::sum);
                }
            });
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        Map<String, Integer> ordered = new LinkedHashMap<>();
        sorted.forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return java.util.Collections.unmodifiableMap(ordered);
    }

    /**
     * @return every method anybody in the band has rung, sorted
     */
    public List<String> titles() {
        TreeSet<String> titles = new TreeSet<>();
        byRinger.values().forEach(repertoire -> titles.addAll(repertoire.keySet()));
        return List.copyOf(titles);
    }
}
