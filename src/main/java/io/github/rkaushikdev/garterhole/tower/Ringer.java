package io.github.rkaushikdev.garterhole.tower;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Guards;
import io.github.rkaushikdev.garterhole.util.Json;
import io.github.rkaushikdev.garterhole.util.Text;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

/**
 * A ringer.
 *
 * <p>The record holds only what a practice planner needs to decide who can
 * ring what: the name, how heavy a bell they can handle, and whether they
 * conduct. What they can actually ring is a separate thing, kept in a
 * {@link CompetencyRegister}, because it changes week by week while the rest
 * of this does not.
 */
public final class Ringer {

    /** The things a ringer can be asked to do besides ring. */
    public enum Role {
        /** Calls the touches. */
        CONDUCTOR,
        /** Teaches learners. */
        TEACHER,
        /** Looks after the bells and the frames. */
        STEEPLE_KEEPER,
        /** Runs the band. */
        TOWER_CAPTAIN;

        /**
         * @return the role the way a tower writes it, for example {@code steeple keeper}
         */
        public String label() {
            return name().toLowerCase(Locale.ROOT).replace('_', ' ');
        }

        /**
         * @param label the role as a tower writes it
         * @return the role
         * @throws ValidationException when no role has that name
         */
        public static Role of(String label) {
            for (Role role : values()) {
                if (role.label().equals(label)) {
                    return role;
                }
            }
            throw new ValidationException(Json.quote(label) + " is not a role in a tower");
        }

        @Override
        public String toString() {
            return label();
        }
    }

    /** Every role a tower has. */
    public static final List<Role> ROLES = List.of(Role.values());

    private final String name;
    private final String id;
    private final Double heaviestBellPounds;
    private final List<Role> roles;
    private final String notes;

    /**
     * What is known about a ringer besides the name. Each part is {@code null}
     * when it is not known.
     *
     * @param id an identifier of the ringer's own; {@code null} makes one from the name
     * @param heaviestBellPounds the heaviest bell they can handle, in pounds
     * @param roles what they do besides ring; {@code null} means nothing
     * @param notes anything else worth writing down
     */
    public record Details(String id, Double heaviestBellPounds, List<Role> roles, String notes) {

        /** Nothing known. */
        public static final Details NONE = new Details(null, null, null, null);

        /**
         * @param newId the identifier
         * @return these details with the identifier
         */
        public Details withId(String newId) {
            return new Details(newId, heaviestBellPounds, roles, notes);
        }

        /**
         * @param pounds the heaviest bell the ringer can handle
         * @return these details with the limit
         */
        public Details withHeaviestBellPounds(Double pounds) {
            return new Details(id, pounds, roles, notes);
        }

        /**
         * @param newRoles what the ringer does besides ring
         * @return these details with the roles
         */
        public Details withRoles(Role... newRoles) {
            return withRoles(java.util.Arrays.asList(newRoles));
        }

        /**
         * @param newRoles what the ringer does besides ring
         * @return these details with the roles
         */
        public Details withRoles(List<Role> newRoles) {
            return new Details(id, heaviestBellPounds, newRoles, notes);
        }

        /**
         * @param newNotes anything else worth writing down
         * @return these details with the notes
         */
        public Details withNotes(String newNotes) {
            return new Details(id, heaviestBellPounds, roles, newNotes);
        }
    }

    /**
     * @param name the ringer's name
     * @param details what else is known about them
     * @throws ValidationException when the details cannot be used
     * @throws IllegalArgumentException when the name is missing or empty
     */
    public Ringer(String name, Details details) {
        if (details == null) {
            throw new ValidationException("a ringer needs a definition object");
        }
        Guards.nonEmptyString(name, "a ringer name");
        Double heaviest = details.heaviestBellPounds();
        if (heaviest != null && !(heaviest > 0)) {
            throw new ValidationException(Json.number(heaviest) + " is not a bell weight a ringer can handle");
        }
        List<Role> given = details.roles() == null ? List.of() : details.roles();
        for (Role role : given) {
            if (role == null) {
                throw new ValidationException("null is not a role in a tower");
            }
        }
        this.name = Text.squash(name);
        this.id = details.id() == null ? Text.slugify(this.name) : details.id();
        this.heaviestBellPounds = heaviest;
        this.roles = new LinkedHashSet<>(given)
                .stream().sorted(Comparator.comparing(Role::label)).toList();
        this.notes = details.notes() == null ? "" : Text.squash(details.notes());
    }

    /**
     * Build a ringer from a name and nothing else, which is how most band lists
     * start out.
     *
     * @param name the ringer's name
     * @return the ringer
     */
    public static Ringer ringer(String name) {
        return new Ringer(name, Details.NONE);
    }

    /**
     * @param name the ringer's name
     * @param details what else is known about them
     * @return the ringer
     */
    public static Ringer ringer(String name, Details details) {
        return new Ringer(name, details);
    }

    /**
     * @param band the band
     * @param role a role
     * @return the ringers in the band who hold it, in band order
     * @throws ValidationException when there is no role
     */
    public static List<Ringer> withRole(Iterable<Ringer> band, Role role) {
        if (role == null) {
            throw new ValidationException("null is not a role in a tower");
        }
        List<Ringer> found = new java.util.ArrayList<>();
        band.forEach(member -> {
            if (member.hasRole(role)) {
                found.add(member);
            }
        });
        return List.copyOf(found);
    }

    /**
     * @param band the band
     * @param bell a bell
     * @return the ringers in the band who could take it, in band order
     */
    public static List<Ringer> whoCanHandle(Iterable<Ringer> band, Bell bell) {
        List<Ringer> found = new java.util.ArrayList<>();
        band.forEach(member -> {
            if (member.canHandle(bell)) {
                found.add(member);
            }
        });
        return List.copyOf(found);
    }

    /**
     * @return the ringer's name, with its whitespace squashed
     */
    public String name() {
        return name;
    }

    /**
     * @return the identifier, made from the name when none was given
     */
    public String id() {
        return id;
    }

    /**
     * @return the heaviest bell they can handle, in pounds, when that is recorded
     */
    public OptionalDouble heaviestBellPounds() {
        return heaviestBellPounds == null ? OptionalDouble.empty() : OptionalDouble.of(heaviestBellPounds);
    }

    /**
     * @return what they do besides ring, sorted and without repeats
     */
    public List<Role> roles() {
        return roles;
    }

    /**
     * @return anything else worth writing down, empty when there is nothing
     */
    public String notes() {
        return notes;
    }

    /**
     * @param role a role
     * @return true when the ringer holds it
     */
    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    /**
     * @return true when the ringer conducts
     */
    public boolean conducts() {
        return hasRole(Role.CONDUCTOR);
    }

    /**
     * True when the ringer can handle a bell of a given weight. A ringer with no
     * weight recorded is assumed to manage anything, because most bands never
     * write it down and refusing everybody would be worse than useless.
     *
     * @param bell a bell
     * @return true when the bell is not too heavy for them
     */
    public boolean canHandle(Bell bell) {
        return heaviestBellPounds == null || bell.weightPounds() <= heaviestBellPounds;
    }

    /**
     * @return the name, followed by the roles in brackets when there are any
     */
    @Override
    public String toString() {
        if (roles.isEmpty()) {
            return name;
        }
        return name + " (" + roles.stream().map(Role::label).collect(Collectors.joining(", ")) + ")";
    }

    /**
     * @return the roles, by label, which is the form a document writes them in
     */
    List<String> roleLabels() {
        return roles.stream().map(Role::label).toList();
    }
}
