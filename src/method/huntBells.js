import { fixedBells } from '../core/cycles.js';
import { Method } from './method.js';

/**
 * A hunt bell is one that rings the same path in every lead. That is the same
 * thing as being left in its own place by the lead head, which is how it is
 * worked out here rather than by comparing two leads row by row.
 *
 * Everything the classifier does starts from this. A method with one hunt bell
 * is a treble dominated method and gets a class name like Surprise; a method
 * with none is a principle and gets no class name at all.
 */

/**
 * The hunt bells, ascending.
 *
 * @param {Method} method
 * @returns {number[]}
 */
export function huntBells(method) {
  assertMethod(method);
  return fixedBells(method.leadHead());
}

/**
 * The bells that are not hunt bells. These are the ones a composition moves
 * around, and the ones a caller has to keep track of.
 *
 * @param {Method} method
 * @returns {number[]}
 */
export function workingBells(method) {
  const hunt = new Set(huntBells(method));
  const working = [];
  for (let bell = 1; bell <= method.stage; bell += 1) {
    if (!hunt.has(bell)) {
      working.push(bell);
    }
  }
  return working;
}

/**
 * True when the method has no hunt bell, which makes it a principle.
 *
 * @param {Method} method
 * @returns {boolean}
 */
export function isPrinciple(method) {
  return huntBells(method).length === 0;
}

/**
 * The places a bell rings in, one for each row of a plain lead including both
 * ends.
 *
 * @param {Method} method
 * @param {number} bell
 * @returns {number[]}
 */
export function pathOf(method, bell) {
  assertMethod(method);
  const lead = method.plainLead();
  return lead.rows.map((row) => row.placeOf(bell));
}

/**
 * The places at which the bell turns round, leaving out the two turns every
 * bell makes at the front and the back. What is left is the dodges.
 *
 * @param {readonly number[]} path
 * @param {number} stage
 * @returns {number[]}
 */
export function dodgePlaces(path, stage) {
  const places = [];
  let previous = 0;
  for (let index = 0; index + 1 < path.length; index += 1) {
    const direction = Math.sign(path[index + 1] - path[index]);
    if (direction === 0) {
      continue;
    }
    if (previous !== 0 && direction !== previous && path[index] !== 1 && path[index] !== stage) {
      places.push(path[index]);
    }
    previous = direction;
  }
  return places;
}

/**
 * The places where the bell rings two blows running, again leaving out the
 * front and the back where every bell does it.
 *
 * @param {readonly number[]} path
 * @param {number} stage
 * @returns {number[]}
 */
export function placesMade(path, stage) {
  const places = [];
  for (let index = 0; index + 1 < path.length; index += 1) {
    if (path[index] === path[index + 1] && path[index] !== 1 && path[index] !== stage) {
      places.push(path[index]);
    }
  }
  return places;
}

/**
 * Describe the shape of a hunt bell's path. The answer decides most of the
 * method's class name.
 *
 * @param {Method} method
 * @param {number} bell
 * @returns {'plain' | 'treble bob' | 'treble place'}
 */
export function huntPathKind(method, bell) {
  const path = pathOf(method, bell);
  if (placesMade(path, method.stage).length > 0) {
    return 'treble place';
  }
  if (dodgePlaces(path, method.stage).length > 0) {
    return 'treble bob';
  }
  return 'plain';
}

/**
 * How many blows the bell spends in each place over a lead, indexed by place.
 * Used by the reports, and by the check that a hunt bell really does cover the
 * whole row rather than sitting in one place.
 *
 * @param {Method} method
 * @param {number} bell
 * @returns {number[]} one entry per place, starting at place one
 */
export function blowsInEachPlace(method, bell) {
  const path = pathOf(method, bell).slice(0, -1);
  const counts = new Array(method.stage).fill(0);
  for (const place of path) {
    counts[place - 1] += 1;
  }
  return counts;
}

/**
 * @param {unknown} method
 */
function assertMethod(method) {
  if (!(method instanceof Method)) {
    throw new TypeError('a Method is needed here');
  }
}
