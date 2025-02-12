import { Method } from './method.js';
import { plainCourse } from './plainCourse.js';
import { chunk } from '../util/collections.js';

/**
 * The blue line. A ringer learns a method as the path one bell takes through a
 * course, described as a run of pieces of work: dodge here, make a place there,
 * lie behind, lead. This module turns the rows into that description.
 *
 * The description is deliberately mechanical. It says what happened rather than
 * naming it the way a teacher would, because the names differ between towers
 * and the mechanics do not.
 */

/**
 * @typedef {{ kind: string, place: number, at: number, blows: number }} WorkPiece
 */

/**
 * The place a bell rings in for every row of a plain course.
 *
 * @param {Method} method
 * @param {number} bell
 * @returns {number[]}
 */
export function coursePath(method, bell) {
  assertMethod(method);
  const course = plainCourse(method);
  return course.rows.map((row) => row.placeOf(bell));
}

/**
 * Break a path into pieces of work.
 *
 * A piece is one of:
 * - `lead`, two or more blows in first place
 * - `lie`, two or more blows at the back
 * - `place`, two or more blows anywhere else
 * - `dodge`, two turns one place apart, so the bell ends up where it came from
 * - `point`, a turn with no answering turn beside it
 * - `hunt`, a run of blows in the same direction
 *
 * @param {readonly number[]} path
 * @param {number} stage
 * @returns {WorkPiece[]}
 */
export function describePath(path, stage) {
  const turns = turnPoints(path);
  /** @type {WorkPiece[]} */
  const pieces = [];
  let index = 0;
  while (index + 1 < path.length) {
    const place = path[index];
    let blows = 1;
    while (index + blows < path.length && path[index + blows] === place) {
      blows += 1;
    }
    if (blows > 1) {
      pieces.push({ kind: stationaryKind(place, stage), place, at: index, blows });
      index += blows;
      continue;
    }
    if (turns[index]) {
      if (turns[index + 1] && Math.abs(path[index + 1] - place) === 1) {
        pieces.push({
          kind: 'dodge',
          place: Math.min(place, path[index + 1]),
          at: index,
          blows: 2,
        });
        index += 2;
        continue;
      }
      pieces.push({ kind: 'point', place, at: index, blows: 1 });
      index += 1;
      continue;
    }
    pieces.push({ kind: 'hunt', place, at: index, blows: 1 });
    index += 1;
  }
  return pieces;
}

/**
 * Mark every index where the bell reverses direction.
 *
 * @param {readonly number[]} path
 * @returns {boolean[]}
 */
export function turnPoints(path) {
  const turns = new Array(path.length).fill(false);
  for (let index = 1; index + 1 < path.length; index += 1) {
    const before = Math.sign(path[index] - path[index - 1]);
    const after = Math.sign(path[index + 1] - path[index]);
    turns[index] = before !== 0 && after !== 0 && before !== after;
  }
  return turns;
}

/**
 * The pieces of work a bell does over a plain course, with the runs of plain
 * hunting left out because nobody describes those out loud.
 *
 * @param {Method} method
 * @param {number} bell
 * @returns {WorkPiece[]}
 */
export function workOf(method, bell) {
  const path = coursePath(method, bell);
  return describePath(path, method.stage).filter((piece) => piece.kind !== 'hunt');
}

/**
 * The path written out as one line of place symbols per lead, which is how a
 * blue line is printed on paper.
 *
 * @param {Method} method
 * @param {number} bell
 * @returns {number[][]}
 */
export function pathByLead(method, bell) {
  const path = coursePath(method, bell).slice(0, -1);
  return chunk(path, method.leadLength);
}

/**
 * True when two bells ring the same line, which is what makes a method double.
 * The comparison rotates one path against the other, because two bells doing
 * the same work start it at different points in the course.
 *
 * @param {readonly number[]} left
 * @param {readonly number[]} right
 * @returns {boolean}
 */
export function samePathRotated(left, right) {
  if (left.length !== right.length) {
    return false;
  }
  const length = left.length;
  for (let offset = 0; offset < length; offset += 1) {
    let matched = true;
    for (let index = 0; index < length; index += 1) {
      if (left[index] !== right[(index + offset) % length]) {
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
 * @param {number} place
 * @param {number} stage
 * @returns {string}
 */
function stationaryKind(place, stage) {
  if (place === 1) {
    return 'lead';
  }
  if (place === stage) {
    return 'lie';
  }
  return 'place';
}

/**
 * @param {unknown} method
 */
function assertMethod(method) {
  if (!(method instanceof Method)) {
    throw new TypeError('a Method is needed here');
  }
}
