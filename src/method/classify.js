import { Method } from './method.js';
import { huntBells, workingBells, pathOf, huntPathKind, dodgePlaces } from './huntBells.js';
import { cycles } from '../core/cycles.js';
import { unique } from '../util/collections.js';

/**
 * The class name of a method. Cambridge is Surprise, Kent is Treble Bob, Plain
 * Bob is Bob, and the difference between the three is not the name anybody gave
 * them but where the internal places fall against the treble's path.
 *
 * Everything here is worked out from the changes. A collection that records a
 * class name is not consulted, because collections disagree with themselves.
 */

/**
 * The pair of places a bell is dodging in, counting pairs from the front.
 *
 * @param {number} place
 * @returns {number}
 */
export function sectionOf(place) {
  return Math.ceil(place / 2);
}

/**
 * The indices of the changes where the hunt bell moves from one dodging
 * position to the next. These are the cross sections, and what happens at them
 * decides between Treble Bob, Delight and Surprise.
 *
 * @param {readonly number[]} path
 * @returns {number[]}
 */
export function crossSections(path) {
  const indices = [];
  for (let index = 0; index + 1 < path.length; index += 1) {
    if (sectionOf(path[index]) !== sectionOf(path[index + 1])) {
      indices.push(index);
    }
  }
  return indices;
}

/**
 * True when the change makes a place anywhere except the front and the back.
 *
 * @param {import('../core/change.js').Change} change
 * @returns {boolean}
 */
export function makesInternalPlace(change) {
  return change.places.some((place) => place !== 1 && place !== change.stage);
}

/**
 * True when the hunt bell never reaches the back, which makes the method little.
 *
 * @param {Method} method
 * @param {number} bell
 * @returns {boolean}
 */
export function isLittle(method, bell) {
  const path = pathOf(method, bell);
  return Math.max(...path) < method.stage;
}

/**
 * True when the working bells do not all belong to one cycle of the lead head,
 * so they do not all ring the same line.
 *
 * @param {Method} method
 * @returns {boolean}
 */
export function isDifferential(method) {
  const working = new Set(workingBells(method));
  if (working.size === 0) {
    return false;
  }
  const lengths = cycles(method.leadHead())
    .filter((cycle) => cycle.some((bell) => working.has(bell)))
    .map((cycle) => cycle.length);
  return unique(lengths).length > 1;
}

/**
 * The class name a method belongs to, without the Little and Differential
 * prefixes.
 *
 * @param {Method} method
 * @returns {string}
 */
export function baseClass(method) {
  assertMethod(method);
  const hunts = huntBells(method);
  if (hunts.length === 0) {
    return 'Principle';
  }
  const principal = hunts[0];
  const path = pathOf(method, principal);
  const kind = huntPathKind(method, principal);
  if (kind === 'plain') {
    return hasWorkingDodges(method) ? 'Bob' : 'Place';
  }
  if (kind === 'treble bob') {
    return trebleBobFamily(method, path);
  }
  return dodgePlaces(path, method.stage).length > 0 ? 'Alliance' : 'Treble Place';
}

/**
 * The full classification, including the prefixes and the parts that went into
 * the decision.
 *
 * @param {Method} method
 * @returns {{
 *   className: string,
 *   base: string,
 *   little: boolean,
 *   differential: boolean,
 *   huntBells: number[],
 *   huntPath: string | null
 * }}
 */
export function classify(method) {
  assertMethod(method);
  const hunts = huntBells(method);
  const base = baseClass(method);
  const little = hunts.length > 0 && isLittle(method, hunts[0]);
  const differential = isDifferential(method);
  const parts = [];
  if (differential) {
    parts.push('Differential');
  }
  if (little) {
    parts.push('Little');
  }
  if (base !== '') {
    parts.push(base);
  }
  return {
    className: parts.join(' '),
    base,
    little,
    differential,
    huntBells: hunts,
    huntPath: hunts.length > 0 ? huntPathKind(method, hunts[0]) : null,
  };
}

/**
 * Treble Bob, Delight and Surprise differ only in how many cross sections have
 * an internal place.
 *
 * @param {Method} method
 * @param {readonly number[]} path
 * @returns {string}
 */
function trebleBobFamily(method, path) {
  const sections = crossSections(path);
  if (sections.length === 0) {
    return 'Treble Bob';
  }
  const withPlace = sections.filter((index) => makesInternalPlace(method.changes[index]));
  if (withPlace.length === sections.length) {
    return 'Surprise';
  }
  if (withPlace.length === 0) {
    return 'Treble Bob';
  }
  return 'Delight';
}

/**
 * True when any working bell turns round away from the front and the back,
 * which is what separates a Bob method from a Place method.
 *
 * @param {Method} method
 * @returns {boolean}
 */
function hasWorkingDodges(method) {
  return workingBells(method).some(
    (bell) => dodgePlaces(pathOf(method, bell), method.stage).length > 0,
  );
}

/**
 * @param {unknown} method
 */
function assertMethod(method) {
  if (!(method instanceof Method)) {
    throw new TypeError('a Method is needed here');
  }
}
