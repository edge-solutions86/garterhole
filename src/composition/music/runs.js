import { Row } from '../../core/row.js';
import { ValidationError } from '../../errors.js';

/**
 * Runs are the music that bands notice first. Four bells in order at the back,
 * `5678`, is a run of four, and a composition is judged largely on how many of
 * those it has.
 *
 * A run counts whether it goes up or down, and it counts at the front of the
 * row as well as the back. The two ends are counted separately because a
 * composer will happily trade one for the other.
 */

/**
 * The length of the run at the back of a row, counting from the tenor end. A
 * row with no run at all still has a run of one, so the smallest interesting
 * answer is two.
 *
 * @param {Row} row
 * @returns {number}
 */
export function runLengthAtBack(row) {
  assertRow(row);
  return longestRunEndingAt(row.bells, row.stage - 1, -1);
}

/**
 * The length of the run at the front of a row.
 *
 * @param {Row} row
 * @returns {number}
 */
export function runLengthAtFront(row) {
  assertRow(row);
  return longestRunEndingAt(row.bells, 0, 1);
}

/**
 * True when the row has a run of at least the given length at the back.
 *
 * @param {Row} row
 * @param {number} length
 * @returns {boolean}
 */
export function hasRunAtBack(row, length) {
  return runLengthAtBack(row) >= assertLength(length);
}

/**
 * True when the row has a run of at least the given length at the front.
 *
 * @param {Row} row
 * @param {number} length
 * @returns {boolean}
 */
export function hasRunAtFront(row, length) {
  return runLengthAtFront(row) >= assertLength(length);
}

/**
 * Count the rows of a block that have a run of at least the given length,
 * split by which end the run is at. A row with a run at both ends is counted
 * once in each.
 *
 * @param {Iterable<Row>} rows
 * @param {number} length
 * @returns {{ front: number, back: number, total: number }}
 */
export function countRuns(rows, length) {
  assertLength(length);
  let front = 0;
  let back = 0;
  let total = 0;
  for (const row of rows) {
    const atFront = hasRunAtFront(row, length);
    const atBack = hasRunAtBack(row, length);
    if (atFront) {
      front += 1;
    }
    if (atBack) {
      back += 1;
    }
    if (atFront || atBack) {
      total += 1;
    }
  }
  return { front, back, total };
}

/**
 * Count runs of every length from four up to the stage, which is the table a
 * composition is usually judged on.
 *
 * @param {Iterable<Row>} rows
 * @param {number} stage
 * @param {number} [shortest]
 * @returns {Map<number, { front: number, back: number, total: number }>}
 */
export function runTable(rows, stage, shortest = 4) {
  const kept = [...rows];
  const table = new Map();
  for (let length = shortest; length <= stage; length += 1) {
    table.set(length, countRuns(kept, length));
  }
  return table;
}

/**
 * Walk out from one end of the row for as long as the bells keep going up by
 * one or down by one.
 *
 * @param {readonly number[]} bells
 * @param {number} start the index to start from
 * @param {number} step which way to walk
 * @returns {number}
 */
function longestRunEndingAt(bells, start, step) {
  return Math.max(runFrom(bells, start, step, 1), runFrom(bells, start, step, -1));
}

/**
 * @param {readonly number[]} bells
 * @param {number} start
 * @param {number} step
 * @param {number} delta the change in bell number expected at each step
 * @returns {number}
 */
function runFrom(bells, start, step, delta) {
  let length = 1;
  let index = start;
  while (index + step >= 0 && index + step < bells.length) {
    if (bells[index + step] !== bells[index] + delta) {
      break;
    }
    length += 1;
    index += step;
  }
  return length;
}

/**
 * @param {unknown} row
 */
function assertRow(row) {
  if (!(row instanceof Row)) {
    throw new ValidationError('a Row is needed to look for runs in it');
  }
}

/**
 * @param {number} length
 * @returns {number}
 */
function assertLength(length) {
  if (!Number.isInteger(length) || length < 2) {
    throw new ValidationError(`a run of ${length} is not something to count`);
  }
  return length;
}
