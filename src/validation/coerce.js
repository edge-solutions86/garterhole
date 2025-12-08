import { ValidationError } from '../errors.js';

/**
 * Turning text into the types the domain wants.
 *
 * Everything that arrives in a query string or an environment variable is text,
 * and most of it means a number or a flag. Doing that conversion by hand at
 * every call site is where the mistakes live: `Number('')` is zero, `Boolean('false')`
 * is true, and both of those have caused real outages in other people's code.
 *
 * So each conversion here is explicit about what it accepts and refuses
 * everything else rather than guessing.
 */

/** The words that mean true, and the ones that mean false. */
const TRUE_WORDS = Object.freeze(['true', 'yes', 'on', '1']);
const FALSE_WORDS = Object.freeze(['false', 'no', 'off', '0']);

/**
 * @param {unknown} value
 * @param {string} what
 * @returns {number}
 */
export function toNumber(value, what = 'the value') {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  if (typeof value !== 'string' || value.trim() === '') {
    throw new ValidationError(`${what} must be a number`);
  }
  const parsed = Number(value.trim());
  if (!Number.isFinite(parsed)) {
    throw new ValidationError(`${what} must be a number, not ${JSON.stringify(value)}`);
  }
  return parsed;
}

/**
 * @param {unknown} value
 * @param {string} what
 * @returns {number}
 */
export function toInteger(value, what = 'the value') {
  const parsed = toNumber(value, what);
  if (!Number.isInteger(parsed)) {
    throw new ValidationError(`${what} must be a whole number, not ${parsed}`);
  }
  return parsed;
}

/**
 * @param {unknown} value
 * @param {string} what
 * @returns {boolean}
 */
export function toBoolean(value, what = 'the value') {
  if (typeof value === 'boolean') {
    return value;
  }
  if (typeof value === 'string') {
    const word = value.trim().toLowerCase();
    if (TRUE_WORDS.includes(word)) {
      return true;
    }
    if (FALSE_WORDS.includes(word)) {
      return false;
    }
  }
  throw new ValidationError(`${what} must be one of ${[...TRUE_WORDS, ...FALSE_WORDS].join(', ')}`);
}

/**
 * A date from the way a form or a query string writes one.
 *
 * @param {unknown} value
 * @param {string} what
 * @returns {Date}
 */
export function toDate(value, what = 'the value') {
  if (value instanceof Date && !Number.isNaN(value.getTime())) {
    return new Date(value.getTime());
  }
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    throw new ValidationError(`${what} must be a date like 2025-05-18`);
  }
  const parsed = new Date(`${value}T00:00:00Z`);
  if (Number.isNaN(parsed.getTime()) || !parsed.toISOString().startsWith(value)) {
    throw new ValidationError(`${what} is not a real date`);
  }
  return parsed;
}

/**
 * A list from a comma separated string, with the blanks dropped.
 *
 * @param {unknown} value
 * @param {string} what
 * @returns {string[]}
 */
export function toList(value, what = 'the value') {
  if (Array.isArray(value)) {
    return value.map((entry) => String(entry));
  }
  if (typeof value !== 'string') {
    throw new ValidationError(`${what} must be a list or a comma separated string`);
  }
  return value
    .split(',')
    .map((entry) => entry.trim())
    .filter((entry) => entry.length > 0);
}

/**
 * Convert a whole bag of query parameters at once, leaving out anything that
 * was not sent.
 *
 * @param {Record<string, unknown>} query
 * @param {Record<string, (value: unknown, what: string) => unknown>} conversions
 * @returns {Record<string, unknown>}
 */
export function convert(query, conversions) {
  if (query === null || typeof query !== 'object') {
    throw new ValidationError('there is nothing to convert');
  }
  const converted = {};
  for (const [name, conversion] of Object.entries(conversions)) {
    const value = query[name];
    if (value === undefined || value === '') {
      continue;
    }
    converted[name] = conversion(value, name);
  }
  return converted;
}
