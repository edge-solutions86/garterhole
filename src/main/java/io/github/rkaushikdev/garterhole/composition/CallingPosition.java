package io.github.rkaushikdev.garterhole.composition;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.core.Stage;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Calling positions. A conductor says "wrong" rather than "a bob at the end of
 * the fourth lead", because the position tells the band where the observation
 * bell is and the lead number does not.
 *
 * <p>The convention here is the usual one: the position is named by the place
 * the observation bell occupies in the lead head that follows the call. Home is
 * at the back, Wrong two places under it, and Middle two under that. The front
 * three places are Before, In and Out.
 *
 * <p>Fourths and Middle land on the same place at Major, and Fifths and Wrong do
 * the same at Minor. Where two names collide the one nearer the back wins,
 * which is what a band would say out loud.
 */
public final class CallingPosition {

    private static final Map<String, String> NAMES = Map.of(
            "B", "Before",
            "I", "In",
            "O", "Out",
            "F", "Fourths",
            "V", "Fifths",
            "M", "Middle",
            "W", "Wrong",
            "H", "Home");

    /** A place written as a number, the way an unnamed position is written. */
    private static final Pattern NUMBER = Pattern.compile("\\s*\\d+(\\.0*)?\\s*");

    private CallingPosition() {}

    /**
     * A named position, as a list of them shows it.
     *
     * @param code the one letter code, for example {@code W}
     * @param place the place it sits at
     * @param name the long name, for example {@code Wrong}
     */
    public record NamedPosition(String code, int place, String name) {}

    /**
     * The place each named position sits at, for a stage. Positions that fall
     * outside the stage are left out.
     *
     * @param stage a supported stage
     * @return each code against its place, from Before to Home
     * @throws ValidationException when the stage is not supported
     */
    public static Map<String, Integer> positionPlaces(int stage) {
        requireStage(stage);
        Map<String, Integer> wanted = new LinkedHashMap<>();
        wanted.put("B", 1);
        wanted.put("I", 2);
        wanted.put("O", 3);
        wanted.put("F", 4);
        wanted.put("V", 5);
        wanted.put("M", stage - 4);
        wanted.put("W", stage - 2);
        wanted.put("H", stage);
        Map<String, Integer> places = new LinkedHashMap<>();
        wanted.forEach((code, place) -> {
            if (place >= 1 && place <= stage) {
                places.put(code, place);
            }
        });
        return Collections.unmodifiableMap(places);
    }

    /**
     * The code for each place, with the collisions resolved in favour of the
     * name nearer the back.
     *
     * @param stage a supported stage
     * @return each place against its code
     */
    public static Map<Integer, String> placeCodes(int stage) {
        Map<Integer, String> codes = new LinkedHashMap<>();
        positionPlaces(stage).forEach((code, place) -> codes.put(place, code));
        return Collections.unmodifiableMap(codes);
    }

    /**
     * @param stage a supported stage
     * @param place a place in the row
     * @return the position code for the place, or the place written as a number when it has no name
     * @throws ValidationException when the place is outside the stage
     */
    public static String positionAt(int stage, int place) {
        requireStage(stage);
        if (place < 1 || place > stage) {
            throw new ValidationException("place " + place + " is outside 1.." + stage);
        }
        String code = placeCodes(stage).get(place);
        return code == null ? Integer.toString(place) : code;
    }

    /**
     * The place a named position sits at.
     *
     * @param stage a supported stage
     * @param code a position code, or a place written as a number
     * @return the place
     * @throws ValidationException when the code is neither
     */
    public static int placeForPosition(int stage, String code) {
        Integer place = positionPlaces(stage).get(code);
        if (place != null) {
            return place;
        }
        if (code != null && NUMBER.matcher(code).matches()) {
            double number = Double.parseDouble(code.strip());
            if (number >= 1 && number <= stage) {
                return (int) number;
            }
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("stage", stage);
        details.put("code", code);
        throw new ValidationException(Json.quote(code) + " is not a calling position on " + stage, details);
    }

    /**
     * @param code a position code
     * @return the long name of the code, or the code itself when it has none
     */
    public static String positionName(String code) {
        return NAMES.getOrDefault(code, code);
    }

    /**
     * @param stage a supported stage
     * @return every position that has a name at the stage, from the front to the back
     */
    public static List<NamedPosition> namedPositions(int stage) {
        return placeCodes(stage).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new NamedPosition(entry.getValue(), entry.getKey(), positionName(entry.getValue())))
                .toList();
    }

    /**
     * The position a call was made at, worked out from where the observation
     * bell ended up.
     *
     * @param leadHead the row after the call
     * @param observation the bell being watched, usually the tenor
     * @return the position code
     */
    public static String positionOfCall(Row leadHead, int observation) {
        return positionAt(leadHead.stage(), leadHead.placeOf(observation));
    }

    private static void requireStage(int stage) {
        if (!Stage.isSupported(stage)) {
            throw new ValidationException("stage " + stage + " is not supported");
        }
    }
}
