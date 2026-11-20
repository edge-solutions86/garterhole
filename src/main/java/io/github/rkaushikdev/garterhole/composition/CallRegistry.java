package io.github.rkaushikdev.garterhole.composition;

import io.github.rkaushikdev.garterhole.NotFoundException;
import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.method.HuntBells;
import io.github.rkaushikdev.garterhole.method.Method;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Which calls go with which method.
 *
 * <p>This started as a pair of branches inside the call set, which was fine
 * while there were two families. It is a registry now because a tower that
 * rings anything unusual needs to add its own entry without editing the
 * library, and because the order the entries are tried in has to be visible
 * rather than buried in an if.
 *
 * <p>Entries are tried in the order they were registered, most recent first,
 * and the first one whose test passes wins, so a tower's own entry beats the
 * built in ones. A registry is safe to read while another thread registers.
 */
public final class CallRegistry {

    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    /** An empty registry, which finds calls for nothing. */
    public CallRegistry() {
        // Entries are added with register.
    }

    private record Entry(String name, Predicate<Method> test, Function<Method, Map<String, Call>> build) {}

    /**
     * The registry the library uses when nobody supplies one: the thirds place
     * calls for a method with two or more hunt bells, which is the Grandsire
     * family, and the fourths place calls for everything else.
     *
     * @return a new registry with the two built in entries
     */
    public static CallRegistry defaultRegistry() {
        return new CallRegistry()
                .register("fourths place", method -> true, method -> CallSet.fourthsPlaceCalls(method.stage()))
                .register(
                        "thirds place",
                        method -> HuntBells.huntBells(method).size() >= 2,
                        method -> CallSet.thirdsPlaceCalls(method.stage()));
    }

    /**
     * Add an entry at the front, so that it is tried before everything already
     * registered.
     *
     * @param name what the entry is called
     * @param test whether the entry applies to a method
     * @param build the calls for a method the entry applies to, by role
     * @return this registry, so that registrations can be chained
     * @throws ValidationException when the name is empty or the test or builder is missing
     */
    public CallRegistry register(String name, Predicate<Method> test, Function<Method, Map<String, Call>> build) {
        if (name == null || name.isEmpty()) {
            throw new ValidationException("a registry entry needs a name");
        }
        if (test == null || build == null) {
            throw new ValidationException("the entry " + Json.quote(name) + " needs a test and a builder");
        }
        entries.add(0, new Entry(name, test, build));
        return this;
    }

    /**
     * The name of the entry that would be used for a method.
     *
     * @param method the method
     * @return the entry's name
     * @throws ValidationException when there is no method
     * @throws NotFoundException when no entry applies
     */
    public String entryFor(Method method) {
        return find(method).name();
    }

    /**
     * The calls for a method, the plain lead first and then whatever the entry
     * builds.
     *
     * @param method the method
     * @return the calls, by role, for example {@code plain}, {@code bob}, {@code single}
     */
    public Map<String, Call> callsFor(Method method) {
        Entry entry = find(method);
        Map<String, Call> calls = new LinkedHashMap<>();
        calls.put("plain", Call.plain(method.stage()));
        calls.putAll(entry.build().apply(method));
        return Collections.unmodifiableMap(calls);
    }

    /**
     * @return every entry name, in the order they are tried
     */
    public List<String> names() {
        return entries.stream().map(Entry::name).toList();
    }

    private Entry find(Method method) {
        if (method == null) {
            throw new ValidationException("a Method is needed to choose its calls");
        }
        for (Entry entry : entries) {
            if (entry.test().test(method)) {
                return entry;
            }
        }
        throw new NotFoundException("call set", method.name());
    }
}
