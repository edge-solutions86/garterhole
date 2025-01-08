import { Row } from './row.js';

/**
 * A row is a permutation, and every permutation splits into cycles. The cycle
 * structure answers three questions the rest of the library keeps asking: how
 * many parts a composition has, whether a row can be reached at a handstroke,
 * and which bells a part end keeps together.
 */

/**
 * Split a row into its cycles. Each cycle is a list of bells, starting with the
 * lowest numbered bell in that cycle, and the cycles come out ordered by that
 * first bell. Bells that stay in their own place appear as cycles of one.
 *
 * @param {Row} row
 * @returns {number[][]}
 */
export function cycles(row) {
  assertRow(row);
  const seen = new Set();
  const found = [];
  for (let bell = 1; bell <= row.stage; bell += 1) {
    if (seen.has(bell)) {
      continue;
    }
    const cycle = [];
    let current = bell;
    while (!seen.has(current)) {
      seen.add(current);
      cycle.push(current);
      current = row.bellAt(current);
    }
    found.push(cycle);
  }
  return found;
}

/**
 * The lengths of the cycles, longest first. This is the shape of the
 * permutation, and two rows with the same shape behave the same way when they
 * are used as part ends.
 *
 * @param {Row} row
 * @returns {number[]}
 */
export function cycleLengths(row) {
  return cycles(row)
    .map((cycle) => cycle.length)
    .sort((a, b) => b - a);
}

/**
 * How many times a row has to be applied to itself before it comes back to
 * rounds, worked out from the cycle lengths rather than by walking the group.
 *
 * @param {Row} row
 * @returns {number}
 */
export function orderOf(row) {
  return cycleLengths(row).reduce((total, length) => leastCommonMultiple(total, length), 1);
}

/**
 * The parity of a row: even when it takes an even number of swaps to get back
 * to rounds, odd otherwise.
 *
 * @param {Row} row
 * @returns {'even' | 'odd'}
 */
export function parity(row) {
  const swaps = cycles(row).reduce((total, cycle) => total + cycle.length - 1, 0);
  return swaps % 2 === 0 ? 'even' : 'odd';
}

/**
 * True when the row is in course. Composers split the course heads of a method
 * into the in course half and the out of course half, and a call that changes
 * the parity moves between the two halves.
 *
 * Note that parity does not simply alternate row by row. A change swaps
 * `(stage - places) / 2` pairs, and that count is odd for some changes and even
 * for others, so a lead of Plain Bob Minor moves between the halves at the
 * cross changes and stays put at the place changes.
 *
 * @param {Row} row
 * @returns {boolean}
 */
export function isInCourse(row) {
  return parity(row) === 'even';
}

/**
 * The bells that stay in their own place. On a part end these are the bells the
 * composition never moves, which is usually the treble and the tenors.
 *
 * @param {Row} row
 * @returns {number[]}
 */
export function fixedBells(row) {
  const fixed = [];
  for (let bell = 1; bell <= row.stage; bell += 1) {
    if (row.bellAt(bell) === bell) {
      fixed.push(bell);
    }
  }
  return fixed;
}

/**
 * Write the cycle structure the way a composer would, for example `(234)(56)`,
 * leaving out the bells that do not move.
 *
 * @param {Row} row
 * @returns {string}
 */
export function writeCycles(row) {
  const moving = cycles(row).filter((cycle) => cycle.length > 1);
  if (moving.length === 0) {
    return '()';
  }
  return moving.map((cycle) => `(${cycle.join('')})`).join('');
}

/**
 * @param {number} left
 * @param {number} right
 * @returns {number}
 */
function leastCommonMultiple(left, right) {
  return (left / greatestCommonDivisor(left, right)) * right;
}

/**
 * @param {number} left
 * @param {number} right
 * @returns {number}
 */
function greatestCommonDivisor(left, right) {
  let a = left;
  let b = right;
  while (b !== 0) {
    const remainder = a % b;
    a = b;
    b = remainder;
  }
  return a;
}

/**
 * @param {unknown} row
 */
function assertRow(row) {
  if (!(row instanceof Row)) {
    throw new TypeError('a row is needed to work out its cycles');
  }
}
