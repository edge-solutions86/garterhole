import { Row } from '../../core/row.js';
import { bellFromSymbol, isBellSymbol, bellSymbol } from '../../core/bells.js';
import { NotationError, ValidationError } from '../../errors.js';

/**
 * Musical patterns, written the way a composer writes them in a search.
 *
 * A pattern is a run of bell symbols with `*` standing for any number of bells
 * and `?` for exactly one. `*5678` is any row ending in the four tenors in
 * order, `56*` is any row that starts with the five and the six, and `*8*` is
 * any row at all on eight bells.
 *
 * There is no regular expression underneath. Patterns are matched by walking
 * the row, which keeps the meaning of `*` obvious and stops a stray character
 * turning into a regular expression operator.
 */

/** Any number of bells, including none. */
const ANY_RUN = '*';
/** Exactly one bell. */
const ANY_ONE = '?';

/**
 * @typedef {{ kind: 'bell', bell: number } | { kind: 'any' } | { kind: 'run' }} PatternPart
 */

/**
 * Read a pattern into its parts.
 *
 * @param {string} pattern
 * @returns {PatternPart[]}
 */
export function parsePattern(pattern) {
  if (typeof pattern !== 'string' || pattern.length === 0) {
    throw new ValidationError('a pattern must be a non empty string');
  }
  /** @type {PatternPart[]} */
  const parts = [];
  for (let index = 0; index < pattern.length; index += 1) {
    const character = pattern[index];
    if (character === ANY_RUN) {
      parts.push({ kind: 'run' });
      continue;
    }
    if (character === ANY_ONE) {
      parts.push({ kind: 'any' });
      continue;
    }
    if (isBellSymbol(character)) {
      parts.push({ kind: 'bell', bell: bellFromSymbol(character) });
      continue;
    }
    throw new NotationError(
      `${JSON.stringify(character)} at position ${index + 1} is not part of a pattern`,
      { source: pattern, position: index + 1 },
    );
  }
  return parts;
}

/**
 * True when a row matches a pattern.
 *
 * @param {Row} row
 * @param {string | PatternPart[]} pattern
 * @returns {boolean}
 */
export function matchesPattern(row, pattern) {
  if (!(row instanceof Row)) {
    throw new ValidationError('a Row is needed to match a pattern against');
  }
  const parts = Array.isArray(pattern) ? pattern : parsePattern(pattern);
  return matchFrom(row.bells, 0, parts, 0);
}

/**
 * Count the rows that match a pattern.
 *
 * @param {Iterable<Row>} rows
 * @param {string} pattern
 * @returns {number}
 */
export function countPattern(rows, pattern) {
  const parts = parsePattern(pattern);
  let count = 0;
  for (const row of rows) {
    if (matchesPattern(row, parts)) {
      count += 1;
    }
  }
  return count;
}

/**
 * The rows that match a pattern, with the index each one was at.
 *
 * @param {Iterable<Row>} rows
 * @param {string} pattern
 * @returns {{ index: number, row: Row }[]}
 */
export function findPattern(rows, pattern) {
  const parts = parsePattern(pattern);
  const found = [];
  let index = 0;
  for (const row of rows) {
    if (matchesPattern(row, parts)) {
      found.push({ index, row });
    }
    index += 1;
  }
  return found;
}

/**
 * The patterns a composer usually asks for at a stage: the tenors together at
 * the back, the little bells at the front, and the four four bell runs.
 *
 * @param {number} stage
 * @returns {Map<string, string>} the name of each pattern against the pattern
 */
export function commonPatterns(stage) {
  if (!Number.isInteger(stage) || stage < 4) {
    throw new ValidationError(`there are no common patterns on ${stage}`);
  }
  const patterns = new Map();
  const back = [];
  for (let bell = stage - 3; bell <= stage; bell += 1) {
    back.push(bellSymbol(bell));
  }
  patterns.set(`${back.join('')} at the back`, `*${back.join('')}`);
  patterns.set(`${[...back].reverse().join('')} at the back`, `*${[...back].reverse().join('')}`);
  const front = [];
  for (let bell = 1; bell <= 4; bell += 1) {
    front.push(bellSymbol(bell));
  }
  patterns.set(`${front.join('')} at the front`, `${front.join('')}*`);
  patterns.set('tenors together', `*${bellSymbol(stage - 1)}${bellSymbol(stage)}`);
  return patterns;
}

/**
 * Match a pattern against the bells, walking both at once. A run may swallow
 * any number of bells, so it tries the shortest match first and lengthens it
 * until the rest of the pattern fits.
 *
 * @param {readonly number[]} bells
 * @param {number} bellIndex
 * @param {readonly PatternPart[]} parts
 * @param {number} partIndex
 * @returns {boolean}
 */
function matchFrom(bells, bellIndex, parts, partIndex) {
  if (partIndex === parts.length) {
    return bellIndex === bells.length;
  }
  const part = parts[partIndex];
  if (part.kind === 'run') {
    for (let taken = 0; bellIndex + taken <= bells.length; taken += 1) {
      if (matchFrom(bells, bellIndex + taken, parts, partIndex + 1)) {
        return true;
      }
    }
    return false;
  }
  if (bellIndex >= bells.length) {
    return false;
  }
  if (part.kind === 'any') {
    return matchFrom(bells, bellIndex + 1, parts, partIndex + 1);
  }
  return part.bell === bells[bellIndex] && matchFrom(bells, bellIndex + 1, parts, partIndex + 1);
}
