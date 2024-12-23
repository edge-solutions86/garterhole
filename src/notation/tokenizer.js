import { isBellSymbol } from '../core/bells.js';
import { NotationError } from '../errors.js';

/**
 * Place notation is written in half a dozen dialects. Every one of them uses
 * the same three ideas: a cross change, a run of places, and a separator. The
 * tokeniser flattens the dialects into one token stream so that the parser
 * never has to think about whether the author typed `x` or `-`.
 */

/** A change where every bell swaps. */
export const CROSS = 'cross';
/** A run of bell symbols naming the places made. */
export const PLACES = 'places';
/** A full stop, which separates two runs of places. */
export const SEPARATOR = 'separator';
/** A comma, which marks the fold in a palindromic method. */
export const FOLD = 'fold';

const CROSS_CHARACTERS = new Set(['x', 'X', '-']);

/**
 * @typedef {{ type: string, text: string, position: number }} Token
 */

/**
 * Break place notation into tokens.
 *
 * @param {string} text
 * @returns {Token[]}
 */
export function tokenize(text) {
  if (typeof text !== 'string') {
    throw new TypeError('place notation must be a string');
  }
  /** @type {Token[]} */
  const tokens = [];
  let index = 0;
  while (index < text.length) {
    const character = text[index];
    if (character === ' ' || character === '\t' || character === '\n' || character === '\r') {
      index += 1;
      continue;
    }
    if (CROSS_CHARACTERS.has(character)) {
      tokens.push({ type: CROSS, text: character, position: index + 1 });
      index += 1;
      continue;
    }
    if (character === '.') {
      tokens.push({ type: SEPARATOR, text: character, position: index + 1 });
      index += 1;
      continue;
    }
    if (character === ',') {
      tokens.push({ type: FOLD, text: character, position: index + 1 });
      index += 1;
      continue;
    }
    if (isBellSymbol(character)) {
      const start = index;
      let run = '';
      while (index < text.length && isBellSymbol(text[index])) {
        run += text[index];
        index += 1;
      }
      tokens.push({ type: PLACES, text: run.toUpperCase(), position: start + 1 });
      continue;
    }
    throw new NotationError(
      `${JSON.stringify(character)} at position ${index + 1} is not part of place notation`,
      { source: text, position: index + 1 },
    );
  }
  return tokens;
}

/**
 * True when the token stream contains a fold, which means the notation is
 * written as a palindrome and has to be expanded before it can be rung.
 *
 * @param {readonly Token[]} tokens
 * @returns {boolean}
 */
export function hasFold(tokens) {
  return tokens.some((token) => token.type === FOLD);
}

/**
 * Split a token stream at its folds. Notation with no fold gives one section,
 * which keeps the caller free of a special case.
 *
 * @param {readonly Token[]} tokens
 * @returns {Token[][]}
 */
export function splitAtFolds(tokens) {
  /** @type {Token[][]} */
  const sections = [[]];
  for (const token of tokens) {
    if (token.type === FOLD) {
      sections.push([]);
      continue;
    }
    sections[sections.length - 1].push(token);
  }
  return sections;
}

/**
 * Write a token stream back out. Used by the round trip tests and by the error
 * messages, which quote the notation as the tokeniser understood it rather than
 * as the author typed it.
 *
 * @param {readonly Token[]} tokens
 * @returns {string}
 */
export function renderTokens(tokens) {
  let text = '';
  for (const token of tokens) {
    switch (token.type) {
      case CROSS:
        text += 'x';
        break;
      case PLACES:
        text += token.text;
        break;
      case SEPARATOR:
        text += '.';
        break;
      case FOLD:
        text += ',';
        break;
      default:
        throw new RangeError(`unknown token type ${token.type}`);
    }
  }
  return text;
}
