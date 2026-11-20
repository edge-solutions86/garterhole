package io.github.rkaushikdev.garterhole.composition;

import io.github.rkaushikdev.garterhole.NotationException;
import io.github.rkaushikdev.garterhole.util.Guards;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Breaking a touch into tokens.
 *
 * <p>A touch is written as a run of calls, and every composer writes it
 * slightly differently. {@code -.-.s-} and {@code W H sH} and {@code 3(WsH)}
 * are all touches, and all three are read here into the same token stream.
 *
 * <p>The rule that keeps it unambiguous is about case. An upper case letter is a
 * calling position, so {@code H} means home. A lower case letter, a dash or a
 * full stop is a call symbol, so {@code s} means single. Digits are repeat
 * counts and brackets group.
 */
public final class TouchLexer {

    private TouchLexer() {}

    /** The kinds of token a touch is made of. */
    public enum TokenType {
        /** A call symbol such as {@code -} or {@code s}. */
        CALL,
        /** A calling position such as {@code W} or {@code H}. */
        POSITION,
        /** A repeat count. */
        NUMBER,
        /** An opening bracket. */
        OPEN,
        /** A closing bracket. */
        CLOSE,
        /** A comma, which separates one part of a touch from the next. */
        PART;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * One token of a touch.
     *
     * @param type what kind of token it is
     * @param text the text it was read from
     * @param position the one based position it started at
     */
    public record TouchToken(TokenType type, String text, int position) {}

    /**
     * Break a touch into tokens.
     *
     * @param text the touch as written
     * @return the tokens, in order
     * @throws NotationException naming the first character that is not part of a touch
     * @throws IllegalArgumentException when there is no text
     */
    public static List<TouchToken> lexTouch(String text) {
        Guards.present(text, "a touch must be a string");
        List<TouchToken> tokens = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            char character = text.charAt(index);
            if (character == ' ' || character == '\t' || character == '\n' || character == '\r') {
                index += 1;
            } else if (character == '(') {
                tokens.add(new TouchToken(TokenType.OPEN, "(", index + 1));
                index += 1;
            } else if (character == ')') {
                tokens.add(new TouchToken(TokenType.CLOSE, ")", index + 1));
                index += 1;
            } else if (character == ',') {
                tokens.add(new TouchToken(TokenType.PART, ",", index + 1));
                index += 1;
            } else if (isDigit(character)) {
                int start = index;
                while (index < text.length() && isDigit(text.charAt(index))) {
                    index += 1;
                }
                tokens.add(new TouchToken(TokenType.NUMBER, text.substring(start, index), start + 1));
            } else if (character >= 'A' && character <= 'Z') {
                tokens.add(new TouchToken(TokenType.POSITION, String.valueOf(character), index + 1));
                index += 1;
            } else if ((character >= 'a' && character <= 'z') || character == '-' || character == '.') {
                tokens.add(new TouchToken(TokenType.CALL, String.valueOf(character), index + 1));
                index += 1;
            } else {
                throw new NotationException(
                        Json.quote(String.valueOf(character)) + " at position " + (index + 1)
                                + " is not part of a touch",
                        text,
                        index + 1);
            }
        }
        return List.copyOf(tokens);
    }

    /**
     * True when the touch is written as calling positions rather than as a call
     * symbol for every lead. The two styles mean different things, so the
     * reader has to know which one it is looking at before it starts.
     *
     * @param tokens the tokens
     * @return true when an upper case letter appears
     */
    public static boolean usesCallingPositions(List<TouchToken> tokens) {
        return tokens.stream().anyMatch(token -> token.type() == TokenType.POSITION);
    }

    /**
     * Check the brackets match, and say where they do not.
     *
     * @param tokens the tokens
     * @throws NotationException naming the bracket that does not match
     */
    public static void checkBracketsBalance(List<TouchToken> tokens) {
        Deque<TouchToken> open = new ArrayDeque<>();
        for (TouchToken token : tokens) {
            if (token.type() == TokenType.OPEN) {
                open.push(token);
            } else if (token.type() == TokenType.CLOSE) {
                if (open.isEmpty()) {
                    throw new NotationException(
                            "a bracket closes at position " + token.position() + " without opening",
                            null,
                            token.position());
                }
                open.pop();
            }
        }
        if (!open.isEmpty()) {
            TouchToken last = open.peek();
            throw new NotationException(
                    "the bracket at position " + last.position() + " is never closed", null, last.position());
        }
    }

    /**
     * Write a token stream back out, which the error messages use so that a
     * touch is quoted as the reader understood it.
     *
     * @param tokens the tokens
     * @return the touch without its whitespace
     */
    public static String renderTouchTokens(List<TouchToken> tokens) {
        return tokens.stream().map(TouchToken::text).collect(Collectors.joining());
    }

    private static boolean isDigit(char character) {
        return character >= '0' && character <= '9';
    }
}
