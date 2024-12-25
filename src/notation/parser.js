import { Change } from '../core/change.js';
import { bellsFromSymbols } from '../core/bells.js';
import { isSupportedStage } from '../core/stage.js';
import { NotationError } from '../errors.js';
import { tokenize, splitAtFolds, CROSS, PLACES, SEPARATOR } from './tokenizer.js';

/**
 * Turn a token stream into changes. The parser is deliberately strict about
 * separators: a full stop between two runs of places is the only thing that
 * says where one run ends, so notation that leaves it out is ambiguous rather
 * than merely untidy and is refused.
 */

/**
 * Parse one section, that is a run of notation with no comma in it.
 *
 * @param {readonly import('./tokenizer.js').Token[]} tokens
 * @param {number} stage
 * @returns {Change[]}
 */
export function parseSection(tokens, stage) {
  assertStage(stage);
  const changes = [];
  let previous = null;
  for (const token of tokens) {
    if (token.type === SEPARATOR) {
      if (previous === null) {
        throw new NotationError('place notation cannot start with a separator', {
          position: token.position,
        });
      }
      if (previous.type === SEPARATOR) {
        throw new NotationError('place notation has two separators in a row', {
          position: token.position,
        });
      }
      previous = token;
      continue;
    }
    changes.push(changeFromToken(token, stage));
    previous = token;
  }
  if (previous !== null && previous.type === SEPARATOR) {
    throw new NotationError('place notation cannot end with a separator', {
      position: previous.position,
    });
  }
  return changes;
}

/**
 * Parse a whole notation string, keeping the sections the commas divide it
 * into. Nothing is expanded here; a palindromic method still has to be folded
 * out before it can be rung.
 *
 * @param {string} text
 * @param {number} stage
 * @returns {{ sections: Change[][], folded: boolean, source: string }}
 */
export function parseNotation(text, stage) {
  assertStage(stage);
  const tokens = tokenize(text);
  const sections = splitAtFolds(tokens);
  const parsed = sections.map((section, index) => {
    if (section.length === 0) {
      throw new NotationError(`section ${index + 1} of the notation is empty`, { source: text });
    }
    return parseSection(section, stage);
  });
  return { sections: parsed, folded: parsed.length > 1, source: text };
}

/**
 * The common case: notation with no comma, read straight through.
 *
 * @param {string} text
 * @param {number} stage
 * @returns {Change[]}
 */
export function parseChanges(text, stage) {
  const parsed = parseNotation(text, stage);
  if (parsed.folded) {
    throw new NotationError('this notation has a fold and has to be expanded first', {
      source: text,
    });
  }
  return parsed.sections[0];
}

/**
 * @param {import('./tokenizer.js').Token} token
 * @param {number} stage
 * @returns {Change}
 */
function changeFromToken(token, stage) {
  if (token.type === CROSS) {
    return buildChange(stage, [], token);
  }
  if (token.type === PLACES) {
    return buildChange(stage, bellsFromSymbols(token.text), token);
  }
  throw new NotationError(`unexpected ${token.type} in place notation`, {
    position: token.position,
  });
}

/**
 * Build a change and turn any complaint from the core into a notation error
 * that names where in the source the trouble is.
 *
 * @param {number} stage
 * @param {number[]} places
 * @param {import('./tokenizer.js').Token} token
 * @returns {Change}
 */
function buildChange(stage, places, token) {
  try {
    return Change.withImpliedPlaces(stage, places);
  } catch (error) {
    throw new NotationError(`${JSON.stringify(token.text)}: ${error.message}`, {
      position: token.position,
      details: { stage, places },
    });
  }
}

/**
 * @param {number} stage
 */
function assertStage(stage) {
  if (!isSupportedStage(stage)) {
    throw new RangeError(`stage ${stage} is not supported`);
  }
}
