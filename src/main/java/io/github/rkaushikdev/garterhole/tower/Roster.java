package io.github.rkaushikdev.garterhole.tower;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.Clock;
import io.github.rkaushikdev.garterhole.util.Guards;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Who came to practice.
 *
 * <p>A tower captain keeps this in a notebook and uses it for two things:
 * knowing who has stopped coming, so that somebody can ring them up, and
 * knowing who has been often enough to be asked to a quarter peal.
 *
 * <p>Attendance is recorded per day rather than per practice, because a ringer
 * who comes to the morning service and the evening practice on the same Sunday
 * has come once as far as any of these questions are concerned. Days are UTC
 * days.
 */
public final class Roster {

    private final Map<String, Set<LocalDate>> byRinger = new LinkedHashMap<>();

    /**
     * One ringer's attendance.
     *
     * @param ringerId the ringer
     * @param attended how many days they have come
     * @param lastSeen the last day they came
     */
    public record Line(String ringerId, int attended, LocalDate lastSeen) {}

    /**
     * Somebody who has not been for a while.
     *
     * @param ringerId the ringer
     * @param lastSeen the last day they came
     */
    public record Absence(String ringerId, LocalDate lastSeen) {}

    /**
     * Record that somebody came.
     *
     * @param ringerId the ringer
     * @param when the moment they came, anywhere in the day
     * @return this roster
     * @throws ValidationException when there is no moment
     * @throws IllegalArgumentException when the ringer id is missing or empty
     */
    public Roster attended(String ringerId, Instant when) {
        Guards.nonEmptyString(ringerId, "a ringer id");
        if (when == null) {
            throw new ValidationException("attendance needs the date it happened");
        }
        byRinger.computeIfAbsent(ringerId, id -> new TreeSet<>()).add(dayOf(when));
        return this;
    }

    /**
     * @param ringerId the ringer
     * @return how many days they have been
     */
    public int countFor(String ringerId) {
        Set<LocalDate> days = byRinger.get(ringerId);
        return days == null ? 0 : days.size();
    }

    /**
     * @param ringerId the ringer
     * @return the days they came, earliest first
     */
    public List<LocalDate> daysFor(String ringerId) {
        Set<LocalDate> days = byRinger.get(ringerId);
        return days == null ? List.of() : List.copyOf(days);
    }

    /**
     * @param ringerId the ringer
     * @return the last day they came, when they have ever come
     */
    public Optional<LocalDate> lastSeen(String ringerId) {
        List<LocalDate> days = daysFor(ringerId);
        return days.isEmpty() ? Optional.empty() : Optional.of(days.get(days.size() - 1));
    }

    /**
     * @param when a moment in the day
     * @return who came that day, in id order
     */
    public List<String> whoCame(Instant when) {
        LocalDate day = dayOf(when);
        List<String> came = new ArrayList<>();
        byRinger.forEach((ringerId, days) -> {
            if (days.contains(day)) {
                came.add(ringerId);
            }
        });
        came.sort(null);
        return List.copyOf(came);
    }

    /**
     * @return everybody, most regular first, ties in id order
     */
    public List<Line> attendance() {
        List<Line> lines = new ArrayList<>();
        for (String ringerId : byRinger.keySet()) {
            lines.add(new Line(ringerId, countFor(ringerId), lastSeen(ringerId).orElseThrow()));
        }
        lines.sort(Comparator.comparingInt(Line::attended).reversed().thenComparing(Line::ringerId));
        return List.copyOf(lines);
    }

    /**
     * Who has not been since a date, which is the list somebody rings round.
     *
     * @param since a moment in the first day they should have come by
     * @return the ringers not seen since, most regular first
     * @throws ValidationException when there is no date
     */
    public List<Absence> missingSince(Instant since) {
        if (since == null) {
            throw new ValidationException("a date is needed to ask who is missing");
        }
        LocalDate day = dayOf(since);
        return attendance().stream()
                .filter(line -> line.lastSeen().isBefore(day))
                .map(line -> new Absence(line.ringerId(), line.lastSeen()))
                .toList();
    }

    /**
     * @return every day anybody came, earliest first
     */
    public List<LocalDate> days() {
        TreeSet<LocalDate> days = new TreeSet<>();
        byRinger.values().forEach(days::addAll);
        return List.copyOf(days);
    }

    private static LocalDate dayOf(Instant when) {
        return LocalDate.ofInstant(Clock.startOfDay(when), ZoneOffset.UTC);
    }
}
