package io.github.rkaushikdev.garterhole.cli.commands;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.tower.Bell;
import io.github.rkaushikdev.garterhole.tower.Tower;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.ArrayList;
import java.util.List;

/**
 * Building a tower from the document it is filed as.
 *
 * <p>A tower is stored as the document that came in rather than as the class,
 * which keeps the stored shape stable while the class changes. The class is
 * built from the document to check it, so that a tower with a gap in its ring is
 * refused rather than stored, and to answer the questions that need it.
 */
final class TowerDocuments {

    private TowerDocuments() {}

    /**
     * @param document a tower document: a name, an optional place and its bells,
     *     each with a number and a board weight or a weight in pounds
     * @return the tower
     * @throws ValidationException when a bell cannot be built, the ring has a gap,
     *     or the document's id disagrees with the one its name gives
     */
    static Tower towerFrom(JsonNode document) {
        List<Bell> bells = new ArrayList<>();
        for (JsonNode entry : document.path("bells")) {
            bells.add(bellFrom(entry));
        }
        JsonNode place = document.path("place");
        Tower tower = new Tower(document.path("name").textValue(), place.isTextual() ? place.textValue() : null, bells);
        JsonNode id = document.path("id");
        if (!Json.isUndefined(id) && !(id.isTextual() && id.textValue().equals(tower.id()))) {
            throw new ValidationException(
                    "the tower says its id is " + Json.stringify(id) + " but its name gives " + Json.quote(tower.id()));
        }
        return tower;
    }

    private static Bell bellFrom(JsonNode entry) {
        int number = entry.path("number").asInt();
        Bell.Details details = new Bell.Details(
                textOrNull(entry.path("note")),
                textOrNull(entry.path("founder")),
                entry.path("cast").isNumber() ? entry.path("cast").asInt() : null);
        JsonNode weight = entry.path("weight");
        if (!Json.isUndefined(weight)) {
            return Bell.weighing(number, weight.textValue(), details);
        }
        JsonNode weightPounds = entry.path("weightPounds");
        if (Json.isUndefined(weightPounds)) {
            throw new ValidationException(
                    "bell " + Json.templateText(entry.path("number")) + " does not say what it weighs");
        }
        return new Bell(number, weightPounds.asDouble(), details);
    }

    private static String textOrNull(JsonNode value) {
        return value.isTextual() ? value.textValue() : null;
    }
}
