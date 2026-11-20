package io.github.rkaushikdev.garterhole.method;

import io.github.rkaushikdev.garterhole.ConflictException;
import io.github.rkaushikdev.garterhole.NotFoundException;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A collection of methods, held in memory and keyed by title.
 *
 * <p>The library refuses two methods with the same title, and it refuses two
 * methods that ring the same rows under different titles. The second check is
 * the one that matters: a tower library that quietly holds Cambridge twice
 * under two spellings will produce a splicing table that cannot be rung.
 *
 * <p>Iterating a library gives its methods in title order. A library is not
 * safe to change from two threads at once.
 */
public final class MethodLibrary implements Iterable<Method> {

    private final Map<String, Method> byTitle = new HashMap<>();
    private final Map<String, String> titleByNotation = new HashMap<>();

    /** An empty library. */
    public MethodLibrary() {
        // Methods are added one at a time, each checked against what is already held.
    }

    /**
     * @return how many methods the library holds
     */
    public int size() {
        return byTitle.size();
    }

    /**
     * Add a method. The title is worked out from the method rather than
     * supplied, so a caller cannot file Cambridge under the wrong class.
     *
     * @param method the method
     * @return the title it was filed under
     * @throws ValidationException when there is no method
     * @throws ConflictException when the title is taken, or another title rings the same rows
     */
    public String add(Method method) {
        if (method == null) {
            throw new ValidationException("only a Method can go into the library");
        }
        String title = Title.of(method);
        if (byTitle.containsKey(title)) {
            throw new ConflictException("the library already holds " + Json.quote(title), Map.of("title", title));
        }
        String key = notationKey(method);
        String existing = titleByNotation.get(key);
        if (existing != null) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("title", title);
            details.put("existing", existing);
            throw new ConflictException(Json.quote(title) + " rings the same rows as " + Json.quote(existing), details);
        }
        byTitle.put(title, method);
        titleByNotation.put(key, title);
        return title;
    }

    /**
     * Add several methods, stopping at the first one that clashes. The ones
     * added before the clash stay added.
     *
     * @param methods the methods
     * @return the titles added
     */
    public List<String> addAll(Iterable<Method> methods) {
        List<String> titles = new ArrayList<>();
        for (Method method : methods) {
            titles.add(add(method));
        }
        return titles;
    }

    /**
     * @param title a title
     * @return true when the library holds a method with that title
     */
    public boolean has(String title) {
        return byTitle.containsKey(title);
    }

    /**
     * @param title a title
     * @return the method filed under it
     * @throws NotFoundException when there is none
     */
    public Method get(String title) {
        Method method = byTitle.get(title);
        if (method == null) {
            throw new NotFoundException("method", title);
        }
        return method;
    }

    /**
     * The method with a title, if there is one. Used where a miss is ordinary
     * rather than exceptional, such as resolving the methods named in a
     * composition.
     *
     * @param title a title
     * @return the method, when the library holds it
     */
    public Optional<Method> find(String title) {
        return Optional.ofNullable(byTitle.get(title));
    }

    /**
     * @param method a method
     * @return the method that rings the same rows, whatever it is called here
     */
    public Optional<Method> findByNotation(Method method) {
        return Optional.ofNullable(titleByNotation.get(notationKey(method))).map(byTitle::get);
    }

    /**
     * Take a method out, freeing both its title and its rows.
     *
     * @param title the title
     * @return the method that was removed
     * @throws NotFoundException when the library does not hold the title
     */
    public Method remove(String title) {
        Method method = get(title);
        byTitle.remove(title);
        titleByNotation.remove(notationKey(method));
        return method;
    }

    /**
     * @return every title, sorted
     */
    public List<String> titles() {
        return byTitle.keySet().stream().sorted().toList();
    }

    /**
     * @return every method, in title order
     */
    public List<Method> methods() {
        return titles().stream().map(byTitle::get).toList();
    }

    @Override
    public Iterator<Method> iterator() {
        return methods().iterator();
    }

    /**
     * @param title a title
     * @return a short identifier for the title, safe in a URL
     */
    public static String slugFor(String title) {
        return Text.slugify(title);
    }

    /**
     * Two methods ring the same rows when they are on the same stage and have
     * the same changes written out in full.
     */
    private static String notationKey(Method method) {
        return method.stage() + ":" + method.fullNotation();
    }
}
