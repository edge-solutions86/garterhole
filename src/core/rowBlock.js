import { Row } from './row.js';
import { Change } from './change.js';
import { ValidationError } from '../errors.js';

/**
 * A block of rows is what a touch actually is once it has been rung out. The
 * class keeps the invariant that matters: consecutive rows differ by a single
 * legal change, so a block cannot hold a jump that no band could ring.
 *
 * Blocks are built once and read many times. The prover walks one row by row,
 * the striking analysis lines one up against a stream of strike times, and the
 * music scorer counts patterns in it, so lookup has to be cheap and nothing may
 * mutate underneath those three.
 */
export class RowBlock {
  /**
   * @param {readonly Row[]} rows
   */
  constructor(rows) {
    if (!Array.isArray(rows) || rows.length === 0) {
      throw new ValidationError('a block must contain at least one row');
    }
    const copy = Array.from(rows);
    const stage = copy[0].stage;
    for (const [index, row] of copy.entries()) {
      if (!(row instanceof Row)) {
        throw new ValidationError(`entry ${index} of the block is not a row`);
      }
      if (row.stage !== stage) {
        throw new ValidationError(
          `row ${index} is on ${row.stage} bells but the block is on ${stage}`,
        );
      }
    }
    /** @type {readonly Row[]} */
    this.rows = Object.freeze(copy);
    this.stage = stage;
    Object.freeze(this);
  }

  /**
   * Ring a run of changes from a starting row.
   *
   * @param {Row} start
   * @param {readonly Change[]} changes
   * @returns {RowBlock}
   */
  static fromChanges(start, changes) {
    if (!(start instanceof Row)) {
      throw new ValidationError('a block starts from a row');
    }
    const rows = [start];
    for (const change of changes) {
      rows.push(change.apply(rows[rows.length - 1]));
    }
    return new RowBlock(rows);
  }

  /**
   * How many rows the block holds. A lead of Plain Bob Minor rung from rounds
   * is thirteen rows long, because it holds both the row it started on and the
   * row it finished on.
   *
   * @returns {number}
   */
  get length() {
    return this.rows.length;
  }

  /**
   * The number of changes, which is what a peal length counts.
   *
   * @returns {number}
   */
  get changeCount() {
    return this.rows.length - 1;
  }

  /**
   * @returns {Row}
   */
  first() {
    return this.rows[0];
  }

  /**
   * @returns {Row}
   */
  last() {
    return this.rows[this.rows.length - 1];
  }

  /**
   * @param {number} index
   * @returns {Row}
   */
  at(index) {
    if (!Number.isInteger(index) || index < 0 || index >= this.rows.length) {
      throw new RangeError(`index ${index} is outside 0..${this.rows.length - 1}`);
    }
    return this.rows[index];
  }

  /**
   * The changes that take each row to the next.
   *
   * @returns {Change[]}
   */
  changes() {
    const changes = [];
    for (let index = 1; index < this.rows.length; index += 1) {
      changes.push(changeBetween(this.rows[index - 1], this.rows[index]));
    }
    return changes;
  }

  /**
   * True when no row appears twice. Truth is the property a composition lives
   * or dies by, though the prover has a faster way of deciding it for a whole
   * peal than building a block first.
   *
   * @returns {boolean}
   */
  isTrue() {
    const seen = new Set();
    for (const row of this.rows) {
      const key = row.key();
      if (seen.has(key)) {
        return false;
      }
      seen.add(key);
    }
    return true;
  }

  /**
   * True when the block ends where it started, which is what a touch has to do
   * before anybody will call it rung.
   *
   * @returns {boolean}
   */
  comesRound() {
    return this.first().equals(this.last());
  }

  /**
   * @param {Row} row
   * @returns {boolean}
   */
  contains(row) {
    return this.rows.some((candidate) => candidate.equals(row));
  }

  /**
   * @param {number} start
   * @param {number} [end]
   * @returns {RowBlock}
   */
  slice(start, end) {
    return new RowBlock(this.rows.slice(start, end));
  }

  /**
   * Join two blocks. The second block has to start on the row the first one
   * ended on, and that shared row appears only once in the answer.
   *
   * @param {RowBlock} other
   * @returns {RowBlock}
   */
  concat(other) {
    if (!(other instanceof RowBlock)) {
      throw new ValidationError('a block is joined to another block');
    }
    if (!this.last().equals(other.first())) {
      throw new ValidationError(
        `block ends on ${this.last()} but the next one starts on ${other.first()}`,
      );
    }
    return new RowBlock([...this.rows, ...other.rows.slice(1)]);
  }

  /**
   * @returns {IterableIterator<Row>}
   */
  [Symbol.iterator]() {
    return this.rows[Symbol.iterator]();
  }
}

/**
 * Work out the change that takes one row to the next. Two rows that are not one
 * change apart have no answer, and saying so is more useful than inventing one.
 *
 * @param {Row} from
 * @param {Row} to
 * @returns {Change}
 */
export function changeBetween(from, to) {
  if (from.stage !== to.stage) {
    throw new ValidationError(`cannot change from ${from.stage} bells to ${to.stage}`);
  }
  const places = [];
  let place = 1;
  while (place <= from.stage) {
    if (from.bellAt(place) === to.bellAt(place)) {
      places.push(place);
      place += 1;
      continue;
    }
    if (
      place === from.stage ||
      from.bellAt(place) !== to.bellAt(place + 1) ||
      from.bellAt(place + 1) !== to.bellAt(place)
    ) {
      throw new ValidationError(`${from} and ${to} are not one change apart`);
    }
    place += 2;
  }
  return new Change(from.stage, places);
}
