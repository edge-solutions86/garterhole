import { Change } from '../core/change.js';
import { Method } from './method.js';
import { symmetryPoints } from '../notation/expand.js';

/**
 * Methods are named partly for the symmetries they have. A method that reads
 * the same backwards is palindromic and is much easier to learn; one that looks
 * the same turned upside down is double; one that is unchanged by doing both at
 * once has rotational symmetry.
 *
 * All three are decided from the changes, and all three allow for the lead
 * being written starting at a different point, because where a lead starts is a
 * matter of convention rather than of the method.
 */

/**
 * Turn a change upside down, so that a place in seconds becomes a place in the
 * second from the back.
 *
 * @param {Change} change
 * @returns {Change}
 */
export function reverseChange(change) {
  const places = change.places.map((place) => change.stage + 1 - place).sort((a, b) => a - b);
  return new Change(change.stage, places);
}

/**
 * Turn a whole lead upside down.
 *
 * @param {readonly Change[]} changes
 * @returns {Change[]}
 */
export function reverseChanges(changes) {
  return changes.map((change) => reverseChange(change));
}

/**
 * True when one run of changes is a rotation of another.
 *
 * @param {readonly Change[]} left
 * @param {readonly Change[]} right
 * @returns {boolean}
 */
export function isRotationOf(left, right) {
  if (left.length !== right.length) {
    return false;
  }
  const length = left.length;
  for (let offset = 0; offset < length; offset += 1) {
    let matched = true;
    for (let index = 0; index < length; index += 1) {
      if (!left[index].equals(right[(index + offset) % length])) {
        matched = false;
        break;
      }
    }
    if (matched) {
      return true;
    }
  }
  return false;
}

/**
 * True when the lead reads the same forwards as backwards.
 *
 * @param {Method} method
 * @returns {boolean}
 */
export function isPalindromic(method) {
  assertMethod(method);
  return symmetryPoints(method.changes).length > 0;
}

/**
 * True when the lead is unchanged by turning it upside down.
 *
 * @param {Method} method
 * @returns {boolean}
 */
export function isDouble(method) {
  assertMethod(method);
  return isRotationOf(method.changes, reverseChanges(method.changes));
}

/**
 * True when the lead is unchanged by turning it upside down and running it
 * backwards at the same time.
 *
 * @param {Method} method
 * @returns {boolean}
 */
export function hasRotationalSymmetry(method) {
  assertMethod(method);
  const flipped = reverseChanges(method.changes).slice().reverse();
  return isRotationOf(method.changes, flipped);
}

/**
 * Every symmetry a method has.
 *
 * @param {Method} method
 * @returns {{ palindromic: boolean, double: boolean, rotational: boolean, names: string[] }}
 */
export function symmetryOf(method) {
  const palindromic = isPalindromic(method);
  const double = isDouble(method);
  const rotational = hasRotationalSymmetry(method);
  const names = [];
  if (palindromic) {
    names.push('palindromic');
  }
  if (double) {
    names.push('double');
  }
  if (rotational) {
    names.push('rotational');
  }
  return { palindromic, double, rotational, names };
}

/**
 * @param {unknown} method
 */
function assertMethod(method) {
  if (!(method instanceof Method)) {
    throw new TypeError('a Method is needed here');
  }
}
