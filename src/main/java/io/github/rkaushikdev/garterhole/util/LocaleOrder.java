package io.github.rkaushikdev.garterhole.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The order a person expects a list of names in, as the original's
 * {@code localeCompare} gave it.
 *
 * <p>That is the root collation of the Unicode collation algorithm, which the
 * JDK's own collator does not follow: the JDK ignores spaces and hyphens, so it
 * puts {@code stoke} before {@code st-peter}, where every ringer and the
 * original put {@code st-peter} first. This compares in three levels the way
 * the root collation does. First the letters, ignoring accents and case, with
 * spaces and then punctuation before digits and digits before letters. Then
 * the accents. Then the case, lower case first.
 *
 * <p>Strings that differ only in characters the collation ignores compare
 * equal, as they did in the original, so a stable sort keeps their order.
 */
public final class LocaleOrder {

    /** The comparator. */
    public static final Comparator<String> ORDER = LocaleOrder::compare;

    /** Punctuation and symbols in the order the root collation puts them. */
    private static final String PUNCTUATION = "_-,;:!?.'\"()[]{}@*/\\&#%`^+<=>|~$";

    private static final int SPACE_BASE = 100;
    private static final int PUNCTUATION_BASE = 1_000;
    private static final int SYMBOL_BASE = 2_000;
    private static final int DIGIT_BASE = 2_000_000;
    private static final int LETTER_BASE = 3_000_000;
    private static final int OTHER_LETTER_BASE = 3_000_100;

    private LocaleOrder() {}

    /**
     * @param left one string
     * @param right another
     * @return negative, zero or positive, the way {@link Comparator#compare} does
     */
    public static int compare(String left, String right) {
        Keys a = keysOf(left);
        Keys b = keysOf(right);
        int primary = compareSequences(a.primary(), b.primary());
        if (primary != 0) {
            return primary;
        }
        int secondary = compareSequences(a.secondary(), b.secondary());
        if (secondary != 0) {
            return secondary;
        }
        return compareSequences(a.tertiary(), b.tertiary());
    }

    private static Keys keysOf(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        List<Integer> primary = new ArrayList<>();
        List<Integer> secondary = new ArrayList<>();
        List<Integer> tertiary = new ArrayList<>();
        decomposed.codePoints().forEach(point -> {
            int type = Character.getType(point);
            if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK) {
                secondary.add(point);
                return;
            }
            if (Character.isISOControl(point) && !Character.isWhitespace(point)) {
                return;
            }
            primary.add(primaryOf(point));
            secondary.add(0);
            tertiary.add(Character.isUpperCase(point) || Character.isTitleCase(point) ? 1 : 0);
        });
        return new Keys(primary, secondary, tertiary);
    }

    private static int primaryOf(int point) {
        if (Character.isWhitespace(point) || Character.isSpaceChar(point)) {
            return SPACE_BASE + point;
        }
        int punctuation = point < Character.MIN_SUPPLEMENTARY_CODE_POINT ? PUNCTUATION.indexOf(point) : -1;
        if (punctuation >= 0) {
            return PUNCTUATION_BASE + punctuation;
        }
        if (Character.isDigit(point)) {
            return DIGIT_BASE + Character.digit(point, 10);
        }
        if (Character.isLetter(point)) {
            int lower = Character.toLowerCase(point);
            if (lower >= 'a' && lower <= 'z') {
                return LETTER_BASE + (lower - 'a');
            }
            return OTHER_LETTER_BASE + lower;
        }
        return SYMBOL_BASE + point;
    }

    private static int compareSequences(List<Integer> left, List<Integer> right) {
        int shared = Math.min(left.size(), right.size());
        for (int index = 0; index < shared; index += 1) {
            int difference = Integer.compare(left.get(index), right.get(index));
            if (difference != 0) {
                return difference;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    private record Keys(List<Integer> primary, List<Integer> secondary, List<Integer> tertiary) {}
}
