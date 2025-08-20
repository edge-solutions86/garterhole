import { Bell } from './bell.js';
import { isSupportedStage, stageName } from '../core/stage.js';
import { sortBy, sum } from '../util/collections.js';
import { assertNonEmptyString } from '../util/assert.js';
import { slugify } from '../util/text.js';
import { ValidationError, NotFoundError } from '../errors.js';

/**
 * A tower: a ring of bells in a building, with a name and a place.
 *
 * The bells are numbered from the treble and there are no gaps in the
 * numbering, which is checked rather than assumed. A tower record with bells
 * one, two and four in it is a data entry mistake, and finding it here saves
 * the assignment solver from producing a plan nobody can ring.
 */
export class Tower {
  /**
   * @param {{
   *   name: string,
   *   place?: string,
   *   bells: readonly Bell[],
   *   ringingRoom?: string
   * }} definition
   */
  constructor(definition) {
    if (definition === null || typeof definition !== 'object') {
      throw new ValidationError('a tower needs a definition object');
    }
    assertNonEmptyString(definition.name, 'a tower name');
    if (!Array.isArray(definition.bells) || definition.bells.length === 0) {
      throw new ValidationError('a tower needs at least one bell');
    }
    const bells = sortBy(definition.bells, (bell) => bell.number);
    bells.forEach((bell, index) => {
      if (!(bell instanceof Bell)) {
        throw new ValidationError(`entry ${index} of the ring is not a bell`);
      }
      if (bell.number !== index + 1) {
        throw new ValidationError(
          `the ring jumps from bell ${index} to bell ${bell.number}, so it has a gap`,
        );
      }
    });
    this.name = definition.name.trim();
    this.place = definition.place === undefined ? null : definition.place.trim();
    /** @type {readonly Bell[]} */
    this.bells = Object.freeze(bells);
    this.ringingRoom = definition.ringingRoom ?? null;
    Object.freeze(this);
  }

  /**
   * How many bells hang in the tower.
   *
   * @returns {number}
   */
  get size() {
    return this.bells.length;
  }

  /**
   * The tenor, which is the heaviest and lowest bell.
   *
   * @returns {Bell}
   */
  get tenor() {
    return this.bells[this.bells.length - 1];
  }

  /**
   * The treble.
   *
   * @returns {Bell}
   */
  get treble() {
    return this.bells[0];
  }

  /**
   * What the whole ring weighs, in pounds.
   *
   * @returns {number}
   */
  get totalWeightPounds() {
    return sum(this.bells.map((bell) => bell.weightPounds));
  }

  /**
   * A bell by its number.
   *
   * @param {number} number
   * @returns {Bell}
   */
  bell(number) {
    const found = this.bells[number - 1];
    if (found === undefined) {
      throw new NotFoundError('bell', String(number));
    }
    return found;
  }

  /**
   * True when a method at this stage can be rung here at all.
   *
   * @param {number} stage
   * @returns {boolean}
   */
  canRing(stage) {
    return isSupportedStage(stage) && this.size >= stage;
  }

  /**
   * True when the tower has a spare bell to cover behind an odd stage. Bands
   * prefer to cover, but a tower of exactly the stage can still ring it, so
   * this is a separate question from whether the stage is possible.
   *
   * @param {number} stage
   * @returns {boolean}
   */
  canCover(stage) {
    return this.canRing(stage) && stage % 2 === 1 && this.size >= stage + 1;
  }

  /**
   * The stages this tower can ring, largest first, which is what a practice
   * planner works down.
   *
   * @returns {number[]}
   */
  stages() {
    const stages = [];
    for (let stage = this.size; stage >= 3; stage -= 1) {
      if (this.canRing(stage)) {
        stages.push(stage);
      }
    }
    return stages;
  }

  /**
   * The largest stage this tower can ring.
   *
   * @returns {number}
   */
  get largestStage() {
    return this.stages()[0] ?? 0;
  }

  /**
   * An identifier safe in a URL.
   *
   * @returns {string}
   */
  get id() {
    return slugify(this.place === null ? this.name : `${this.place} ${this.name}`);
  }

  /**
   * @returns {string}
   */
  toString() {
    const where = this.place === null ? '' : `, ${this.place}`;
    return `${this.name}${where}: ${this.size} bells, tenor ${this.tenor.weightText}`;
  }

  /**
   * How the tower would describe itself in a directory.
   *
   * @returns {string}
   */
  describe() {
    return `${this.toString()}, ringing up to ${stageName(this.largestStage)}`;
  }
}
