package io.github.rkaushikdev.garterhole.notation;

import io.github.rkaushikdev.garterhole.GarterholeException;
import io.github.rkaushikdev.garterhole.NotationException;
import io.github.rkaushikdev.garterhole.core.Bells;
import io.github.rkaushikdev.garterhole.core.Change;
import io.github.rkaushikdev.garterhole.core.Stage;
import io.github.rkaushikdev.garterhole.notation.Tokenizer.Token;
import io.github.rkaushikdev.garterhole.notation.Tokenizer.TokenType;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turning a token stream into changes. The parser is deliberately strict about
 * separators: a full stop between two runs of places is the only thing that
 * says where one run ends, so notation that leaves it out is ambiguous rather
 * than merely untidy and is refused.
 */
public final class NotationParser {

    private NotationParser() {}

    /**
     * Notation read into sections, before any folding out.
     *
     * @param sections the changes of each section the commas divide the notation into
     * @param folded true when there was a comma
     * @param source the notation as written
     */
    public record ParsedNotation(List<List<Change>> sections, boolean folded, String source) {}

    /**
     * Parse one section, that is a run of notation with no comma in it.
     *
     * @param tokens the tokens of the section
     * @param stage the stage
     * @return the changes
     * @throws NotationException when the separators are misplaced or a run is not a change
     */
    public static List<Change> parseSection(List<Token> tokens, int stage) {
        requireStage(stage);
        List<Change> changes = new ArrayList<>();
        Token previous = null;
        for (Token token : tokens) {
            if (token.type() == TokenType.SEPARATOR) {
                if (previous == null) {
                    throw new NotationException("place notation cannot start with a separator", null, token.position());
                }
                if (previous.type() == TokenType.SEPARATOR) {
                    throw new NotationException("place notation has two separators in a row", null, token.position());
                }
                previous = token;
                continue;
            }
            changes.add(changeFromToken(token, stage));
            previous = token;
        }
        if (previous != null && previous.type() == TokenType.SEPARATOR) {
            throw new NotationException("place notation cannot end with a separator", null, previous.position());
        }
        return List.copyOf(changes);
    }

    /**
     * Parse a whole notation string, keeping the sections the commas divide it
     * into. Nothing is expanded here; a palindromic method still has to be
     * folded out before it can be rung.
     *
     * @param text the notation
     * @param stage the stage
     * @return the sections
     * @throws NotationException when a section is empty or cannot be read
     */
    public static ParsedNotation parseNotation(String text, int stage) {
        requireStage(stage);
        List<List<Token>> sections = Tokenizer.splitAtFolds(Tokenizer.tokenize(text));
        List<List<Change>> parsed = new ArrayList<>(sections.size());
        for (int index = 0; index < sections.size(); index += 1) {
            if (sections.get(index).isEmpty()) {
                throw new NotationException("section " + (index + 1) + " of the notation is empty", text, null);
            }
            parsed.add(parseSection(sections.get(index), stage));
        }
        return new ParsedNotation(List.copyOf(parsed), parsed.size() > 1, text);
    }

    /**
     * The common case: notation with no comma, read straight through.
     *
     * @param text the notation
     * @param stage the stage
     * @return the changes
     * @throws NotationException when the notation has a fold or cannot be read
     */
    public static List<Change> parseChanges(String text, int stage) {
        ParsedNotation parsed = parseNotation(text, stage);
        if (parsed.folded()) {
            throw new NotationException("this notation has a fold and has to be expanded first", text, null);
        }
        return parsed.sections().get(0);
    }

    private static Change changeFromToken(Token token, int stage) {
        return switch (token.type()) {
            case CROSS -> buildChange(stage, List.of(), token);
            case PLACES ->
                buildChange(
                        stage,
                        Arrays.stream(Bells.fromSymbols(token.text())).boxed().toList(),
                        token);
            default ->
                throw new NotationException(
                        "unexpected " + token.type() + " in place notation", null, token.position());
        };
    }

    /**
     * Build a change and turn any complaint from the core into a notation error
     * that names where in the source the trouble is.
     */
    private static Change buildChange(int stage, List<Integer> places, Token token) {
        try {
            return Change.withImpliedPlaces(stage, places);
        } catch (GarterholeException | IllegalArgumentException failure) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("stage", stage);
            details.put("places", places);
            throw new NotationException(
                    Json.quote(token.text()) + ": " + failure.getMessage(), null, token.position(), details);
        }
    }

    private static void requireStage(int stage) {
        if (!Stage.isSupported(stage)) {
            throw new IllegalArgumentException("stage " + stage + " is not supported");
        }
    }
}
