package io.github.rkaushikdev.garterhole.util;

import io.github.rkaushikdev.garterhole.ValidationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * Identifiers.
 *
 * <p>Two kinds are needed and they are not the same thing. A record filed by a
 * person wants an identifier built from what it is, so that filing the same
 * performance twice is caught. A request passing through the service wants one
 * that is unique and sorts by when it was made, so that a log can be read in
 * order.
 *
 * <p>The second kind is built here: a timestamp in base thirty six followed by
 * some randomness. Sorting by the string sorts by time, which is worth more in
 * a log than the extra few bits a random identifier would have. The source
 * takes its clock and its randomness so that a test can make it produce a
 * known sequence.
 */
public final class IdSource {

    /**
     * The alphabet the random tail is drawn from. It leaves out the characters
     * people misread when they read an identifier out over the phone.
     *
     * <p>The timestamp in front is written in base thirty six and therefore uses
     * the whole of {@code 0-9a-z}, so the two halves are checked against
     * different sets.
     */
    private static final String ALPHABET = "0123456789abcdefghjkmnpqrstvwxyz";

    private static final Pattern BASE_36 = Pattern.compile("^[0-9a-z]+$");
    private static final int STAMP_LENGTH = 9;
    private static final int DEFAULT_LENGTH = 8;
    private static final int SHORTEST = 4;
    private static final int LONGEST = 32;

    private final LongSupplier now;
    private final DoubleSupplier random;
    private final int length;

    /** A source on the real clock with real randomness and eight random characters. */
    public IdSource() {
        this(System::currentTimeMillis, () -> ThreadLocalRandom.current().nextDouble(), DEFAULT_LENGTH);
    }

    /**
     * @param now milliseconds since the epoch
     * @param random a number from zero up to but not including one
     */
    public IdSource(LongSupplier now, DoubleSupplier random) {
        this(now, random, DEFAULT_LENGTH);
    }

    /**
     * @param now milliseconds since the epoch
     * @param random a number from zero up to but not including one
     * @param length how many random characters follow the timestamp
     * @throws ValidationException when the length is below four or above thirty two
     */
    public IdSource(LongSupplier now, DoubleSupplier random, int length) {
        if (length < SHORTEST || length > LONGEST) {
            throw new ValidationException("an identifier cannot have " + length + " random characters");
        }
        this.now = now;
        this.random = random;
        this.length = length;
    }

    /**
     * @return the source the service uses when nobody supplies one
     */
    public static IdSource defaultIds() {
        return new IdSource();
    }

    /**
     * @return the next identifier, with no prefix
     */
    public String next() {
        return next("");
    }

    /**
     * @param prefix what goes in front, joined with an underscore; empty for none
     * @return the next identifier
     */
    public String next(String prefix) {
        String stamp =
                Text.padLeft(Long.toString(now.getAsLong(), 36), STAMP_LENGTH).replace(' ', '0');
        StringBuilder tail = new StringBuilder(length);
        for (int index = 0; index < length; index += 1) {
            int pick = (int) Math.floor(random.getAsDouble() * ALPHABET.length());
            tail.append(ALPHABET.charAt(Math.min(Math.max(pick, 0), ALPHABET.length() - 1)));
        }
        return prefix.isEmpty() ? stamp + tail : prefix + "_" + stamp + tail;
    }

    /**
     * Several identifiers at once.
     *
     * @param count how many
     * @param prefix what goes in front of each; empty for none
     * @return the identifiers
     * @throws ValidationException when the count is negative
     */
    public List<String> many(int count, String prefix) {
        if (count < 0) {
            throw new ValidationException(count + " is not a number of identifiers");
        }
        List<String> ids = new ArrayList<>(count);
        for (int index = 0; index < count; index += 1) {
            ids.add(next(prefix));
        }
        return ids;
    }

    /**
     * @param count how many
     * @return that many identifiers with no prefix
     */
    public List<String> many(int count) {
        return many(count, "");
    }

    /**
     * @param id anything that might be an identifier, or {@code null}
     * @return true when the string looks like one of these identifiers
     */
    public static boolean looksLikeId(String id) {
        if (id == null) {
            return false;
        }
        String body = bodyOf(id);
        return body.length() >= STAMP_LENGTH + SHORTEST && BASE_36.matcher(body).matches();
    }

    /**
     * @param id an identifier
     * @return the moment it was made, when it carries one
     */
    public static Optional<Instant> madeAt(String id) {
        if (!looksLikeId(id)) {
            return Optional.empty();
        }
        long stamp = Long.parseLong(bodyOf(id).substring(0, STAMP_LENGTH), 36);
        return Optional.of(Instant.ofEpochMilli(stamp));
    }

    private static String bodyOf(String id) {
        int underscore = id.indexOf('_');
        return underscore == -1 ? id : id.substring(underscore + 1);
    }
}
