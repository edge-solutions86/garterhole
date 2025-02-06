import { rounds } from '../core/row.js';
import { orderOf } from '../core/cycles.js';
import { Method } from './method.js';

/**
 * A plain course is the method rung with no calls until it comes back to
 * rounds. Its length is the lead length times the order of the lead head, and
 * that number is the first thing a composer needs before working out how many
 * courses a peal takes.
 */

/**
 * How many leads a plain course has.
 *
 * @param {Method} method
 * @returns {number}
 */
export function leadsInCourse(method) {
  assertMethod(method);
  return orderOf(method.leadHead());
}

/**
 * How many changes a plain course has.
 *
 * @param {Method} method
 * @returns {number}
 */
export function courseLength(method) {
  return leadsInCourse(method) * method.leadLength;
}

/**
 * Every lead head in a plain course, starting with rounds and ending with the
 * lead head that brings it back.
 *
 * @param {Method} method
 * @param {import('../core/row.js').Row} [start]
 * @returns {import('../core/row.js').Row[]}
 */
export function courseLeadHeads(method, start = rounds(method.stage)) {
  assertMethod(method);
  const heads = [start];
  let current = start;
  const limit = leadsInCourse(method);
  for (let lead = 0; lead < limit; lead += 1) {
    current = method.leadHead(current);
    heads.push(current);
  }
  return heads;
}

/**
 * Ring the whole plain course.
 *
 * @param {Method} method
 * @param {import('../core/row.js').Row} [start]
 * @returns {import('../core/rowBlock.js').RowBlock}
 */
export function plainCourse(method, start = rounds(method.stage)) {
  assertMethod(method);
  let block = method.plainLead(start);
  for (let lead = 1; lead < leadsInCourse(method); lead += 1) {
    block = block.concat(method.plainLead(block.last()));
  }
  return block;
}

/**
 * True when every row of the plain course is different. A method whose plain
 * course repeats a row is false in the plain, and no composition of it can be
 * true either.
 *
 * @param {Method} method
 * @returns {boolean}
 */
export function isTrueInThePlain(method) {
  const course = plainCourse(method);
  const seen = new Set();
  for (let index = 0; index < course.length - 1; index += 1) {
    const key = course.at(index).key();
    if (seen.has(key)) {
      return false;
    }
    seen.add(key);
  }
  return true;
}

/**
 * The place each working bell starts a course in, paired with the place it
 * finishes the first lead in. Callers use this to work out which bell is where
 * when a call comes up.
 *
 * @param {Method} method
 * @returns {{ bell: number, from: number, to: number }[]}
 */
export function leadHeadMovement(method) {
  assertMethod(method);
  const head = method.leadHead();
  const movement = [];
  for (let bell = 1; bell <= method.stage; bell += 1) {
    movement.push({ bell, from: bell, to: head.placeOf(bell) });
  }
  return movement;
}

/**
 * @param {unknown} method
 */
function assertMethod(method) {
  if (!(method instanceof Method)) {
    throw new TypeError('a Method is needed here');
  }
}
