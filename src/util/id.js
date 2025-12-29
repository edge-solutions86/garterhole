import { ValidationError } from '../errors.js';

/**
 * Identifiers.
 *
 * Two kinds are needed and they are not the same thing. A record filed by a
 * person wants an identifier built from what it is, so that filing the same
 * performance twice is caught. A request passing through the service wants one
 * that is unique and sorts by when it was made, so that a log can be read in
 * order.
 *
 * The second kind is built here: a timestamp in base thirty six followed by
 * some randomness. Sorting by the string sorts by time, which is worth more in
 * a log than the extra few bits a random identifier would have.
 */

/**
 * The alphabet the random tail is drawn from. It leaves out the characters
 * people misread when they read an identifier out over the phone, which happens
 * more often than it should.
 *
 * The timestamp in front is written in base thirty six and therefore uses the
 * whole of `0-9a-z`, so the two halves are checked against different sets.
 */
const ALPHABET = '0123456789abcdefghjkmnpqrstvwxyz';

/** Everything base thirty six can produce. */
const BASE_36 = /^[0-9a-z]+$/;

/**
 * A generator of identifiers, taking its clock and its randomness so that a
 * test can make it produce a known sequence.
 */
export class IdSource {
  /**
   * @param {{ now?: () => number, random?: () => number, length?: number }} [options]
   */
  constructor(options = {}) {
    this.now = options.now ?? (() => Date.now());
    this.random = options.random ?? Math.random;
    this.length = options.length ?? 8;
    if (!Number.isInteger(this.length) || this.length < 4 || this.length > 32) {
      throw new ValidationError(`an identifier cannot have ${this.length} random characters`);
    }
  }

  /**
   * The next identifier.
   *
   * @param {string} [prefix]
   * @returns {string}
   */
  next(prefix = '') {
    const stamp = Math.floor(this.now()).toString(36).padStart(9, '0');
    let tail = '';
    for (let index = 0; index < this.length; index += 1) {
      const pick = Math.floor(this.random() * ALPHABET.length);
      tail += ALPHABET[Math.min(Math.max(pick, 0), ALPHABET.length - 1)];
    }
    return prefix === '' ? `${stamp}${tail}` : `${prefix}_${stamp}${tail}`;
  }

  /**
   * Several identifiers at once.
   *
   * @param {number} count
   * @param {string} [prefix]
   * @returns {string[]}
   */
  many(count, prefix = '') {
    if (!Number.isInteger(count) || count < 0) {
      throw new ValidationError(`${count} is not a number of identifiers`);
    }
    const ids = [];
    for (let index = 0; index < count; index += 1) {
      ids.push(this.next(prefix));
    }
    return ids;
  }
}

/**
 * The source the service uses when nobody supplies one.
 *
 * @returns {IdSource}
 */
export function defaultIds() {
  return new IdSource();
}

/**
 * True when a string looks like one of these identifiers.
 *
 * @param {string} id
 * @returns {boolean}
 */
export function looksLikeId(id) {
  if (typeof id !== 'string') {
    return false;
  }
  const body = id.includes('_') ? id.slice(id.indexOf('_') + 1) : id;
  return body.length >= 13 && BASE_36.test(body);
}

/**
 * The moment an identifier was made, or null when it does not carry one.
 *
 * @param {string} id
 * @returns {Date | null}
 */
export function madeAt(id) {
  if (!looksLikeId(id)) {
    return null;
  }
  const body = id.includes('_') ? id.slice(id.indexOf('_') + 1) : id;
  const stamp = Number.parseInt(body.slice(0, 9), 36);
  return Number.isFinite(stamp) ? new Date(stamp) : null;
}
