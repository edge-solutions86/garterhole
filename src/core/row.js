import { isSupportedStage } from './stage.js';
import { symbolsFromBells } from './bells.js';
import { assertIntegerInRange, assertNonEmptyArray } from '../util/assert.js';

/**
 * A row is one strike of every bell in the tower, in the order they sound. It
 * is stored as an array of bell numbers indexed by place, so `bells[0]` is the
 * bell leading and `bells[stage - 1]` is the bell lying behind.
 *
 * Rows are immutable. Every operation that would change one returns a new row,
 * which is what lets the prover hold a whole peal of them without copying.
 */
export class Row {
  /**
   * @param {number[]} bells bell numbers in striking order
   */
  constructor(bells) {
    const copy = Object.freeze(Array.from(bells));
    assertPermutation(copy);
    /** @type {readonly number[]} */
    this.bells = copy;
    Object.freeze(this);
  }

  /**
   * The number of bells in the row.
   *
   * @returns {number}
   */
  get stage() {
    return this.bells.length;
  }

  /**
   * The bell striking in a given place, counting places from one.
   *
   * @param {number} place
   * @returns {number}
   */
  bellAt(place) {
    assertIntegerInRange(place, 1, this.stage, 'place');
    return this.bells[place - 1];
  }

  /**
   * The place a given bell is striking in, counting places from one.
   *
   * @param {number} bell
   * @returns {number}
   */
  placeOf(bell) {
    const index = this.bells.indexOf(bell);
    if (index === -1) {
      throw new RangeError(`bell ${bell} is not in a row of ${this.stage}`);
    }
    return index + 1;
  }

  /**
   * True when the row is rounds, that is every bell in its own place.
   *
   * @returns {boolean}
   */
  isRounds() {
    return this.bells.every((bell, index) => bell === index + 1);
  }

  /**
   * @param {Row} other
   * @returns {boolean}
   */
  equals(other) {
    if (!(other instanceof Row) || other.stage !== this.stage) {
      return false;
    }
    for (let i = 0; i < this.bells.length; i += 1) {
      if (this.bells[i] !== other.bells[i]) {
        return false;
      }
    }
    return true;
  }

  /**
   * The row written the way it is printed, one symbol per bell.
   *
   * @returns {string}
   */
  toString() {
    return symbolsFromBells(this.bells);
  }

  /**
   * Rows are used as object keys all over the prover, so they need a cheap
   * stable key. The printed form is already unique for a given stage.
   *
   * @returns {string}
   */
  key() {
    return this.toString();
  }

  /**
   * @returns {number[]} a mutable copy of the bells
   */
  toArray() {
    return Array.from(this.bells);
  }
}

/**
 * Rounds at a stage, the row every touch starts and ends on.
 *
 * @param {number} stage
 * @returns {Row}
 */
export function rounds(stage) {
  if (!isSupportedStage(stage)) {
    throw new RangeError(`stage ${stage} is not supported`);
  }
  const bells = [];
  for (let bell = 1; bell <= stage; bell += 1) {
    bells.push(bell);
  }
  return new Row(bells);
}

/**
 * Check that an array really is a permutation of 1..n and say what is wrong
 * with it when it is not. The message matters because most bad rows come from
 * a ringer typing a touch in by hand.
 *
 * @param {readonly number[]} bells
 */
function assertPermutation(bells) {
  assertNonEmptyArray(bells, 'a row');
  const seen = new Set();
  for (const bell of bells) {
    assertIntegerInRange(bell, 1, bells.length, 'bell');
    if (seen.has(bell)) {
      throw new RangeError(`bell ${bell} appears more than once`);
    }
    seen.add(bell);
  }
}
