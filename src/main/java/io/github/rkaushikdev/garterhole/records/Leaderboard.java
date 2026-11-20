package io.github.rkaushikdev.garterhole.records;

import static io.github.rkaushikdev.garterhole.util.Text.plural;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.records.Statistics.RingerLine;
import io.github.rkaushikdev.garterhole.records.Statistics.Totals;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.LocaleOrder;
import io.github.rkaushikdev.garterhole.util.Text;
import io.github.rkaushikdev.garterhole.util.Text.Align;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The table an association prints at the end of the year.
 *
 * <p>Counting performances is not the only way to order it, and which way is
 * chosen changes who is at the top. Somebody who rings fifty quarters is not
 * obviously ahead of somebody who rings four peals, so the ordering is a
 * parameter with three honest choices rather than a decision buried in a sort.
 */
public final class Leaderboard {

    /** How a leaderboard may be ordered. */
    public static final List<String> ORDERINGS = List.of("performances", "peals", "changes", "conducted");

    private static final String DEFAULT_ORDERING = "performances";
    private static final int DEFAULT_TOP = 20;

    /** Highest score first, and by name on a tie. */
    private static final Comparator<Place> BEST_FIRST =
            Comparator.comparingLong(Place::score).reversed().thenComparing(Place::name, LocaleOrder.ORDER);

    private Leaderboard() {}

    /**
     * How a leaderboard is drawn up.
     *
     * @param ordering one of {@link #ORDERINGS}
     * @param top how many places to give
     */
    public record Options(String ordering, int top) {

        /** Ordered by performances, twenty places. */
        public static final Options DEFAULT = new Options(DEFAULT_ORDERING, DEFAULT_TOP);

        /**
         * @param how one of {@link #ORDERINGS}
         * @return these options with another ordering
         */
        public Options withOrdering(String how) {
            return new Options(how, top);
        }

        /**
         * @param places how many places to give
         * @return these options with another number of places
         */
        public Options withTop(int places) {
            return new Options(ordering, places);
        }
    }

    /**
     * One place on the board.
     *
     * @param place the place, counting from one
     * @param name the ringer
     * @param score what they scored under the ordering
     * @param totals what they rang, added up
     * @param conducted how many they conducted
     */
    public record Place(int place, String name, long score, Totals totals, int conducted) {}

    /**
     * The score a ringer gets under an ordering.
     *
     * @param line what the ringer rang
     * @param ordering one of {@link #ORDERINGS}
     * @return the score
     * @throws ValidationException when the ordering is not one of them
     */
    public static long scoreOf(RingerLine line, String ordering) {
        if (!ORDERINGS.contains(ordering)) {
            throw new ValidationException(
                    Json.quote(ordering) + " is not an ordering, so try one of " + String.join(", ", ORDERINGS));
        }
        return switch (ordering) {
            case "conducted" -> line.conducted();
            case "peals" -> line.totals().peals();
            case "changes" -> line.totals().changes();
            default -> line.totals().performances();
        };
    }

    /**
     * The leaderboard by performances, twenty places.
     *
     * @param records the performances
     * @return the places
     */
    public static List<Place> leaderboard(Iterable<Performance> records) {
        return leaderboard(records, Options.DEFAULT);
    }

    /**
     * The leaderboard. Anybody who scored nothing is left off.
     *
     * @param records the performances
     * @param options the ordering and how many places
     * @return the places
     * @throws ValidationException when the board would have no places or the ordering is unknown
     */
    public static List<Place> leaderboard(Iterable<Performance> records, Options options) {
        if (options.top() < 1) {
            throw new ValidationException("a leaderboard of " + options.top() + " places is not one");
        }
        List<Place> lines = new ArrayList<>();
        for (RingerLine line : Statistics.byRinger(records)) {
            lines.add(new Place(0, line.name(), scoreOf(line, options.ordering()), line.totals(), line.conducted()));
        }
        lines.sort(BEST_FIRST);
        List<Place> board = new ArrayList<>();
        for (Place line : lines) {
            if (line.score() <= 0 || board.size() == options.top()) {
                continue;
            }
            board.add(new Place(board.size() + 1, line.name(), line.score(), line.totals(), line.conducted()));
        }
        return List.copyOf(board);
    }

    /**
     * The leaderboard by performances, as lines of text.
     *
     * @param records the performances
     * @return the heading and the table
     */
    public static List<String> leaderboardLines(Iterable<Performance> records) {
        return leaderboardLines(records, Options.DEFAULT);
    }

    /**
     * The leaderboard as lines of text.
     *
     * @param records the performances
     * @param options the ordering and how many places
     * @return the heading and the table, or a line saying there is nothing to show
     */
    public static List<String> leaderboardLines(Iterable<Performance> records, Options options) {
        List<Place> board = leaderboard(records, options);
        if (board.isEmpty()) {
            return List.of("nobody has rung anything");
        }
        List<String> lines = new ArrayList<>();
        lines.add("Most " + options.ordering());
        lines.addAll(Text.tabulate(
                board.stream()
                        .map(line -> List.of(String.valueOf(line.place()), line.name(), String.valueOf(line.score())))
                        .toList(),
                List.of(Align.RIGHT, Align.LEFT, Align.RIGHT)));
        return lines;
    }

    /**
     * A sentence summing the year up, which is what goes at the top of the report.
     *
     * @param records the performances
     * @return the sentence
     */
    public static String summary(Iterable<Performance> records) {
        List<Performance> kept = new ArrayList<>();
        records.forEach(kept::add);
        Totals counted = Statistics.totals(kept);
        List<Place> board = leaderboard(kept, Options.DEFAULT.withTop(1));
        if (board.isEmpty()) {
            return "nothing was rung";
        }
        return plural(counted.performances(), "performance") + " in " + plural(counted.changes(), "change")
                + ", most of them by " + board.get(0).name() + " with "
                + board.get(0).score();
    }
}
