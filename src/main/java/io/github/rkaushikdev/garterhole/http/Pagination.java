package io.github.rkaushikdev.garterhole.http;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.validation.Coerce;
import java.util.List;
import java.util.Optional;

/**
 * Handing back a long list a page at a time.
 *
 * <p>Every listing route grew the same three lines: read a limit, read an
 * offset, slice. They had drifted apart by the time there were three of them,
 * so one of them capped the limit and the others did not, which is the sort of
 * difference nobody notices until a client asks for a million records.
 *
 * <p>The page is described in the answer rather than in a header, because a
 * secretary reading the JSON in a browser can see it there.
 */
public final class Pagination {

    /** The page size used when the caller does not ask for one. */
    public static final int DEFAULT_LIMIT = 50;

    /** The largest page anybody may ask for. */
    public static final int MAX_LIMIT = 500;

    private Pagination() {}

    /**
     * The page a request asks for.
     *
     * @param limit how many entries the page holds at most
     * @param offset how many entries come before it
     */
    public record Page(long limit, long offset) {}

    /**
     * Read the page a request is asking for, with the usual bounds.
     *
     * @param request the request
     * @return the page
     * @throws ValidationException when the limit or the offset is not one
     */
    public static Page pageOf(Request request) {
        return pageOf(request, DEFAULT_LIMIT, MAX_LIMIT);
    }

    /**
     * Read the page a request is asking for.
     *
     * @param request the request
     * @param defaultLimit the page size when none is asked for
     * @param maxLimit the largest page that may be asked for
     * @return the page
     * @throws ValidationException when the limit or the offset is not one
     */
    public static Page pageOf(Request request, int defaultLimit, int maxLimit) {
        Optional<String> limitText = request.queryValue("limit");
        Optional<String> offsetText = request.queryValue("offset");
        long limit = limitText.isEmpty() ? defaultLimit : Coerce.toInteger(limitText.get(), "limit");
        long offset = offsetText.isEmpty() ? 0 : Coerce.toInteger(offsetText.get(), "offset");
        if (limit < 1) {
            throw new ValidationException("a limit of " + limit + " would return nothing");
        }
        if (limit > maxLimit) {
            throw new ValidationException("a limit of " + limit + " is more than the " + maxLimit + " allowed");
        }
        if (offset < 0) {
            throw new ValidationException("an offset of " + offset + " is before the start of the list");
        }
        return new Page(limit, offset);
    }

    /**
     * Cut a page out of a list and describe it.
     *
     * @param everything the whole list
     * @param page the page
     * @param name what the list is called in the answer
     * @return the entries on the page, how many there are in all, the page
     *     asked for, how many were returned and whether there are more
     */
    public static ObjectNode pageOut(List<?> everything, Page page, String name) {
        int from = (int) Math.min(page.offset(), everything.size());
        int to = (int) Math.min(page.offset() + page.limit(), everything.size());
        List<?> items = everything.subList(from, Math.max(from, to));
        ArrayNode entries = Json.array();
        items.forEach(item -> entries.add(Json.toNode(item)));
        ObjectNode answer = Json.object();
        answer.set(name, entries);
        answer.put("total", everything.size());
        answer.put("limit", page.limit());
        answer.put("offset", page.offset());
        answer.put("returned", items.size());
        answer.put("more", page.offset() + items.size() < everything.size());
        return answer;
    }

    /**
     * Read the page and cut it out in one step, which is what a listing route
     * actually wants.
     *
     * @param request the request
     * @param everything the whole list
     * @param name what the list is called in the answer
     * @return the page, described
     * @throws ValidationException when the limit or the offset is not one
     */
    public static ObjectNode paginate(Request request, List<?> everything, String name) {
        return pageOut(everything, pageOf(request), name);
    }

    /**
     * Read the page and cut it out in one step, with bounds of the caller's own.
     *
     * @param request the request
     * @param everything the whole list
     * @param name what the list is called in the answer
     * @param defaultLimit the page size when none is asked for
     * @param maxLimit the largest page that may be asked for
     * @return the page, described
     * @throws ValidationException when the limit or the offset is not one
     */
    public static ObjectNode paginate(
            Request request, List<?> everything, String name, int defaultLimit, int maxLimit) {
        return pageOut(everything, pageOf(request, defaultLimit, maxLimit), name);
    }
}
