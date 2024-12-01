import { Row } from './row.js';
import { bellFromSymbol, isBellSymbol } from './bells.js';
import { isSupportedStage } from './stage.js';
import { assertNonEmptyString } from '../util/assert.js';

/**
 * Rows arrive as text far more often than as arrays: out of a composition
 * file, out of a form field, out of a message from another ringer. Parsing is
 * separated from the `Row` class so that the class stays a value object and
 * every complaint about bad input is raised in one place.
 */

const SEPARATOR = /[\s,;]+/;

/**
 * Read a single row.
 *
 * @param {string} text the row as printed, for example `13527486`
 * @param {{ stage?: number }} [options]
 * @returns {Row}
 */
export function parseRow(text, options = {}) {
  assertNonEmptyString(text, 'a row');
  const trimmed = text.trim();
  if (trimmed.length === 0) {
    throw new SyntaxError('a row must not be blank');
  }
  const bells = [];
  for (let index = 0; index < trimmed.length; index += 1) {
    const symbol = trimmed[index];
    if (!isBellSymbol(symbol)) {
      throw new SyntaxError(
        `${JSON.stringify(symbol)} at position ${index + 1} of ${JSON.stringify(trimmed)} is not a bell`,
      );
    }
    bells.push(bellFromSymbol(symbol));
  }
  if (options.stage !== undefined) {
    if (!isSupportedStage(options.stage)) {
      throw new RangeError(`stage ${options.stage} is not supported`);
    }
    if (bells.length !== options.stage) {
      throw new SyntaxError(
        `row ${JSON.stringify(trimmed)} has ${bells.length} bells but the stage is ${options.stage}`,
      );
    }
  }
  return new Row(bells);
}

/**
 * Read a block of rows separated by whitespace, commas or semicolons. Blank
 * input gives an empty array rather than an error, because an empty block is a
 * legitimate thing for a composition to contain.
 *
 * @param {string} text
 * @param {{ stage?: number }} [options]
 * @returns {Row[]}
 */
export function parseRows(text, options = {}) {
  if (typeof text !== 'string') {
    throw new TypeError('a block of rows must be a string');
  }
  const parts = text.split(SEPARATOR).filter((part) => part.length > 0);
  if (parts.length === 0) {
    return [];
  }
  const stage = options.stage ?? parseRow(parts[0]).stage;
  return parts.map((part) => parseRow(part, { stage }));
}

/**
 * Try to read a row and report the failure rather than throwing. Used by the
 * HTTP layer, which wants to collect every bad field in a request before it
 * answers.
 *
 * @param {string} text
 * @param {{ stage?: number }} [options]
 * @returns {{ ok: true, row: Row } | { ok: false, message: string }}
 */
export function tryParseRow(text, options = {}) {
  try {
    return { ok: true, row: parseRow(text, options) };
  } catch (error) {
    return { ok: false, message: error.message };
  }
}
