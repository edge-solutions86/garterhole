package io.github.rkaushikdev.garterhole.striking;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.core.Bells;
import io.github.rkaushikdev.garterhole.striking.ErrorAccumulator.Summary;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.Text;
import io.github.rkaushikdev.garterhole.util.Text.Align;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turning a striking analysis into something a band will read.
 *
 * <p>A report that opens with a table of milliseconds gets folded up. One that
 * opens with a sentence saying how the ringing went, and only then gives the
 * numbers, gets read to the end. So the order here is deliberate: the verdict,
 * then the bells, then the faults.
 */
public final class StrikingReport {

    /** How many faults a report lists before it says how many more there were. */
    public static final int FAULT_LIMIT = 10;

    private static final double EXCELLENT = 20;
    private static final double GOOD = 40;
    private static final double FAIR = 70;
    private static final long EVEN = 10;

    private StrikingReport() {}

    /**
     * The verdict on a session, in the words a tower captain would use.
     *
     * @param rms the root mean square error in milliseconds
     * @return excellent, good, fair or ragged
     * @throws ValidationException when the accuracy is negative
     */
    public static String verdict(double rms) {
        if (rms < 0) {
            throw new ValidationException(Json.number(rms) + " is not a striking accuracy");
        }
        if (rms < EXCELLENT) {
            return "excellent";
        }
        if (rms < GOOD) {
            return "good";
        }
        if (rms < FAIR) {
            return "fair";
        }
        return "ragged";
    }

    /**
     * The opening sentence of a report.
     *
     * @param session the analysis
     * @return for example {@code 61 rows rung, excellent striking at 0ms}
     */
    public static String headline(StrikingSession session) {
        requireSession(session);
        double rms = session.accuracy().rms();
        return Text.plural(session.rowCount(), "row") + " rung, " + verdict(rms) + " striking at " + Math.round(rms)
                + "ms";
    }

    /**
     * Which way a bell leans, in words.
     *
     * @param meanError the bell's mean error in milliseconds
     * @return {@code even}, or how far early or late the bell is
     */
    public static String tendency(double meanError) {
        long size = Math.round(Math.abs(meanError));
        if (size < EVEN) {
            return "even";
        }
        return meanError < 0 ? size + "ms early" : size + "ms late";
    }

    /**
     * The per bell table.
     *
     * @param session the analysis
     * @return the heading and one line per bell
     */
    public static List<String> bellTable(StrikingSession session) {
        requireSession(session);
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("bell", "blows", "accuracy", "tendency"));
        for (Map.Entry<Integer, Summary> entry : session.byBell().entrySet()) {
            Summary summary = entry.getValue();
            rows.add(List.of(
                    String.valueOf(Bells.symbol(entry.getKey())),
                    String.valueOf(summary.blows()),
                    Math.round(summary.rms()) + "ms",
                    tendency(summary.meanError())));
        }
        return Text.tabulate(rows, List.of(Align.LEFT, Align.RIGHT, Align.RIGHT, Align.LEFT));
    }

    /**
     * The faults worth mentioning, up to the usual limit.
     *
     * @param session the analysis
     * @return one line per fault, or a line saying there were none
     */
    public static List<String> faultLines(StrikingSession session) {
        return faultLines(session, FAULT_LIMIT);
    }

    /**
     * The faults worth mentioning.
     *
     * @param session the analysis
     * @param limit the most faults to list
     * @return one line per fault listed and a line saying how many more there
     *     were, or a line saying there were none
     */
    public static List<String> faultLines(StrikingSession session, int limit) {
        requireSession(session);
        List<Fault> faults = session.faults();
        if (faults.isEmpty()) {
            return List.of("no faults worth reporting");
        }
        List<String> lines = new ArrayList<>();
        for (Fault fault : faults.subList(0, Math.min(limit, faults.size()))) {
            lines.add(Faults.describeFault(fault));
        }
        if (faults.size() > limit) {
            lines.add("and " + (faults.size() - limit) + " more");
        }
        return lines;
    }

    /**
     * The whole report: the headline, a note when the recording stopped making
     * sense, the bell table and the faults.
     *
     * @param session the analysis
     * @return the lines of the report
     */
    public static List<String> reportLines(StrikingSession session) {
        requireSession(session);
        List<String> lines = new ArrayList<>();
        lines.add(headline(session));
        session.stoppedAt().ifPresent(index -> lines.add("the recording stopped making sense at row " + (index + 1)));
        lines.add("");
        lines.addAll(bellTable(session));
        lines.add("");
        lines.addAll(faultLines(session));
        return lines;
    }

    private static void requireSession(StrikingSession session) {
        if (session == null) {
            throw new ValidationException("a StrikingSession is needed to report on it");
        }
    }
}
