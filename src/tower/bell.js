import { assertNonEmptyString } from '../util/assert.js';
import { ValidationError } from '../errors.js';

/**
 * One bell in a tower.
 *
 * Weight is in hundredweight, quarters and pounds, because that is what is cast
 * on the bell and what every ringer quotes. Storing it as pounds and converting
 * on the way out loses nothing and stops the arithmetic going wrong: a tenor of
 * twenty three hundredweight two quarters and eleven pounds is 2647 pounds, and
 * that is a number that can be added up.
 */

/** Pounds in a hundredweight. */
export const POUNDS_PER_HUNDREDWEIGHT = 112;
/** Pounds in a quarter. */
export const POUNDS_PER_QUARTER = 28;

/**
 * The notes a bell can be tuned to, sharps only, as they are written on a
 * tower's board.
 */
const NOTES = Object.freeze(['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B']);

export class Bell {
  /**
   * @param {{
   *   number: number,
   *   weightPounds: number,
   *   note?: string,
   *   founder?: string,
   *   cast?: number
   * }} definition
   */
  constructor(definition) {
    if (definition === null || typeof definition !== 'object') {
      throw new ValidationError('a bell needs a definition object');
    }
    const { number, weightPounds } = definition;
    if (!Number.isInteger(number) || number < 1) {
      throw new ValidationError(`${number} is not a bell number`);
    }
    if (typeof weightPounds !== 'number' || !(weightPounds > 0)) {
      throw new ValidationError(`a bell cannot weigh ${weightPounds} pounds`);
    }
    if (definition.note !== undefined && !NOTES.includes(definition.note)) {
      throw new ValidationError(`${JSON.stringify(definition.note)} is not a note`);
    }
    if (
      definition.cast !== undefined &&
      (!Number.isInteger(definition.cast) || definition.cast < 1200 || definition.cast > 2200)
    ) {
      throw new ValidationError(`${definition.cast} is not a year a bell was cast in`);
    }
    this.number = number;
    this.weightPounds = weightPounds;
    this.note = definition.note ?? null;
    this.founder = definition.founder === undefined ? null : definition.founder;
    this.cast = definition.cast ?? null;
    Object.freeze(this);
  }

  /**
   * The weight broken into hundredweight, quarters and pounds.
   *
   * @returns {{ hundredweight: number, quarters: number, pounds: number }}
   */
  get weight() {
    let left = Math.round(this.weightPounds);
    const hundredweight = Math.floor(left / POUNDS_PER_HUNDREDWEIGHT);
    left -= hundredweight * POUNDS_PER_HUNDREDWEIGHT;
    const quarters = Math.floor(left / POUNDS_PER_QUARTER);
    return { hundredweight, quarters, pounds: left - quarters * POUNDS_PER_QUARTER };
  }

  /**
   * The weight written the way a tower board writes it.
   *
   * @returns {string}
   */
  get weightText() {
    const { hundredweight, quarters, pounds } = this.weight;
    return `${hundredweight}-${quarters}-${pounds}`;
  }

  /**
   * @returns {string}
   */
  toString() {
    const note = this.note === null ? '' : ` in ${this.note}`;
    return `bell ${this.number}, ${this.weightText}${note}`;
  }
}

/**
 * Build a bell from a weight written the way a tower board writes it.
 *
 * @param {number} number
 * @param {string} weight for example `23-2-11`
 * @param {{ note?: string, founder?: string, cast?: number }} [details]
 * @returns {Bell}
 */
export function bellWeighing(number, weight, details = {}) {
  assertNonEmptyString(weight, 'a bell weight');
  const parts = weight.split('-');
  if (parts.length !== 3) {
    throw new ValidationError(
      `${JSON.stringify(weight)} is not a weight in hundredweight, quarters and pounds`,
    );
  }
  const [hundredweight, quarters, pounds] = parts.map((part) => Number(part));
  for (const part of [hundredweight, quarters, pounds]) {
    if (!Number.isInteger(part) || part < 0) {
      throw new ValidationError(`${JSON.stringify(weight)} has a part that is not a whole number`);
    }
  }
  if (quarters > 3) {
    throw new ValidationError(`${quarters} quarters is more than a hundredweight`);
  }
  if (pounds > 27) {
    throw new ValidationError(`${pounds} pounds is more than a quarter`);
  }
  return new Bell({
    number,
    weightPounds: hundredweight * POUNDS_PER_HUNDREDWEIGHT + quarters * POUNDS_PER_QUARTER + pounds,
    ...details,
  });
}

/**
 * Every note a bell may be tuned to.
 *
 * @returns {string[]}
 */
export function notes() {
  return [...NOTES];
}
