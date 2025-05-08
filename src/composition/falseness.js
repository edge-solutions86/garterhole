import { transpose, inverse } from '../core/transposition.js';
import { plainCourse } from '../method/plainCourse.js';
import { Method } from '../method/method.js';
import { cycleLengths } from '../core/cycles.js';
import { sortBy } from '../util/collections.js';
import { ValidationError } from '../errors.js';

/**
 * False course heads.
 *
 * Two courses of a method clash when a row in one is also a row in the other.
 * Which pairs of courses clash depends only on the method, so the set can be
 * worked out once and then used to check any composition of it without ringing
 * a single row twice.
 *
 * The set is the transposition between every pair of rows in a plain course,
 * kept where it leaves the treble at the front. A composer reads it as: ring
 * the plain course and any course headed by one of these, and the two are
 * false against each other.
 */

/**
 * The course head that takes one row of a course to another.
 *
 * @param {import('../core/row.js').Row} from
 * @param {import('../core/row.js').Row} to
 * @returns {import('../core/row.js').Row}
 */
export function courseHeadBetween(from, to) {
  return transpose(to, inverse(from));
}

/**
 * Every course head that is false against the plain course, rounds left out.
 *
 * @param {Method} method
 * @returns {import('../core/row.js').Row[]} sorted by their printed form
 */
export function falseCourseHeads(method) {
  if (!(method instanceof Method)) {
    throw new ValidationError('a Method is needed to find its false course heads');
  }
  const rows = plainCourse(method).rows.slice(0, -1);
  /** @type {Map<string, import('../core/row.js').Row>} */
  const heads = new Map();
  for (const from of rows) {
    for (const to of rows) {
      const head = courseHeadBetween(from, to);
      if (head.isRounds() || head.bellAt(1) !== 1) {
        continue;
      }
      heads.set(head.key(), head);
    }
  }
  return sortBy([...heads.values()], (row) => row.toString());
}

/**
 * The false course heads that leave the tenors where they are, which is the
 * list a composer of tenors together music actually uses.
 *
 * @param {Method} method
 * @param {number} [tenors] how many bells at the back must be fixed
 * @returns {import('../core/row.js').Row[]}
 */
export function tenorsTogetherFalseCourseHeads(method, tenors = 2) {
  const lowest = method.stage - tenors + 1;
  return falseCourseHeads(method).filter((head) => {
    for (let place = lowest; place <= method.stage; place += 1) {
      if (head.bellAt(place) !== place) {
        return false;
      }
    }
    return true;
  });
}

/**
 * True when a course headed by the given row clashes with the plain course.
 *
 * @param {Method} method
 * @param {import('../core/row.js').Row} courseHead
 * @returns {boolean}
 */
export function isFalseAgainstPlainCourse(method, courseHead) {
  return falseCourseHeads(method).some((head) => head.equals(courseHead));
}

/**
 * The rows two blocks have in common.
 *
 * @param {Iterable<import('../core/row.js').Row>} left
 * @param {Iterable<import('../core/row.js').Row>} right
 * @returns {string[]} the shared rows, sorted
 */
export function sharedRows(left, right) {
  const seen = new Set();
  for (const row of left) {
    seen.add(row.key());
  }
  const shared = new Set();
  for (const row of right) {
    if (seen.has(row.key())) {
      shared.add(row.key());
    }
  }
  return [...shared].sort();
}

/**
 * The candidate course heads that are true against the plain course. A composer
 * hands in the heads a plan would use and gets back the ones that survive.
 *
 * @param {Method} method
 * @param {Iterable<import('../core/row.js').Row>} candidates
 * @returns {import('../core/row.js').Row[]}
 */
export function trueCourseHeads(method, candidates) {
  const bad = new Set(falseCourseHeads(method).map((row) => row.key()));
  const kept = [];
  for (const head of candidates) {
    if (!bad.has(head.key())) {
      kept.push(head);
    }
  }
  return kept;
}

/**
 * Group the false course heads by the shape of the transposition, that is by
 * the lengths of its cycles. Heads with the same shape sit together in the
 * printed tables, and a composer who has ruled one out has usually ruled out
 * the rest of its group as well.
 *
 * @param {Method} method
 * @returns {Map<string, import('../core/row.js').Row[]>}
 */
export function falseCourseHeadGroups(method) {
  const groups = new Map();
  for (const head of falseCourseHeads(method)) {
    const key = cycleLengths(head).join('.');
    const bucket = groups.get(key);
    if (bucket === undefined) {
      groups.set(key, [head]);
    } else {
      bucket.push(head);
    }
  }
  return groups;
}
