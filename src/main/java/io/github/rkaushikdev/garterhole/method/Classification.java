package io.github.rkaushikdev.garterhole.method;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.List;
import java.util.Optional;

/**
 * The full classification of a method, with the parts that went into the
 * decision.
 *
 * @param className the class name with its prefixes, for example {@code Little Bob}
 * @param base the class name without the Little and Differential prefixes
 * @param little true when the hunt bell never reaches the back
 * @param differential true when the working bells do not all ring the same line
 * @param huntBells the hunt bells, ascending
 * @param huntPath the shape of the first hunt bell's path, absent for a principle
 */
public record Classification(
        String className,
        String base,
        boolean little,
        boolean differential,
        List<Integer> huntBells,
        Optional<HuntPath> huntPath) {

    /**
     * @param className the class name with its prefixes
     * @param base the class name without the prefixes
     * @param little true for a little method
     * @param differential true for a differential method
     * @param huntBells the hunt bells, ascending
     * @param huntPath the shape of the first hunt bell's path, absent for a principle
     */
    public Classification {
        huntBells = List.copyOf(huntBells);
    }

    /**
     * The classification as a document, with the hunt path written as its name,
     * or as {@code null} for a principle.
     *
     * @return {@code className}, {@code base}, {@code little}, {@code differential},
     *     {@code huntBells} and {@code huntPath}, in that order
     */
    public ObjectNode toJson() {
        ObjectNode json = Json.object();
        json.put("className", className);
        json.put("base", base);
        json.put("little", little);
        json.put("differential", differential);
        ArrayNode bells = json.putArray("huntBells");
        huntBells.forEach(bells::add);
        if (huntPath.isPresent()) {
            json.put("huntPath", huntPath.get().label());
        } else {
            json.putNull("huntPath");
        }
        return json;
    }
}
