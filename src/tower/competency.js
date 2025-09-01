import { assertNonEmptyString } from '../util/assert.js';
import { ValidationError } from '../errors.js';

/**
 * Who can ring what.
 *
 * A band's repertoire is the single most useful thing a practice planner has,
 * and the single thing most towers keep only in somebody's head. The register
 * writes it down as a level per ringer per method, so that a plan can be made
 * without ringing round the band first.
 *
 * The levels are ordered, and the order is the point: anything a ringer can do
 * at a higher level they can do at a lower one, which is what lets a query ask
 * for "at least confident" rather than listing the levels it will accept.
 */

/** The levels, from least to most able. */
export const LEVELS = Object.freeze(['learning', 'inside', 'confident', 'conducts']);

/**
 * The rank of a level, for comparing two of them.
 *
 * @param {string} level
 * @returns {number}
 */
export function rankOf(level) {
  const rank = LEVELS.indexOf(level);
  if (rank === -1) {
    throw new ValidationError(`${JSON.stringify(level)} is not a level of competency`);
  }
  return rank;
}

/**
 * True when one level is at least as good as another.
 *
 * @param {string} level
 * @param {string} atLeast
 * @returns {boolean}
 */
export function meets(level, atLeast) {
  return rankOf(level) >= rankOf(atLeast);
}

export class CompetencyRegister {
  constructor() {
    /** @type {Map<string, Map<string, string>>} */
    this.byRinger = new Map();
  }

  /**
   * Write down what a ringer can do, replacing whatever was there before.
   *
   * @param {string} ringerId
   * @param {string} title the method title
   * @param {string} level
   * @returns {CompetencyRegister} this, so that records can be chained
   */
  record(ringerId, title, level) {
    assertNonEmptyString(ringerId, 'a ringer id');
    assertNonEmptyString(title, 'a method title');
    rankOf(level);
    let repertoire = this.byRinger.get(ringerId);
    if (repertoire === undefined) {
      repertoire = new Map();
      this.byRinger.set(ringerId, repertoire);
    }
    repertoire.set(title, level);
    return this;
  }

  /**
   * Take a method out of a ringer's repertoire.
   *
   * @param {string} ringerId
   * @param {string} title
   * @returns {boolean} true when there was something to forget
   */
  forget(ringerId, title) {
    const repertoire = this.byRinger.get(ringerId);
    return repertoire === undefined ? false : repertoire.delete(title);
  }

  /**
   * A ringer's level in a method, or null when they have never rung it.
   *
   * @param {string} ringerId
   * @param {string} title
   * @returns {string | null}
   */
  levelOf(ringerId, title) {
    return this.byRinger.get(ringerId)?.get(title) ?? null;
  }

  /**
   * True when a ringer is at least at a level in a method.
   *
   * @param {string} ringerId
   * @param {string} title
   * @param {string} [atLeast]
   * @returns {boolean}
   */
  canRing(ringerId, title, atLeast = 'inside') {
    const level = this.levelOf(ringerId, title);
    return level !== null && meets(level, atLeast);
  }

  /**
   * Everything a ringer can ring, in title order.
   *
   * @param {string} ringerId
   * @returns {{ title: string, level: string }[]}
   */
  repertoireOf(ringerId) {
    const repertoire = this.byRinger.get(ringerId);
    if (repertoire === undefined) {
      return [];
    }
    return [...repertoire.entries()]
      .map(([title, level]) => ({ title, level }))
      .sort((left, right) => left.title.localeCompare(right.title));
  }

  /**
   * The ringers who can ring a method, in id order.
   *
   * @param {string} title
   * @param {string} [atLeast]
   * @returns {string[]}
   */
  whoCanRing(title, atLeast = 'inside') {
    const found = [];
    for (const ringerId of this.byRinger.keys()) {
      if (this.canRing(ringerId, title, atLeast)) {
        found.push(ringerId);
      }
    }
    return found.sort();
  }

  /**
   * How many of the band can ring each method, which is the table a tower
   * captain uses to decide what to teach next.
   *
   * @param {string} [atLeast]
   * @returns {Map<string, number>}
   */
  coverage(atLeast = 'inside') {
    /** @type {Map<string, number>} */
    const counts = new Map();
    for (const repertoire of this.byRinger.values()) {
      for (const [title, level] of repertoire) {
        if (meets(level, atLeast)) {
          counts.set(title, (counts.get(title) ?? 0) + 1);
        }
      }
    }
    return new Map([...counts.entries()].sort((left, right) => right[1] - left[1]));
  }

  /**
   * Every method anybody in the band has rung.
   *
   * @returns {string[]}
   */
  titles() {
    const titles = new Set();
    for (const repertoire of this.byRinger.values()) {
      for (const title of repertoire.keys()) {
        titles.add(title);
      }
    }
    return [...titles].sort();
  }
}
