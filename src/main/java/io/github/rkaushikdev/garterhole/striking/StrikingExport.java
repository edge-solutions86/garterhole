package io.github.rkaushikdev.garterhole.striking;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.striking.ErrorAccumulator.Summary;
import io.github.rkaushikdev.garterhole.striking.RollingWindows.Window;
import io.github.rkaushikdev.garterhole.striking.StrikingMetrics.Accuracy;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Getting a striking analysis out of the service.
 *
 * <p>A band wants the numbers in a spreadsheet so that they can draw their own
 * graph of how the practice went, and the recorder that produced the strike
 * times usually wants them back in a form it can play alongside the audio.
 * Both are the same data written differently.
 */
public final class StrikingExport {

    /** The format stamped into every document. */
    public static final String FORMAT = "garterhole.striking";

    /** The version stamped into every document, so that a reader can refuse a future one. */
    public static final int FORMAT_VERSION = 1;

    private StrikingExport() {}

    /**
     * Every blow, as rows for a spreadsheet, with the times rounded to the
     * millisecond so that a spreadsheet is readable.
     *
     * @param session the analysis
     * @return the CSV text, a header row first and no newline at the end
     */
    public static String blowsAsCsv(StrikingSession session) {
        requireSession(session);
        List<String> rows = new ArrayList<>();
        rows.add("row,place,bell,at,expected,error");
        for (BlowError entry : session.errors()) {
            rows.add(csv(
                    entry.rowIndex(),
                    entry.place(),
                    entry.bell(),
                    Json.number(entry.at()),
                    Math.round(entry.expected()),
                    Math.round(entry.error())));
        }
        return String.join("\n", rows);
    }

    /**
     * The per bell summary, as rows for a spreadsheet.
     *
     * @param session the analysis
     * @return the CSV text, a header row first and no newline at the end
     */
    public static String bellsAsCsv(StrikingSession session) {
        requireSession(session);
        List<String> rows = new ArrayList<>();
        rows.add("bell,blows,rms,meanError");
        for (Map.Entry<Integer, Summary> entry : session.byBell().entrySet()) {
            Summary summary = entry.getValue();
            rows.add(csv(
                    entry.getKey(),
                    summary.blows(),
                    Json.number(round(summary.rms())),
                    Json.number(round(summary.meanError()))));
        }
        return String.join("\n", rows);
    }

    /**
     * The whole analysis as a document something else can read, with rolling
     * windows of twenty rows.
     *
     * @param session the analysis
     * @return the document
     */
    public static ObjectNode asDocument(StrikingSession session) {
        return asDocument(session, RollingWindows.DEFAULT_ROWS);
    }

    /**
     * The whole analysis as a document something else can read. Every measured
     * number is rounded to a tenth of a millisecond.
     *
     * @param session the analysis
     * @param windowRows how many rows each rolling window covers
     * @return the document
     */
    public static ObjectNode asDocument(StrikingSession session, int windowRows) {
        requireSession(session);
        Accuracy summary = session.accuracy();
        ObjectNode document = Json.object();
        document.put("format", FORMAT);
        document.put("version", FORMAT_VERSION);
        document.put("stage", session.stream().stage());
        document.put("rows", session.rowCount());
        ObjectNode model = document.putObject("model");
        putNumber(model, "interval", round(session.model().interval()));
        putNumber(model, "handstrokeGap", round(session.model().handstrokeGap()));
        putNumber(model, "pealMinutes", round(session.model().pealMinutes()));
        ObjectNode accuracy = document.putObject("accuracy");
        accuracy.put("blows", summary.blows());
        putNumber(accuracy, "rms", round(summary.rms()));
        putNumber(accuracy, "meanError", round(summary.meanError()));
        ArrayNode bells = document.putArray("bells");
        for (Map.Entry<Integer, Summary> entry : session.byBell().entrySet()) {
            ObjectNode bell = bells.addObject();
            bell.put("bell", entry.getKey());
            bell.put("blows", entry.getValue().blows());
            putNumber(bell, "rms", round(entry.getValue().rms()));
            putNumber(bell, "meanError", round(entry.getValue().meanError()));
        }
        ArrayNode windows = document.putArray("windows");
        for (Window window : RollingWindows.rollingAccuracy(session.errors(), windowRows)) {
            ObjectNode written = windows.addObject();
            written.put("from", window.from());
            written.put("to", window.to());
            putNumber(written, "rms", round(window.rms()));
        }
        ArrayNode faults = document.putArray("faults");
        session.faults().forEach(fault -> faults.add(Faults.describeFault(fault)));
        if (session.stoppedAt().isPresent()) {
            document.put("stoppedAt", session.stoppedAt().getAsInt());
        } else {
            document.putNull("stoppedAt");
        }
        return document;
    }

    /**
     * The document as JSON, indented by two spaces.
     *
     * @param session the analysis
     * @return the JSON text
     */
    public static String asJson(StrikingSession session) {
        return asJson(session, RollingWindows.DEFAULT_ROWS);
    }

    /**
     * The document as JSON, indented by two spaces.
     *
     * @param session the analysis
     * @param windowRows how many rows each rolling window covers
     * @return the JSON text
     */
    public static String asJson(StrikingSession session, int windowRows) {
        return Json.pretty(asDocument(session, windowRows));
    }

    /** Rounded to a tenth, with halves going up, the way the reports round. */
    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    /**
     * Puts a number the way a JavaScript runtime would write it: a whole number
     * goes in as a whole number, so that it is written {@code 250} rather than
     * {@code 250.0}.
     */
    private static void putNumber(ObjectNode node, String name, double value) {
        if (Double.isFinite(value) && value == Math.rint(value) && Math.abs(value) < Long.MAX_VALUE) {
            node.put(name, (long) value);
        } else {
            node.put(name, value);
        }
    }

    private static String csv(Object... cells) {
        return Stream.of(cells).map(String::valueOf).collect(Collectors.joining(","));
    }

    private static void requireSession(StrikingSession session) {
        if (session == null) {
            throw new ValidationException("a StrikingSession is needed to export it");
        }
    }
}
