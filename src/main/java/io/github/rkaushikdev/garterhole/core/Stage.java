package io.github.rkaushikdev.garterhole.core;

import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.Text;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Stages are the number of bells a method is rung on. Ringers name them rather
 * than count them, and the names are not derived from the number in any
 * regular way, so the table is the definition.
 */
public final class Stage {

    /** The smallest stage that has a name and a working method literature. */
    public static final int MIN = 3;

    /**
     * The largest stage this library will build rows for. Sixteen is the
     * practical ceiling for a tower; anything above it is handbell territory and
     * uses a different symbol set.
     */
    public static final int MAX = 16;

    private static final Map<Integer, String> NAMES = Map.ofEntries(
            Map.entry(3, "Singles"),
            Map.entry(4, "Minimus"),
            Map.entry(5, "Doubles"),
            Map.entry(6, "Minor"),
            Map.entry(7, "Triples"),
            Map.entry(8, "Major"),
            Map.entry(9, "Caters"),
            Map.entry(10, "Royal"),
            Map.entry(11, "Cinques"),
            Map.entry(12, "Maximus"),
            Map.entry(13, "Sextuples"),
            Map.entry(14, "Fourteen"),
            Map.entry(15, "Septuples"),
            Map.entry(16, "Sixteen"));

    private Stage() {}

    /**
     * @param stage a number of bells
     * @return true when the stage is one this library can work with
     */
    public static boolean isSupported(int stage) {
        return stage >= MIN && stage <= MAX;
    }

    /**
     * @param stage a number that may not be whole
     * @return true when the number is a whole stage this library can work with
     */
    public static boolean isSupported(double stage) {
        return stage == Math.rint(stage) && stage >= MIN && stage <= MAX;
    }

    /**
     * @param stage a number of bells
     * @return the ringing name for the stage, for example {@code Major}
     * @throws IllegalArgumentException when the stage has no name
     */
    public static String name(int stage) {
        String name = NAMES.get(stage);
        if (name == null) {
            throw new IllegalArgumentException("no stage name for " + stage + " bells");
        }
        return name;
    }

    /**
     * Look a stage up by its ringing name. Comparison ignores case and padding
     * so that input typed by a ringer in a hurry still resolves.
     *
     * @param name a stage name
     * @return the stage
     * @throws IllegalArgumentException when no stage has that name
     */
    public static int fromName(String name) {
        String wanted = Text.trim(String.valueOf(name)).toLowerCase(Locale.ROOT);
        for (int stage : supported()) {
            if (NAMES.get(stage).toLowerCase(Locale.ROOT).equals(wanted)) {
                return stage;
            }
        }
        throw new IllegalArgumentException("unknown stage name " + Json.quote(name));
    }

    /**
     * @param stage a number of bells
     * @return true for an odd number of bells
     */
    public static boolean isOdd(int stage) {
        return stage % 2 == 1;
    }

    /**
     * True when the stage is normally rung with a bell covering behind.
     *
     * <p>The cover bell is not part of the stage. Doubles is five bells
     * changing, and the tower rings it on six with the tenor behind. Counting
     * the cover into the stage is a mistake that makes the extent of Doubles
     * come out as twenty four rows instead of a hundred and twenty.
     *
     * @param stage a number of bells
     * @return true when a bell covers behind
     */
    public static boolean hasCover(int stage) {
        return isOdd(stage);
    }

    /**
     * @param stage a number of bells
     * @return how many bells the tower needs to ring the stage, which is one
     *     more than the stage when a bell covers
     */
    public static int bellsInTower(int stage) {
        return hasCover(stage) ? stage + 1 : stage;
    }

    /**
     * @return every stage this library supports, ascending
     */
    public static List<Integer> supported() {
        return IntStream.rangeClosed(MIN, MAX).boxed().toList();
    }
}
