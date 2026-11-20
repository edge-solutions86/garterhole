package io.github.rkaushikdev.garterhole.composition;

import io.github.rkaushikdev.garterhole.NotationException;
import io.github.rkaushikdev.garterhole.composition.TouchLexer.TokenType;
import io.github.rkaushikdev.garterhole.composition.TouchLexer.TouchToken;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Reading a touch into a list of instructions.
 *
 * <p>There are two styles and they mean different things. A touch written as one
 * symbol per lead says what happens at every lead, plain leads included. A
 * touch written as calling positions says only where the calls are, and the
 * leads in between are plain by implication.
 *
 * <p>Repeats are written either way round, so both {@code 3(WH)} and
 * {@code (WH)3} mean the same thing, and both are expanded here rather than
 * being carried through: what comes out is a flat list of instructions.
 */
public final class TouchParser {

    /** The call symbol assumed when a calling position is written with no symbol. */
    public static final String DEFAULT_CALL_SYMBOL = "-";

    /** The symbol that means a plain lead. */
    public static final String PLAIN_SYMBOL = ".";

    private TouchParser() {}

    /** How a touch is written. */
    public enum Style {
        /** One call symbol for every lead, plain leads included. */
        SYMBOLS,
        /** Calling positions, with the plain leads between them implied. */
        POSITIONS;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * One thing the conductor says: a call, and where it is made when the touch
     * is written as calling positions.
     *
     * @param symbol the call symbol, {@code .} for a plain lead
     * @param position the calling position, when the touch is written that way
     * @param at the one based position in the source the instruction was read from
     */
    public record Instruction(String symbol, Optional<String> position, int at) {}

    /**
     * A touch, read.
     *
     * @param style how it was written
     * @param instructions every instruction, repeats written out
     * @param source the touch as written
     */
    public record Touch(Style style, List<Instruction> instructions, String source) {}

    /**
     * Read a touch.
     *
     * @param text the touch as written, for example {@code 3(WsH)} or {@code -.-.s-}
     * @return the touch
     * @throws NotationException when the touch cannot be read
     */
    public static Touch parseTouch(String text) {
        List<TouchToken> tokens = TouchLexer.lexTouch(text);
        TouchLexer.checkBracketsBalance(tokens);
        Style style = TouchLexer.usesCallingPositions(tokens) ? Style.POSITIONS : Style.SYMBOLS;
        Reader reader = new Reader(tokens, style);
        List<Instruction> instructions = reader.readSequence(false);
        return new Touch(style, List.copyOf(instructions), text);
    }

    /**
     * How many leads a touch written as symbols runs to. A touch written as
     * calling positions has no answer until it is rung against a method.
     *
     * @param touch a touch
     * @return the number of leads, when the touch is written as symbols
     */
    public static OptionalInt leadCount(Touch touch) {
        return touch.style() == Style.SYMBOLS
                ? OptionalInt.of(touch.instructions().size())
                : OptionalInt.empty();
    }

    /**
     * @param touch a touch
     * @return the calls in it, leaving out the plain leads
     */
    public static List<Instruction> callsIn(Touch touch) {
        return touch.instructions().stream()
                .filter(instruction -> !instruction.symbol().equals(PLAIN_SYMBOL))
                .toList();
    }

    /** Walks the tokens once, keeping its place between the methods that read a piece each. */
    private static final class Reader {
        private final List<TouchToken> tokens;
        private final Style style;
        private int index;

        Reader(List<TouchToken> tokens, Style style) {
            this.tokens = tokens;
            this.style = style;
        }

        List<Instruction> readSequence(boolean inGroup) {
            List<Instruction> instructions = new ArrayList<>();
            while (index < tokens.size()) {
                TouchToken token = tokens.get(index);
                switch (token.type()) {
                    case CLOSE -> {
                        if (!inGroup) {
                            throw new NotationException(
                                    "a bracket closes at position " + token.position() + " without opening",
                                    null,
                                    token.position());
                        }
                        return instructions;
                    }
                    case PART -> index += 1;
                    case NUMBER -> {
                        index += 1;
                        List<Instruction> group = readGroup(token);
                        instructions.addAll(repeat(group, countOf(token), token));
                    }
                    case OPEN -> {
                        List<Instruction> group = readGroup(token);
                        instructions.addAll(repeat(group, readTrailingCount(), token));
                    }
                    default -> instructions.add(readInstruction());
                }
            }
            if (inGroup) {
                throw new NotationException("a bracket is never closed");
            }
            return instructions;
        }

        private List<Instruction> readGroup(TouchToken at) {
            TouchToken token = index < tokens.size() ? tokens.get(index) : null;
            if (token == null || token.type() != TokenType.OPEN) {
                throw new NotationException(
                        "a repeat at position " + at.position() + " has nothing to repeat", null, at.position());
            }
            index += 1;
            List<Instruction> inner = readSequence(true);
            TouchToken closing = index < tokens.size() ? tokens.get(index) : null;
            if (closing == null || closing.type() != TokenType.CLOSE) {
                throw new NotationException(
                        "the bracket at position " + token.position() + " is never closed", null, token.position());
            }
            index += 1;
            return inner;
        }

        private BigInteger readTrailingCount() {
            TouchToken token = index < tokens.size() ? tokens.get(index) : null;
            if (token != null && token.type() == TokenType.NUMBER) {
                index += 1;
                return countOf(token);
            }
            return BigInteger.ONE;
        }

        private Instruction readInstruction() {
            TouchToken token = tokens.get(index);
            index += 1;
            if (token.type() == TokenType.POSITION) {
                return new Instruction(DEFAULT_CALL_SYMBOL, Optional.of(token.text()), token.position());
            }
            if (style == Style.SYMBOLS) {
                return new Instruction(token.text(), Optional.empty(), token.position());
            }
            TouchToken next = index < tokens.size() ? tokens.get(index) : null;
            if (next == null || next.type() != TokenType.POSITION) {
                throw new NotationException(
                        "the call at position " + token.position() + " has no calling position after it",
                        null,
                        token.position());
            }
            index += 1;
            return new Instruction(token.text(), Optional.of(next.text()), token.position());
        }

        private static BigInteger countOf(TouchToken token) {
            return new BigInteger(token.text());
        }

        private static List<Instruction> repeat(List<Instruction> instructions, BigInteger count, TouchToken at) {
            if (count.signum() < 1) {
                throw new NotationException(
                        "a repeat count of " + count + " at position " + at.position() + " rings nothing",
                        null,
                        at.position());
            }
            if (instructions.isEmpty()) {
                return List.of();
            }
            BigInteger total = count.multiply(BigInteger.valueOf(instructions.size()));
            if (total.compareTo(BigInteger.valueOf(Integer.MAX_VALUE - 8)) > 0) {
                throw new NotationException(
                        "a repeat count of " + count + " at position " + at.position()
                                + " rings more than a touch can hold",
                        null,
                        at.position());
            }
            int times = count.intValueExact();
            List<Instruction> repeated = new ArrayList<>(total.intValueExact());
            for (int time = 0; time < times; time += 1) {
                for (Instruction instruction : instructions) {
                    repeated.add(new Instruction(instruction.symbol(), instruction.position(), instruction.at()));
                }
            }
            return repeated;
        }
    }
}
