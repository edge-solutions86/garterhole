package io.github.rkaushikdev.garterhole.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Text helpers for the report writers and the command line. Everything here
 * works on plain strings and never touches a locale, because a peal report
 * printed in one place has to match the same report printed in another.
 */
public final class Text {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern NOT_SLUG = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_HYPHENS = Pattern.compile("^-+|-+$");
    private static final String DEFAULT_GAP = "  ";

    private Text() {}

    /** How a column of a table is lined up. */
    public enum Align {
        /** Padded on the right, which is the default. */
        LEFT,
        /** Padded on the left, for numbers. */
        RIGHT
    }

    /**
     * Pad on the right to a width, leaving longer text alone.
     *
     * @param text the text
     * @param width the width wanted
     * @return the padded text
     */
    public static String padRight(String text, int width) {
        return text.length() >= width ? text : text + " ".repeat(width - text.length());
    }

    /**
     * Pad on the left to a width, leaving longer text alone.
     *
     * @param text the text
     * @param width the width wanted
     * @return the padded text
     */
    public static String padLeft(String text, int width) {
        return text.length() >= width ? text : " ".repeat(width - text.length()) + text;
    }

    /**
     * Lay out rows of cells as a fixed width table, two spaces between columns
     * and every column on the left.
     *
     * @param rows the rows, each a list of cells
     * @return one string per line
     */
    public static List<String> tabulate(List<? extends List<String>> rows) {
        return tabulate(rows, DEFAULT_GAP, List.of());
    }

    /**
     * Lay out rows of cells as a fixed width table with two spaces between
     * columns.
     *
     * @param rows the rows, each a list of cells
     * @param align how each column is lined up; a column not mentioned goes left
     * @return one string per line
     */
    public static List<String> tabulate(List<? extends List<String>> rows, List<Align> align) {
        return tabulate(rows, DEFAULT_GAP, align);
    }

    /**
     * Lay out rows of cells as a fixed width table, one string per line. Column
     * widths come from the widest cell in each column, and a row with too few
     * cells is padded out rather than refused.
     *
     * @param rows the rows, each a list of cells
     * @param gap what goes between columns
     * @param align how each column is lined up; a column not mentioned goes left
     * @return one string per line
     */
    public static List<String> tabulate(List<? extends List<String>> rows, String gap, List<Align> align) {
        int columns = rows.stream().mapToInt(List::size).max().orElse(0);
        int[] widths = new int[columns];
        for (List<String> row : rows) {
            for (int column = 0; column < columns; column += 1) {
                widths[column] = Math.max(widths[column], cell(row, column).length());
            }
        }
        List<String> lines = new ArrayList<>(rows.size());
        for (List<String> row : rows) {
            List<String> cells = new ArrayList<>(columns);
            for (int column = 0; column < columns; column += 1) {
                String cell = cell(row, column);
                boolean right = column < align.size() && align.get(column) == Align.RIGHT;
                cells.add(right ? padLeft(cell, widths[column]) : padRight(cell, widths[column]));
            }
            lines.add(String.join(gap, cells).stripTrailing());
        }
        return lines;
    }

    /**
     * {@code 1 bell} or {@code 4 bells}, with the number written out.
     *
     * @param count how many
     * @param singular the word for one
     * @return the count and the word
     */
    public static String plural(long count, String singular) {
        return plural(count, singular, singular + "s");
    }

    /**
     * {@code 1 belfry} or {@code 2 belfries}.
     *
     * @param count how many
     * @param singular the word for one
     * @param pluralForm the word for any other number
     * @return the count and the word
     */
    public static String plural(long count, String singular, String pluralForm) {
        return count + " " + (count == 1 ? singular : pluralForm);
    }

    /**
     * Join a list the way a sentence would: {@code a}, {@code a and b},
     * {@code a, b and c}.
     *
     * @param items the words
     * @return the sentence fragment
     */
    public static String joinWords(List<String> items) {
        if (items.isEmpty()) {
            return "";
        }
        if (items.size() == 1) {
            return items.get(0);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.get(items.size() - 1);
    }

    /**
     * Cut text to a width, ending with a full stop when it had to cut.
     *
     * @param text the text
     * @param width the widest the result may be
     * @return the text, cut if it had to be
     */
    public static String truncate(String text, int width) {
        if (width < 1) {
            throw new IllegalArgumentException("width " + width + " must be at least 1");
        }
        if (text.length() <= width) {
            return text;
        }
        if (width == 1) {
            return ".";
        }
        return text.substring(0, width - 1) + ".";
    }

    /**
     * Turn a title into something safe to use in a file name or a URL.
     *
     * @param text the title
     * @return lower case letters and digits joined by hyphens
     */
    public static String slugify(String text) {
        String hyphenated = NOT_SLUG.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("-");
        return EDGE_HYPHENS.matcher(hyphenated).replaceAll("");
    }

    /**
     * Collapse runs of whitespace and trim, which is what a form field needs
     * before anything is compared against it.
     *
     * @param text the text
     * @return the text with single spaces
     */
    public static String squash(String text) {
        return WHITESPACE.matcher(text).replaceAll(" ").strip();
    }

    /**
     * Wrap text at a width without breaking words. A word longer than the width
     * gets a line of its own rather than being cut in half.
     *
     * @param text the text
     * @param width the widest a line may be
     * @return the lines
     */
    public static List<String> wrap(String text, int width) {
        if (width < 1) {
            throw new IllegalArgumentException("width " + width + " must be at least 1");
        }
        List<String> words = Arrays.stream(squash(text).split(" "))
                .filter(word -> !word.isEmpty())
                .toList();
        if (words.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder(words.get(0));
        for (String word : words.subList(1, words.size())) {
            if (line.length() + 1 + word.length() <= width) {
                line.append(' ').append(word);
            } else {
                lines.add(line.toString());
                line = new StringBuilder(word);
            }
        }
        lines.add(line.toString());
        return lines;
    }

    /**
     * A number with a fixed count of digits after the point, rounded from its
     * exact value with halves going away from zero. This is what a report wants
     * and, unlike {@link String#format}, it does not round a value such as
     * {@code 1.005} up because its shortest decimal happens to end in a five.
     *
     * @param value the number
     * @param digits how many digits after the point
     * @return the number as text
     */
    public static String fixed(double value, int digits) {
        if (!Double.isFinite(value)) {
            return Json.number(value);
        }
        if (value < 0) {
            return "-" + fixed(-value, digits);
        }
        return new BigDecimal(value).setScale(digits, RoundingMode.HALF_UP).toPlainString();
    }

    private static String cell(List<String> row, int column) {
        return column < row.size() && row.get(column) != null ? row.get(column) : "";
    }
}
