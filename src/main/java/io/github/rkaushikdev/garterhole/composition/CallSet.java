package io.github.rkaushikdev.garterhole.composition;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.core.Bells;
import io.github.rkaushikdev.garterhole.method.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The calls a band expects to be given for a method, without anybody having to
 * write them down.
 *
 * <p>A call set is an ordered map from a call's role to the call: {@code plain},
 * {@code bob} and {@code single} for the standard sets, and whatever names a
 * tower gives its own. Nearly every treble dominated method takes the same two
 * calls: a bob that makes fourths and a single that makes fourths with a pair
 * of places under it. Methods with two hunt bells, which is the Grandsire
 * family, take a different pair, and those two families cover almost
 * everything a tower rings.
 */
public final class CallSet {

    /** The registry built once and reused, so that an entry a tower registers once affects every later lookup. */
    private static final AtomicReference<CallRegistry> SHARED = new AtomicReference<>();

    private CallSet() {}

    /**
     * The bob and single used with a method that has one hunt bell.
     *
     * @param stage the stage, five or more
     * @return the {@code bob} and the {@code single}
     * @throws ValidationException below five bells
     */
    public static Map<String, Call> fourthsPlaceCalls(int stage) {
        if (stage < 5) {
            throw new ValidationException("fourths place calls need at least five bells, not " + stage);
        }
        return pair(Call.of("Bob", "-", stage, "14"), Call.of("Single", "s", stage, "1234"));
    }

    /**
     * The bob and single used with the Grandsire family, which has two hunt
     * bells. The bob makes thirds; the single makes thirds and holds the two
     * bells under it as well.
     *
     * @param stage the stage, five or more
     * @return the {@code bob} and the {@code single}
     * @throws ValidationException below five bells
     */
    public static Map<String, Call> thirdsPlaceCalls(int stage) {
        if (stage < 5) {
            throw new ValidationException("thirds place calls need at least five bells, not " + stage);
        }
        return pair(Call.of("Bob", "-", stage, "3"), Call.of("Single", "s", stage, "123"));
    }

    /**
     * The calls that go with a method, as the shared registry chooses them.
     *
     * @param method the method
     * @return the calls, the plain lead first
     */
    public static Map<String, Call> standardCalls(Method method) {
        return sharedRegistry().callsFor(method);
    }

    /**
     * The calls that go with a method, as a given registry chooses them.
     *
     * @param method the method
     * @param registry where a tower keeps its own families
     * @return the calls, the plain lead first
     */
    public static Map<String, Call> standardCalls(Method method, CallRegistry registry) {
        return registry.callsFor(method);
    }

    /**
     * @return the registry built once and reused
     */
    public static CallRegistry sharedRegistry() {
        CallRegistry existing = SHARED.get();
        if (existing != null) {
            return existing;
        }
        CallRegistry built = CallRegistry.defaultRegistry();
        return SHARED.compareAndSet(null, built) ? built : SHARED.get();
    }

    /**
     * Throw the shared registry away, which the tests need and a long running
     * process reloading its configuration wants.
     */
    public static void resetRegistry() {
        SHARED.set(null);
    }

    /**
     * A bob that makes a place, built for the methods that do not take either
     * standard pair. The place is written with the bell alphabet so that a
     * caller can read the notation back.
     *
     * @param stage the stage
     * @param place the place the bob makes
     * @return a call named for its place, for example {@code 6ths place bob}, written {@code -}
     */
    public static Call placeCall(int stage, int place) {
        return placeCall(stage, place, Bells.symbol(place) + "ths place bob", "-");
    }

    /**
     * A call that makes a place, with a name and symbol of its own.
     *
     * @param stage the stage
     * @param place the place the call makes
     * @param name what the call is called
     * @param symbol how it is written in a touch
     * @return the call
     */
    public static Call placeCall(int stage, int place, String name, String symbol) {
        return Call.of(name, symbol, stage, "1" + Bells.symbol(place));
    }

    /**
     * @param calls a call set
     * @param symbol a call symbol
     * @return the call written that way, when there is one
     */
    public static Optional<Call> callBySymbol(Map<String, Call> calls, String symbol) {
        return calls.values().stream()
                .filter(candidate -> candidate.symbol().equals(symbol))
                .findFirst();
    }

    /**
     * @param calls a call set
     * @return every symbol in it, which is what the touch reader needs
     */
    public static List<String> symbolsOf(Map<String, Call> calls) {
        return calls.values().stream().map(Call::symbol).toList();
    }

    private static Map<String, Call> pair(Call bob, Call single) {
        Map<String, Call> calls = new LinkedHashMap<>();
        calls.put("bob", bob);
        calls.put("single", single);
        return Collections.unmodifiableMap(calls);
    }
}
