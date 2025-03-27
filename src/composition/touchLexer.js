import { NotationError } from '../errors.js';

/**
 * A touch is written as a run of calls, and every composer writes it slightly
 * differently. `-.-.s-` and `W H sH` and `3(WsH)` are all touches, and all three
 * are read here into the same token stream.
 *
 * The rule that keeps it unambiguous is about case. An upper case letter is a
 * calling position, so `H` means home. A lower case letter or a dash is a call
 * symbol, so `s` means single. Digits are repeat counts and brackets group.
 */

/** A call symbol such as `-` or `s`. */
export const CALL = 'call';
/** A calling position such as `W` or `H`. */
export const POSITION = 'position';
/** A repeat count. */
export const NUMBER = 'number';
/** An opening bracket. */
export const OPEN = 'open';
/** A closing bracket. */
export const CLOSE = 'close';
/** A comma, which separates one part of a touch from the next. */
export const PART = 'part';

const CALL_CHARACTERS = /[-a-z.]/;
const POSITION_CHARACTERS = /[A-Z]/;
const DIGITS = /[0-9]/;

/**
 * @typedef {{ type: string, text: string, position: number }} TouchToken
 */

/**
 * Break a touch into tokens.
 *
 * @param {string} text
 * @returns {TouchToken[]}
 */
export function lexTouch(text) {
  if (typeof text !== 'string') {
    throw new TypeError('a touch must be a string');
  }
  /** @type {TouchToken[]} */
  const tokens = [];
  let index = 0;
  while (index < text.length) {
    const character = text[index];
    if (character === ' ' || character === '\t' || character === '\n' || character === '\r') {
      index += 1;
      continue;
    }
    if (character === '(') {
      tokens.push({ type: OPEN, text: character, position: index + 1 });
      index += 1;
      continue;
    }
    if (character === ')') {
      tokens.push({ type: CLOSE, text: character, position: index + 1 });
      index += 1;
      continue;
    }
    if (character === ',') {
      tokens.push({ type: PART, text: character, position: index + 1 });
      index += 1;
      continue;
    }
    if (DIGITS.test(character)) {
      const start = index;
      let digits = '';
      while (index < text.length && DIGITS.test(text[index])) {
        digits += text[index];
        index += 1;
      }
      tokens.push({ type: NUMBER, text: digits, position: start + 1 });
      continue;
    }
    if (POSITION_CHARACTERS.test(character)) {
      tokens.push({ type: POSITION, text: character, position: index + 1 });
      index += 1;
      continue;
    }
    if (CALL_CHARACTERS.test(character)) {
      tokens.push({ type: CALL, text: character, position: index + 1 });
      index += 1;
      continue;
    }
    throw new NotationError(
      `${JSON.stringify(character)} at position ${index + 1} is not part of a touch`,
      { source: text, position: index + 1 },
    );
  }
  return tokens;
}

/**
 * True when the touch is written as calling positions rather than as a call
 * symbol for every lead. The two styles mean different things, so the reader
 * has to know which one it is looking at before it starts.
 *
 * @param {readonly TouchToken[]} tokens
 * @returns {boolean}
 */
export function usesCallingPositions(tokens) {
  return tokens.some((token) => token.type === POSITION);
}

/**
 * Check the brackets match, and say where they do not.
 *
 * @param {readonly TouchToken[]} tokens
 */
export function assertBracketsBalance(tokens) {
  const open = [];
  for (const token of tokens) {
    if (token.type === OPEN) {
      open.push(token);
    } else if (token.type === CLOSE) {
      if (open.length === 0) {
        throw new NotationError(`a bracket closes at position ${token.position} without opening`, {
          position: token.position,
        });
      }
      open.pop();
    }
  }
  if (open.length > 0) {
    const last = open[open.length - 1];
    throw new NotationError(`the bracket at position ${last.position} is never closed`, {
      position: last.position,
    });
  }
}

/**
 * Write a token stream back out, which the error messages use so that a touch
 * is quoted as the reader understood it.
 *
 * @param {readonly TouchToken[]} tokens
 * @returns {string}
 */
export function renderTouchTokens(tokens) {
  return tokens.map((token) => token.text).join('');
}
