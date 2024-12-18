import { Row } from './row.js';
import { bellSymbol } from './bells.js';
import { isSupportedStage } from './stage.js';
import { NotationError } from '../errors.js';

/**
 * A change is what happens between one row and the next. Every bell either
 * stays where it is, which ringers call making a place, or swaps with the bell
 * next to it. A change is therefore completely described by the set of places
 * that are made.
 *
 * The awkward part is that the places have to leave an even number of bells in
 * every gap, because the bells in a gap can only pair off. A change that does
 * not is not a change at all, and this class refuses to build one.
 */
export class Change {
  /**
   * @param {number} stage
   * @param {readonly number[]} places places made, one based, ascending
   */
  constructor(stage, places) {
    if (!isSupportedStage(stage)) {
      throw new RangeError(`stage ${stage} is not supported`);
    }
    const sorted = normalisePlaces(stage, places);
    assertPairable(stage, sorted);
    this.stage = stage;
    /** @type {readonly number[]} */
    this.places = Object.freeze(sorted);
    Object.freeze(this);
  }

  /**
   * The change where nothing makes a place and every bell swaps. Only possible
   * at an even stage.
   *
   * @param {number} stage
   * @returns {Change}
   */
  static cross(stage) {
    return new Change(stage, []);
  }

  /**
   * Build a change, filling in the places at the front and the back that the
   * printed notation leaves out. Writing `1` on minor means places one and six,
   * because otherwise the five bells above the treble cannot pair off.
   *
   * @param {number} stage
   * @param {readonly number[]} places
   * @returns {Change}
   */
  static withImpliedPlaces(stage, places) {
    if (!isSupportedStage(stage)) {
      throw new RangeError(`stage ${stage} is not supported`);
    }
    const sorted = normalisePlaces(stage, places);
    const completed = [...sorted];
    if (completed.length === 0) {
      if (stage % 2 === 1) {
        completed.push(stage);
      }
    } else {
      if ((completed[0] - 1) % 2 === 1) {
        completed.unshift(1);
      }
      if ((stage - completed[completed.length - 1]) % 2 === 1) {
        completed.push(stage);
      }
    }
    return new Change(stage, completed);
  }

  /**
   * True when no bell makes a place.
   *
   * @returns {boolean}
   */
  isCross() {
    return this.places.length === 0;
  }

  /**
   * @param {number} place
   * @returns {boolean} true when a place is made there
   */
  makesPlace(place) {
    return this.places.includes(place);
  }

  /**
   * Ring the change, giving the row that follows the one supplied.
   *
   * @param {Row} row
   * @returns {Row}
   */
  apply(row) {
    if (!(row instanceof Row)) {
      throw new TypeError('a change is applied to a Row');
    }
    if (row.stage !== this.stage) {
      throw new RangeError(`a change on ${this.stage} cannot be applied to a row of ${row.stage}`);
    }
    const bells = row.toArray();
    let place = 1;
    while (place <= this.stage) {
      if (this.makesPlace(place)) {
        place += 1;
        continue;
      }
      const left = place - 1;
      const right = place;
      const held = bells[left];
      bells[left] = bells[right];
      bells[right] = held;
      place += 2;
    }
    return new Row(bells);
  }

  /**
   * @param {Change} other
   * @returns {boolean}
   */
  equals(other) {
    if (!(other instanceof Change) || other.stage !== this.stage) {
      return false;
    }
    if (other.places.length !== this.places.length) {
      return false;
    }
    return this.places.every((place, index) => place === other.places[index]);
  }

  /**
   * The change written as place notation, using `x` for a cross change.
   *
   * @returns {string}
   */
  toString() {
    if (this.isCross()) {
      return 'x';
    }
    return this.places.map((place) => bellSymbol(place)).join('');
  }
}

/**
 * @param {number} stage
 * @param {readonly number[]} places
 * @returns {number[]} ascending, with duplicates removed
 */
function normalisePlaces(stage, places) {
  if (!Array.isArray(places)) {
    throw new TypeError('places must be an array');
  }
  const seen = new Set();
  for (const place of places) {
    if (!Number.isInteger(place) || place < 1 || place > stage) {
      throw new RangeError(`place ${place} is outside 1..${stage}`);
    }
    seen.add(place);
  }
  return [...seen].sort((a, b) => a - b);
}

/**
 * Every run of bells between two made places has to be even, or the bells in it
 * cannot swap in pairs.
 *
 * @param {number} stage
 * @param {readonly number[]} places
 */
function assertPairable(stage, places) {
  const written = places.length === 0 ? 'a cross change' : `places ${places.join(',')}`;
  let previous = 0;
  for (const place of places) {
    const gap = place - previous - 1;
    if (gap % 2 === 1) {
      const where =
        previous === 0 ? `below place ${place}` : `between places ${previous} and ${place}`;
      throw new NotationError(`${written} would leave ${bellCount(gap)} ${where} on ${stage}`, {
        details: { stage, places: [...places], gap },
      });
    }
    previous = place;
  }
  const tail = stage - previous;
  if (tail % 2 === 1) {
    const where = previous === 0 ? 'in the change' : `above place ${previous}`;
    throw new NotationError(`${written} would leave ${bellCount(tail)} ${where} on ${stage}`, {
      details: { stage, places: [...places], gap: tail },
    });
  }
}

/**
 * @param {number} count
 * @returns {string}
 */
function bellCount(count) {
  return count === 1 ? '1 bell' : `${count} bells`;
}
