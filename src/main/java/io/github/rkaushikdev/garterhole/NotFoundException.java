package io.github.rkaushikdev.garterhole;

import io.github.rkaushikdev.garterhole.util.Json;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Something that was looked up by identifier is not there.
 */
public class NotFoundException extends GarterholeException {

    private static final long serialVersionUID = 1L;

    /** The code every lookup failure carries. */
    public static final String CODE = "not_found";

    private final String kind;
    private final String id;

    /**
     * @param kind what was being looked for, for example {@code method}
     * @param id the identifier it was looked for by
     */
    public NotFoundException(String kind, String id) {
        super("no " + kind + " with id " + Json.quote(id), CODE, detailsOf(kind, id));
        this.kind = kind;
        this.id = id;
    }

    /**
     * @return what was being looked for
     */
    public String kind() {
        return kind;
    }

    /**
     * @return the identifier it was looked for by
     */
    public String id() {
        return id;
    }

    private static Map<String, Object> detailsOf(String kind, String id) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("kind", kind);
        details.put("id", id);
        return details;
    }
}
