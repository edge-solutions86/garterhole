import {
  lexTouch,
  assertBracketsBalance,
  usesCallingPositions,
  POSITION,
  NUMBER,
  OPEN,
  CLOSE,
  PART,
} from './touchLexer.js';
import { NotationError } from '../errors.js';

/**
 * Reading a touch into a list of instructions.
 *
 * There are two styles and they mean different things. A touch written as one
 * symbol per lead says what happens at every lead, plain leads included. A touch
 * written as calling positions says only where the calls are, and the leads in
 * between are plain by implication.
 *
 * Repeats are written either way round, so both `3(WH)` and `(WH)3` mean the
 * same thing, and both are expanded here rather than being carried through.
 */

/** The call symbol assumed when a calling position is written with no symbol. */
export const DEFAULT_CALL_SYMBOL = '-';

/** The symbol that means a plain lead. */
export const PLAIN_SYMBOL = '.';

/**
 * @typedef {{ symbol: string, position?: string, at: number }} TouchInstruction
 */

/**
 * Read a touch.
 *
 * @param {string} text
 * @returns {{ style: 'symbols' | 'positions', instructions: TouchInstruction[], source: string }}
 */
export function parseTouch(text) {
  const tokens = lexTouch(text);
  assertBracketsBalance(tokens);
  const style = usesCallingPositions(tokens) ? 'positions' : 'symbols';
  const reader = { tokens, index: 0, style };
  const instructions = readSequence(reader, false);
  return { style, instructions, source: String(text) };
}

/**
 * How many leads a touch written as symbols runs to. A touch written as calling
 * positions has no answer until it is rung against a method, so it gives null.
 *
 * @param {{ style: string, instructions: TouchInstruction[] }} touch
 * @returns {number | null}
 */
export function leadCount(touch) {
  return touch.style === 'symbols' ? touch.instructions.length : null;
}

/**
 * The calls in a touch, leaving out the plain leads.
 *
 * @param {{ instructions: TouchInstruction[] }} touch
 * @returns {TouchInstruction[]}
 */
export function callsIn(touch) {
  return touch.instructions.filter((instruction) => instruction.symbol !== PLAIN_SYMBOL);
}

/**
 * @param {{ tokens: readonly import('./touchLexer.js').TouchToken[], index: number, style: string }} reader
 * @param {boolean} inGroup
 * @returns {TouchInstruction[]}
 */
function readSequence(reader, inGroup) {
  /** @type {TouchInstruction[]} */
  const instructions = [];
  while (reader.index < reader.tokens.length) {
    const token = reader.tokens[reader.index];
    if (token.type === CLOSE) {
      if (!inGroup) {
        throw new NotationError(`a bracket closes at position ${token.position} without opening`, {
          position: token.position,
        });
      }
      return instructions;
    }
    if (token.type === PART) {
      reader.index += 1;
      continue;
    }
    if (token.type === NUMBER) {
      reader.index += 1;
      const count = Number(token.text);
      const group = readGroup(reader, token);
      instructions.push(...repeat(group, count, token));
      continue;
    }
    if (token.type === OPEN) {
      const group = readGroup(reader, token);
      const count = readTrailingCount(reader);
      instructions.push(...repeat(group, count, token));
      continue;
    }
    instructions.push(readInstruction(reader));
  }
  if (inGroup) {
    throw new NotationError('a bracket is never closed');
  }
  return instructions;
}

/**
 * @param {object} reader
 * @param {import('./touchLexer.js').TouchToken} at
 * @returns {TouchInstruction[]}
 */
function readGroup(reader, at) {
  const token = reader.tokens[reader.index];
  if (token === undefined || token.type !== OPEN) {
    throw new NotationError(`a repeat at position ${at.position} has nothing to repeat`, {
      position: at.position,
    });
  }
  reader.index += 1;
  const inner = readSequence(reader, true);
  const closing = reader.tokens[reader.index];
  if (closing === undefined || closing.type !== CLOSE) {
    throw new NotationError(`the bracket at position ${token.position} is never closed`, {
      position: token.position,
    });
  }
  reader.index += 1;
  return inner;
}

/**
 * @param {object} reader
 * @returns {number}
 */
function readTrailingCount(reader) {
  const token = reader.tokens[reader.index];
  if (token !== undefined && token.type === NUMBER) {
    reader.index += 1;
    return Number(token.text);
  }
  return 1;
}

/**
 * @param {TouchInstruction[]} instructions
 * @param {number} count
 * @param {import('./touchLexer.js').TouchToken} at
 * @returns {TouchInstruction[]}
 */
function repeat(instructions, count, at) {
  if (count < 1) {
    throw new NotationError(`a repeat count of ${count} at position ${at.position} rings nothing`, {
      position: at.position,
    });
  }
  const repeated = [];
  for (let time = 0; time < count; time += 1) {
    repeated.push(...instructions.map((instruction) => ({ ...instruction })));
  }
  return repeated;
}

/**
 * @param {object} reader
 * @returns {TouchInstruction}
 */
function readInstruction(reader) {
  const token = reader.tokens[reader.index];
  if (token.type === POSITION) {
    reader.index += 1;
    return { symbol: DEFAULT_CALL_SYMBOL, position: token.text, at: token.position };
  }
  reader.index += 1;
  if (reader.style === 'symbols') {
    return { symbol: token.text, at: token.position };
  }
  const next = reader.tokens[reader.index];
  if (next === undefined || next.type !== POSITION) {
    throw new NotationError(
      `the call at position ${token.position} has no calling position after it`,
      { position: token.position },
    );
  }
  reader.index += 1;
  return { symbol: token.text, position: next.text, at: token.position };
}
