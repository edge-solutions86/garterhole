package io.github.rkaushikdev.garterhole.notation;

import io.github.rkaushikdev.garterhole.core.Change;
import io.github.rkaushikdev.garterhole.core.Row;
import io.github.rkaushikdev.garterhole.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Writing notation back out.
 *
 * <p>This is not quite the inverse of reading it. The reader accepts three
 * spellings of a cross change and lets separators appear wherever they like;
 * the writer picks one house style and sticks to it.
 *
 * <p>The house style is the one the method collections print: {@code x} for a
 * cross, no separator next to a cross, a full stop between two runs of places,
 * and the palindrome folded at a comma when the lead has one.
 */
public final class NotationFormat {

    private NotationFormat() {}

    /**
     * The two sections a palindromic lead folds into.
     *
     * @param first the changes before the comma
     * @param second the changes after it
     */
    public record Folded(List<Change> first, List<Change> second) {}

    /**
     * Write a run of changes with no fold.
     *
     * @param changes the changes
     * @return the notation
     */
    public static String writeChanges(List<Change> changes) {
        StringBuilder text = new StringBuilder();
        boolean previousWasPlaces = false;
        for (Change change : changes) {
            boolean isPlaces = !change.isCross();
            if (isPlaces && previousWasPlaces) {
                text.append('.');
            }
            text.append(change);
            previousWasPlaces = isPlaces;
        }
        return text.toString();
    }

    /**
     * Split a palindromic lead back into the two sections a comma divides, so
     * that it can be written the short way.
     *
     * @param changes the changes of a lead
     * @return the two sections, or nothing when the lead has no axis or an axis
     *     structure the short form cannot describe
     */
    public static Optional<Folded> foldChanges(List<Change> changes) {
        List<Integer> axes = Expansion.symmetryPoints(changes);
        if (axes.size() != 2) {
            return Optional.empty();
        }
        int first = axes.get(0);
        int second = axes.get(1);
        int headLength = first + 1;
        int tailLength = changes.size() - 2 * first - 1;
        if (tailLength <= 0 || tailLength % 2 == 0) {
            return Optional.empty();
        }
        if (second != 2 * first + (tailLength + 1) / 2) {
            return Optional.empty();
        }
        return Optional.of(new Folded(
                List.copyOf(changes.subList(0, headLength)), List.copyOf(changes.subList(2 * first + 1, second + 1))));
    }

    /**
     * Write a lead the way a method collection prints it, folded at the comma
     * when that is possible and written out in full when it is not.
     *
     * @param changes the changes of a lead
     * @return the notation
     */
    public static String writeFolded(List<Change> changes) {
        return foldChanges(changes)
                .map(folded -> writeChanges(folded.first()) + "," + writeChanges(folded.second()))
                .orElseGet(() -> writeChanges(changes));
    }

    /**
     * Write one change per line with the row it produces beside it, which is
     * what the command line prints when somebody asks to see a lead.
     *
     * @param changes the changes
     * @param rows the rows, one more than the changes
     * @return the lines
     * @throws IllegalArgumentException when the rows and changes do not match up
     */
    public static List<String> writeLeadLines(List<Change> changes, List<Row> rows) {
        if (rows.size() != changes.size() + 1) {
            throw new IllegalArgumentException("expected " + (changes.size() + 1) + " rows for " + changes.size()
                    + " changes but got " + rows.size());
        }
        int width = Math.max(
                changes.stream()
                        .mapToInt(change -> change.toString().length())
                        .max()
                        .orElse(1),
                1);
        List<String> lines = new ArrayList<>(rows.size());
        lines.add(" ".repeat(width) + "  " + rows.get(0));
        for (int index = 0; index < changes.size(); index += 1) {
            lines.add(Text.padLeft(changes.get(index).toString(), width) + "  " + rows.get(index + 1));
        }
        return lines;
    }
}
