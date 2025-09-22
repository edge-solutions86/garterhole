import { classifyLength } from '../composition/lengths.js';
import { parseTitle } from '../method/title.js';
import { assertNonEmptyString } from '../util/assert.js';
import { squash, slugify } from '../util/text.js';
import { ValidationError } from '../errors.js';

/**
 * A performance: a peal, a quarter peal or a touch that somebody wrote down.
 *
 * The record is what goes on the peal board and into the association report, so
 * it holds the band in bell order, the date, the length and the method. What it
 * does not hold is any judgement about whether the performance counts, because
 * that depends on rules that change and is worked out separately.
 */
export class Performance {
  /**
   * @param {{
   *   tower: string,
   *   date: Date,
   *   title: string,
   *   changes: number,
   *   band: readonly string[],
   *   conductor?: string,
   *   composer?: string,
   *   duration?: number,
   *   footnotes?: readonly string[]
   * }} definition
   */
  constructor(definition) {
    if (definition === null || typeof definition !== 'object') {
      throw new ValidationError('a performance needs a definition object');
    }
    assertNonEmptyString(definition.tower, 'a tower name');
    assertNonEmptyString(definition.title, 'a method title');
    if (!(definition.date instanceof Date) || Number.isNaN(definition.date.getTime())) {
      throw new ValidationError('a performance needs the date it was rung');
    }
    if (!Number.isInteger(definition.changes) || definition.changes < 1) {
      throw new ValidationError(`${definition.changes} is not a number of changes`);
    }
    if (!Array.isArray(definition.band) || definition.band.length === 0) {
      throw new ValidationError('a performance needs a band');
    }
    const band = definition.band.map((name, index) => {
      assertNonEmptyString(name, `the ringer of bell ${index + 1}`);
      return squash(name);
    });
    if (definition.conductor !== undefined && !band.includes(squash(definition.conductor))) {
      throw new ValidationError(
        `the conductor ${JSON.stringify(definition.conductor)} is not in the band`,
      );
    }
    if (
      definition.duration !== undefined &&
      (!Number.isInteger(definition.duration) || definition.duration < 1)
    ) {
      throw new ValidationError(`${definition.duration} is not a length of time in minutes`);
    }
    this.tower = squash(definition.tower);
    this.date = new Date(definition.date.getTime());
    this.title = squash(definition.title);
    this.changes = definition.changes;
    /** @type {readonly string[]} */
    this.band = Object.freeze(band);
    this.conductor = definition.conductor === undefined ? null : squash(definition.conductor);
    this.composer = definition.composer === undefined ? null : squash(definition.composer);
    this.duration = definition.duration ?? null;
    /** @type {readonly string[]} */
    this.footnotes = Object.freeze([...(definition.footnotes ?? [])]);
    Object.freeze(this);
  }

  /**
   * The stage, read out of the method title.
   *
   * @returns {number}
   */
  get stage() {
    return parseTitle(this.title).stage;
  }

  /**
   * What the length counts as.
   *
   * @returns {string}
   */
  get kind() {
    return classifyLength(this.changes, this.stage);
  }

  /**
   * How many rang, which is not always the stage: an odd stage has a cover.
   *
   * @returns {number}
   */
  get bandSize() {
    return this.band.length;
  }

  /**
   * The date as it is written on a peal board.
   *
   * @returns {string}
   */
  get dateText() {
    return this.date.toISOString().slice(0, 10);
  }

  /**
   * A stable identifier, so that the same performance filed twice is caught.
   *
   * @returns {string}
   */
  get id() {
    return slugify(`${this.dateText} ${this.tower} ${this.title} ${this.changes}`);
  }

  /**
   * The ringer of a bell.
   *
   * @param {number} bell
   * @returns {string}
   */
  ringerOf(bell) {
    if (!Number.isInteger(bell) || bell < 1 || bell > this.band.length) {
      throw new ValidationError(`bell ${bell} was not rung in this performance`);
    }
    return this.band[bell - 1];
  }

  /**
   * The bell somebody rang, or null when they were not in the band.
   *
   * @param {string} name
   * @returns {number | null}
   */
  bellOf(name) {
    const index = this.band.indexOf(squash(name));
    return index === -1 ? null : index + 1;
  }

  /**
   * @returns {string}
   */
  toString() {
    return `${this.tower}, ${this.dateText}: ${this.changes} ${this.title}`;
  }
}

/**
 * Build a performance from the fields a form collects.
 *
 * @param {object} fields
 * @returns {Performance}
 */
export function performance(fields) {
  return new Performance(fields);
}
