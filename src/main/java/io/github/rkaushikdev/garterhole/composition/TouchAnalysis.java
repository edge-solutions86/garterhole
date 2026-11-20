package io.github.rkaushikdev.garterhole.composition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.composition.Prover.Proof;
import io.github.rkaushikdev.garterhole.composition.music.MusicScorer.MusicSummary;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.List;
import java.util.Optional;

/**
 * The report on a touch: how long it is, whether it is true, what it is worth
 * musically, and how it is built. Everything in it was found by ringing the
 * touch.
 *
 * @param title the method's title, worked out from its notation
 * @param length how many changes the touch runs to
 * @param kind what that length counts as
 * @param comesRound true when the touch finishes where it started
 * @param proof the proof
 * @param summary the proof in one line
 * @param calls how many of each call there are, plain leads left out
 * @param parts the part structure, when the calling has one
 * @param music the music in the rows rung, the closing rounds left out
 */
public record TouchAnalysis(
        String title,
        int length,
        Lengths.Kind kind,
        boolean comesRound,
        Proof proof,
        String summary,
        List<CallCount> calls,
        Optional<PartsFound> parts,
        MusicSummary music) {

    /**
     * How many times a call is made.
     *
     * @param symbol the call symbol
     * @param count how many times
     */
    public record CallCount(String symbol, int count) {}

    /**
     * The part structure of a touch, as a report gives it.
     *
     * @param parts how many parts
     * @param leadsPerPart how many leads each part has
     * @param partEnd the part end, as printed
     */
    public record PartsFound(int parts, int leadsPerPart, String partEnd) {}

    /**
     * @return the calls as a document list, each with its symbol and count
     */
    public ArrayNode callsJson() {
        ArrayNode json = Json.array();
        for (CallCount call : calls) {
            json.addObject().put("symbol", call.symbol()).put("count", call.count());
        }
        return json;
    }

    /**
     * @return the parts as a document, or a JSON null when the calling has no part structure
     */
    public JsonNode partsJson() {
        if (parts.isEmpty()) {
            return Json.mapper().nullNode();
        }
        PartsFound found = parts.get();
        return Json.object()
                .put("parts", found.parts())
                .put("leadsPerPart", found.leadsPerPart())
                .put("partEnd", found.partEnd());
    }

    /**
     * @return the whole analysis as a document, in the order the fields are listed
     */
    public ObjectNode toJson() {
        ObjectNode json = Json.object();
        json.put("title", title);
        json.put("length", length);
        json.put("kind", kind.label());
        json.put("comesRound", comesRound);
        json.set("proof", proof.toJson());
        json.put("summary", summary);
        json.set("calls", callsJson());
        json.set("parts", partsJson());
        json.set("music", music.toJson());
        return json;
    }
}
