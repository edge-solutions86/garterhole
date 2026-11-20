package io.github.rkaushikdev.garterhole.notation;

import io.github.rkaushikdev.garterhole.NotationException;
import io.github.rkaushikdev.garterhole.core.Bells;
import io.github.rkaushikdev.garterhole.util.Guards;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Place notation is written in half a dozen dialects. Every one of them uses
 * the same three ideas: a cross change, a run of places, and a separator. The
 * tokeniser flattens the dialects into one token stream so that the parser
 * never has to think about whether the author typed {@code x} or {@code -}.
 */
public final class Tokenizer {

    private Tokenizer() {}

    /** The kinds of token place notation is made of. */
    public enum TokenType {
        /** A change where every bell swaps. */
        CROSS,
        /** A run of bell symbols naming the places made. */
        PLACES,
        /** A full stop, which separates two runs of places. */
        SEPARATOR,
        /** A comma, which marks the fold in a palindromic method. */
        FOLD;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * One token.
     *
     * @param type what kind of token it is
     * @param text the text it was read from, with letters in upper case
     * @param position the one based position it started at
     */
    public record Token(TokenType type, String text, int position) {}

    /**
     * Break place notation into tokens.
     *
     * @param text the notation
     * @return the tokens, in order
     * @throws NotationException naming the first character that is not notation
     * @throws IllegalArgumentException when there is no text
     */
    public static List<Token> tokenize(String text) {
        Guards.present(text, "place notation must be a string");
        List<Token> tokens = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            char character = text.charAt(index);
            if (character == ' ' || character == '\t' || character == '\n' || character == '\r') {
                index += 1;
            } else if (character == 'x' || character == 'X' || character == '-') {
                tokens.add(new Token(TokenType.CROSS, String.valueOf(character), index + 1));
                index += 1;
            } else if (character == '.') {
                tokens.add(new Token(TokenType.SEPARATOR, ".", index + 1));
                index += 1;
            } else if (character == ',') {
                tokens.add(new Token(TokenType.FOLD, ",", index + 1));
                index += 1;
            } else if (Bells.isSymbol(character)) {
                int start = index;
                while (index < text.length() && Bells.isSymbol(text.charAt(index))) {
                    index += 1;
                }
                String run = text.substring(start, index).toUpperCase(Locale.ROOT);
                tokens.add(new Token(TokenType.PLACES, run, start + 1));
            } else {
                throw new NotationException(
                        Json.quote(String.valueOf(character)) + " at position " + (index + 1)
                                + " is not part of place notation",
                        text,
                        index + 1);
            }
        }
        return List.copyOf(tokens);
    }

    /**
     * True when the token stream contains a fold, which means the notation is
     * written as a palindrome and has to be expanded before it can be rung.
     *
     * @param tokens the tokens
     * @return true when there is a comma
     */
    public static boolean hasFold(List<Token> tokens) {
        return tokens.stream().anyMatch(token -> token.type() == TokenType.FOLD);
    }

    /**
     * Split a token stream at its folds. Notation with no fold gives one
     * section, which keeps the caller free of a special case.
     *
     * @param tokens the tokens
     * @return the sections, each without its folds
     */
    public static List<List<Token>> splitAtFolds(List<Token> tokens) {
        List<List<Token>> sections = new ArrayList<>();
        List<Token> current = new ArrayList<>();
        for (Token token : tokens) {
            if (token.type() == TokenType.FOLD) {
                sections.add(List.copyOf(current));
                current = new ArrayList<>();
            } else {
                current.add(token);
            }
        }
        sections.add(List.copyOf(current));
        return List.copyOf(sections);
    }

    /**
     * Write a token stream back out. Used by the round trip tests and by the
     * error messages, which quote the notation as the tokeniser understood it
     * rather than as the author typed it.
     *
     * @param tokens the tokens
     * @return the notation in the house spelling
     */
    public static String renderTokens(List<Token> tokens) {
        StringBuilder text = new StringBuilder();
        for (Token token : tokens) {
            text.append(
                    switch (token.type()) {
                        case CROSS -> "x";
                        case PLACES -> token.text();
                        case SEPARATOR -> ".";
                        case FOLD -> ",";
                    });
        }
        return text.toString();
    }
}
