import { Method } from './method.js';
import { reverseChanges } from './symmetry.js';
import { writeChanges } from '../notation/format.js';

/**
 * Recognising the same method written two ways.
 *
 * A lead can be written starting at any of its changes, and a method turned
 * upside down is a different method with its own name but the same shape. A
 * library that files both spellings of one method will build a splicing table
 * that cannot be rung, so it needs a form that is the same for every spelling.
 *
 * The canonical form is the smallest of the rotations, in the plain order that
 * string comparison gives. That is arbitrary and it does not matter: what
 * matters is that it is the same for every rotation of the same lead.
 */

/**
 * Every rotation of a lead, as notation.
 *
 * @param {readonly import('../core/change.js').Change[]} changes
 * @returns {string[]}
 */
export function rotations(changes) {
  const written = [];
  for (let offset = 0; offset < changes.length; offset += 1) {
    written.push(writeChanges([...changes.slice(offset), ...changes.slice(0, offset)]));
  }
  return written;
}

/**
 * The canonical form of a lead: the smallest of its rotations.
 *
 * @param {readonly import('../core/change.js').Change[]} changes
 * @returns {string}
 */
export function canonicalNotation(changes) {
  const all = rotations(changes);
  if (all.length === 0) {
    return '';
  }
  return all.sort()[0];
}

/**
 * The canonical form of a method, with its stage, so that two stages never
 * collide.
 *
 * @param {Method} method
 * @returns {string}
 */
export function canonicalForm(method) {
  assertMethod(method);
  return `${method.stage}:${canonicalNotation(method.changes)}`;
}

/**
 * The canonical form of the method turned upside down.
 *
 * @param {Method} method
 * @returns {string}
 */
export function reversedForm(method) {
  assertMethod(method);
  return `${method.stage}:${canonicalNotation(reverseChanges([...method.changes]))}`;
}

/**
 * True when two methods are the same lead written from different starting
 * points.
 *
 * @param {Method} left
 * @param {Method} right
 * @returns {boolean}
 */
export function isRotationOfMethod(left, right) {
  return canonicalForm(left) === canonicalForm(right);
}

/**
 * True when one method is the other turned upside down.
 *
 * @param {Method} left
 * @param {Method} right
 * @returns {boolean}
 */
export function isReverseOfMethod(left, right) {
  return canonicalForm(left) === reversedForm(right);
}

/**
 * Group methods so that every spelling of one method lands together.
 *
 * @param {Iterable<Method>} methods
 * @returns {Map<string, Method[]>}
 */
export function groupBySameLead(methods) {
  /** @type {Map<string, Method[]>} */
  const groups = new Map();
  for (const method of methods) {
    const key = canonicalForm(method);
    const bucket = groups.get(key);
    if (bucket === undefined) {
      groups.set(key, [method]);
    } else {
      bucket.push(method);
    }
  }
  return groups;
}

/**
 * The methods in a list that are spellings of something already in it.
 *
 * @param {Iterable<Method>} methods
 * @returns {Method[]}
 */
export function duplicateSpellings(methods) {
  const seen = new Set();
  const duplicates = [];
  for (const method of methods) {
    const key = canonicalForm(method);
    if (seen.has(key)) {
      duplicates.push(method);
      continue;
    }
    seen.add(key);
  }
  return duplicates;
}

/**
 * @param {unknown} method
 */
function assertMethod(method) {
  if (!(method instanceof Method)) {
    throw new TypeError('a Method is needed here');
  }
}
