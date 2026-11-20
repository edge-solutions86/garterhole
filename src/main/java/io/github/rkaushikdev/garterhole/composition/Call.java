package io.github.rkaushikdev.garterhole.composition;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.core.Change;
import io.github.rkaushikdev.garterhole.method.Method;
import io.github.rkaushikdev.garterhole.notation.NotationFormat;
import io.github.rkaushikdev.garterhole.notation.NotationParser;
import io.github.rkaushikdev.garterhole.util.Guards;
import io.github.rkaushikdev.garterhole.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * A call is an instruction from the conductor that swaps one or more changes at
 * the end of a lead for different ones. A bob in Plain Bob Major replaces the
 * {@code 12} at the lead end with {@code 14}; a single replaces it with
 * {@code 1234}.
 *
 * <p>The call knows only the changes it substitutes and how many it replaces.
 * What that does to the bells depends on the method, which is why the call
 * itself carries no lead head or calling position.
 *
 * <p>The plain lead is modelled as a call too, {@link #plain}, so that a touch
 * can be written as a run of calls without a special case for the leads where
 * nothing is called.
 */
public final class Call {

    private final String name;
    private final String symbol;
    private final int stage;
    private final List<Change> changes;
    private final int replaces;
    private final boolean plain;

    /**
     * What a call is defined by.
     *
     * @param name what the call is called, for example {@code Bob}
     * @param symbol how it is written in a touch, for example {@code -}
     * @param stage the stage it is made at
     * @param notation the changes it substitutes, as place notation with no fold
     * @param replaces how many changes at the end of the lead it replaces;
     *     when not given, as many as it substitutes
     */
    public record Definition(String name, String symbol, int stage, String notation, OptionalInt replaces) {

        /**
         * A call that replaces as many changes as it substitutes.
         *
         * @param name what the call is called
         * @param symbol how it is written in a touch
         * @param stage the stage it is made at
         * @param notation the changes it substitutes
         */
        public Definition(String name, String symbol, int stage, String notation) {
            this(name, symbol, stage, notation, OptionalInt.empty());
        }
    }

    /**
     * @param definition the name, symbol, stage, notation and replaced count
     * @throws ValidationException when there is no definition, the call
     *     substitutes nothing, or the replaced count is not positive
     * @throws IllegalArgumentException when the name, symbol or notation is empty
     */
    public Call(Definition definition) {
        this(definition, false);
    }

    private Call(Definition definition, boolean plain) {
        if (definition == null) {
            throw new ValidationException("a call needs a definition object");
        }
        Guards.nonEmptyString(definition.name(), "a call name");
        Guards.nonEmptyString(definition.symbol(), "a call symbol");
        Guards.nonEmptyString(definition.notation(), "a call notation");
        List<Change> parsed = NotationParser.parseChanges(definition.notation(), definition.stage());
        if (parsed.isEmpty()) {
            throw new ValidationException("a call must substitute at least one change");
        }
        int replacedCount = definition.replaces().orElse(parsed.size());
        if (replacedCount < 1) {
            throw new ValidationException("a call cannot replace " + replacedCount + " changes");
        }
        this.name = Text.trim(definition.name());
        this.symbol = Text.trim(definition.symbol());
        this.stage = definition.stage();
        this.changes = List.copyOf(parsed);
        this.replaces = replacedCount;
        this.plain = plain;
    }

    /**
     * Build a call from the pieces a composition file gives, replacing as many
     * changes as it substitutes.
     *
     * @param name what the call is called
     * @param symbol how it is written in a touch
     * @param stage the stage it is made at
     * @param notation the changes it substitutes
     * @return the call
     */
    public static Call of(String name, String symbol, int stage, String notation) {
        return new Call(new Definition(name, symbol, stage, notation));
    }

    /**
     * Build a call that replaces a stated number of changes.
     *
     * @param name what the call is called
     * @param symbol how it is written in a touch
     * @param stage the stage it is made at
     * @param notation the changes it substitutes
     * @param replaces how many changes at the end of the lead it replaces
     * @return the call
     */
    public static Call of(String name, String symbol, int stage, String notation, int replaces) {
        return new Call(new Definition(name, symbol, stage, notation, OptionalInt.of(replaces)));
    }

    /**
     * The call that changes nothing, which is what a plain lead is.
     *
     * @param stage the stage
     * @return the plain call, written {@code .}
     */
    public static Call plain(int stage) {
        return new Call(new Definition("Plain", TouchParser.PLAIN_SYMBOL, stage, "1", OptionalInt.of(1)), true);
    }

    /**
     * @return what the call is called
     */
    public String name() {
        return name;
    }

    /**
     * @return how the call is written in a touch
     */
    public String symbol() {
        return symbol;
    }

    /**
     * @return the stage the call is made at
     */
    public int stage() {
        return stage;
    }

    /**
     * @return the changes the call substitutes
     */
    public List<Change> changes() {
        return changes;
    }

    /**
     * @return how many changes at the end of the lead the call replaces
     */
    public int replaces() {
        return replaces;
    }

    /**
     * @return true when the call leaves the lead exactly as it was
     */
    public boolean isPlain() {
        return plain;
    }

    /**
     * The changes of a lead of the method with this call made at the end of it.
     * The plain call gives the lead as it stands.
     *
     * @param method the method
     * @return the changes of the called lead
     * @throws ValidationException when there is no method, it is at another
     *     stage, or its lead is shorter than the changes replaced
     */
    public List<Change> applyToLead(Method method) {
        if (method == null) {
            throw new ValidationException("a call is applied to a Method");
        }
        if (plain) {
            return method.changes();
        }
        if (method.stage() != stage) {
            throw new ValidationException("a call on " + stage + " cannot be made in a method on " + method.stage());
        }
        if (replaces > method.leadLength()) {
            throw new ValidationException(
                    "a call replacing " + replaces + " changes cannot be made in a lead of " + method.leadLength());
        }
        List<Change> called = new ArrayList<>(method.changes().subList(0, method.leadLength() - replaces));
        called.addAll(changes);
        return List.copyOf(called);
    }

    /**
     * @return the notation the call substitutes
     */
    public String notation() {
        return NotationFormat.writeChanges(changes);
    }

    /**
     * @return the name, the symbol and the notation, for example {@code Bob (-) 14}
     */
    @Override
    public String toString() {
        return name + " (" + symbol + ") " + notation();
    }
}
