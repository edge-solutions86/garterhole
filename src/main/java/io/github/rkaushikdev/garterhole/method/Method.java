package io.github.rkaushikdev.garterhole.method;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.core.Change;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.core.RowBlock;
import io.github.rkaushikdev.garterhole.core.Stage;
import io.github.rkaushikdev.garterhole.notation.Expansion;
import io.github.rkaushikdev.garterhole.notation.NotationFormat;
import io.github.rkaushikdev.garterhole.util.Guards;
import io.github.rkaushikdev.garterhole.util.Text;
import java.util.List;

/**
 * A method is a name and a lead of place notation. Everything else about it,
 * from its lead head to its classification, follows from the notation, so the
 * class stores only the two facts and derives the rest on demand.
 *
 * <p>Deriving rather than storing matters more than it looks. Method libraries
 * are full of entries whose recorded lead head disagrees with their notation,
 * and a library that trusts the recorded value quietly gives wrong answers for
 * years.
 *
 * <p>Methods are immutable. Two methods are the same object only when they are
 * the same instance; {@link #ringsTheSameAs} is the question of whether two
 * methods ring the same rows.
 */
public final class Method {

    private final String name;
    private final int stage;
    private final List<Change> changes;
    private final String source;
    private final RowBlock leadFromRounds;

    /**
     * What a method is defined by: the two things a collection prints, and the
     * stage they are rung at.
     *
     * @param name the name, without the class or the stage
     * @param stage the number of bells
     * @param notation the place notation, folded or written out in full
     */
    public record Definition(String name, int stage, String notation) {}

    /**
     * @param definition the name, stage and notation
     * @throws ValidationException when there is no definition, the stage is
     *     unsupported or the notation expands to nothing
     * @throws IllegalArgumentException when the name or the notation is empty
     * @throws io.github.rkaushikdev.garterhole.NotationException when the notation cannot be read
     */
    public Method(Definition definition) {
        if (definition == null) {
            throw new ValidationException("a method needs a definition object");
        }
        String givenName = Guards.nonEmptyString(definition.name(), "a method name");
        if (!Stage.isSupported(definition.stage())) {
            throw new ValidationException("stage " + definition.stage() + " is not supported");
        }
        String notation = Guards.nonEmptyString(definition.notation(), "place notation");
        List<Change> expanded = Expansion.expandNotation(notation, definition.stage());
        if (expanded.isEmpty()) {
            throw new ValidationException("a method needs at least one change");
        }
        this.name = Text.trim(givenName);
        this.stage = definition.stage();
        this.changes = List.copyOf(expanded);
        this.source = notation;
        this.leadFromRounds = RowBlock.fromChanges(Row.rounds(stage), changes);
    }

    /**
     * Build a method from the two things a collection prints.
     *
     * @param name the name
     * @param stage the number of bells
     * @param notation the place notation
     * @return the method
     */
    public static Method of(String name, int stage, String notation) {
        return new Method(new Definition(name, stage, notation));
    }

    /**
     * @return the name, trimmed, without the class or the stage
     */
    public String name() {
        return name;
    }

    /**
     * @return the number of bells the method is rung on
     */
    public int stage() {
        return stage;
    }

    /**
     * @return the changes of one whole lead, as a list that cannot be changed
     */
    public List<Change> changes() {
        return changes;
    }

    /**
     * @return the notation as the author wrote it, kept for round tripping
     */
    public String source() {
        return source;
    }

    /**
     * @return the number of changes in a lead
     */
    public int leadLength() {
        return changes.size();
    }

    /**
     * The ringing name of the stage, so that a report can say {@code Major}
     * without looking the number up itself.
     *
     * @return the stage name
     */
    public String stageName() {
        return Stage.name(stage);
    }

    /**
     * @return the notation written in the house style, folded when it can be
     */
    public String notation() {
        return NotationFormat.writeFolded(changes);
    }

    /**
     * @return the notation written out in full, with no comma
     */
    public String fullNotation() {
        return NotationFormat.writeChanges(changes);
    }

    /**
     * @return one plain lead rung from rounds
     */
    public RowBlock plainLead() {
        return leadFromRounds;
    }

    /**
     * Ring one plain lead from a starting row.
     *
     * @param start the row the lead starts on
     * @return the lead, holding the start and every row after it
     * @throws ValidationException when the row is at another stage
     */
    public RowBlock plainLead(Row start) {
        Guards.present(start, "a lead starts from a Row");
        if (start.stage() != stage) {
            throw new ValidationException("a method on " + stage + " cannot start from a row of " + start.stage());
        }
        return start.isRounds() ? leadFromRounds : RowBlock.fromChanges(start, changes);
    }

    /**
     * @return the row a plain lead from rounds finishes on
     */
    public Row leadHead() {
        return leadFromRounds.last();
    }

    /**
     * @param start the row the lead starts on
     * @return the row a plain lead from that row finishes on
     */
    public Row leadHead(Row start) {
        return plainLead(start).last();
    }

    /**
     * @return the row before the lead head of a lead from rounds, which is where a call is made
     */
    public Row leadEnd() {
        return leadEnd(Row.rounds(stage));
    }

    /**
     * @param start the row the lead starts on
     * @return the row before the lead head, which is where a call is made
     */
    public Row leadEnd(Row start) {
        RowBlock lead = plainLead(start);
        return lead.at(lead.length() - 2);
    }

    /**
     * The change that takes the lead end to the lead head. A call replaces this
     * change with one of its own.
     *
     * @return the last change of the lead
     */
    public Change leadEndChange() {
        return changes.get(changes.size() - 1);
    }

    /**
     * @param other another method, or {@code null}
     * @return true when the two ring the same rows, whatever they are called
     */
    public boolean ringsTheSameAs(Method other) {
        if (other == null || other.stage != stage) {
            return false;
        }
        return fullNotation().equals(other.fullNotation());
    }

    /**
     * @return the name, the stage name and the notation, for example {@code Plain Bob (Minor) x16x16x16,12}
     */
    @Override
    public String toString() {
        return name + " (" + stageName() + ") " + notation();
    }
}
