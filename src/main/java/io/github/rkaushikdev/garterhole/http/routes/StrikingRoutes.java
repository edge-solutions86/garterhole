package io.github.rkaushikdev.garterhole.http.routes;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.http.Request;
import io.github.rkaushikdev.garterhole.http.Response;
import io.github.rkaushikdev.garterhole.http.Router;
import io.github.rkaushikdev.garterhole.striking.Strike;
import io.github.rkaushikdev.garterhole.striking.StrikingExport;
import io.github.rkaushikdev.garterhole.striking.StrikingReport;
import io.github.rkaushikdev.garterhole.striking.StrikingSession;
import io.github.rkaushikdev.garterhole.validation.Coerce;
import io.github.rkaushikdev.garterhole.validation.Schema;
import io.github.rkaushikdev.garterhole.validation.Schemas;
import io.github.rkaushikdev.garterhole.validation.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Striking analysis over HTTP.
 *
 * <p>The recorder in the tower posts what it heard and gets back the analysis.
 * Nothing is stored: a striking recording is large, it is only interesting for
 * about a week, and a tower that wants to keep one can keep the file it posted.
 *
 * <p>The lenient flag matters more than it looks. A recording with one bad row
 * in the middle is the usual case rather than the exception, and a service that
 * refuses the whole thing is a service nobody uses twice.
 */
public final class StrikingRoutes {

    private static final int DEFAULT_WINDOW_ROWS = 20;
    private static final int MOST_WINDOW_ROWS = 1000;
    private static final String CSV = "text/csv; charset=utf-8";

    /** What a recording has to say about itself. */
    private static final Schema RECORDING = Schemas.object(Fields.of(
            Map.entry("stage", Types.stage()),
            Map.entry(
                    "strikes",
                    Schemas.arrayOf(Schemas.object(Fields.of(
                                    Map.entry(
                                            "bell",
                                            Schemas.number().integer().min(1).max(16)),
                                    Map.entry("at", Schemas.number().min(0)))))
                            .min(2)
                            .max(200_000)),
            Map.entry("lenient", Schemas.optional(Schemas.object(Map.of())))));

    private StrikingRoutes() {}

    /**
     * Add the routes to a router.
     *
     * @param router the router
     * @return the router
     */
    public static Router add(Router router) {
        router.post(
                "/striking",
                (request, context) ->
                        Response.json(StrikingExport.asDocument(sessionFrom(request), windowRowsOf(request))));

        router.post(
                "/striking/report",
                (request, context) ->
                        Response.text(String.join("\n", StrikingReport.reportLines(sessionFrom(request)))));

        router.post(
                "/striking/blows.csv",
                (request, context) ->
                        Response.text(StrikingExport.blowsAsCsv(sessionFrom(request)), Map.of("content-type", CSV)));

        router.post(
                "/striking/bells.csv",
                (request, context) ->
                        Response.text(StrikingExport.bellsAsCsv(sessionFrom(request)), Map.of("content-type", CSV)));

        return router;
    }

    /**
     * Read a recording out of a request and analyse it. The analysis carries on
     * past a row that makes no sense unless the query says {@code lenient=false}.
     *
     * @param request the request
     * @return the analysis
     * @throws ValidationException when the body is not a recording
     */
    public static StrikingSession sessionFrom(Request request) {
        JsonNode body = Schemas.check(RECORDING, request.body(), "the recording");
        boolean lenient = !request.queryValue("lenient").equals(Optional.of("false"));
        List<Strike> strikes = new ArrayList<>(body.path("strikes").size());
        for (JsonNode strike : body.path("strikes")) {
            strikes.add(
                    new Strike(strike.path("bell").intValue(), strike.path("at").doubleValue()));
        }
        return StrikingSession.analyse(
                body.path("stage").intValue(),
                strikes,
                StrikingSession.Options.defaults().withLenient(lenient));
    }

    /**
     * How many rows a window covers, from the query.
     *
     * @param request the request
     * @return the rows, twenty when the query does not say
     * @throws ValidationException when the query asks for a size this will not measure
     */
    public static int windowRowsOf(Request request) {
        Optional<String> asked = request.queryValue("window");
        if (asked.isEmpty()) {
            return DEFAULT_WINDOW_ROWS;
        }
        double rows = numberOf(asked.get());
        if (rows != Math.rint(rows) || rows < 1 || rows > MOST_WINDOW_ROWS) {
            throw new ValidationException("a window of " + asked.get() + " rows is not one this will measure");
        }
        return (int) rows;
    }

    /** The number some text holds, or NaN when it holds none. */
    private static double numberOf(String text) {
        try {
            return Coerce.toNumber(text);
        } catch (ValidationException notANumber) {
            return Double.NaN;
        }
    }
}
