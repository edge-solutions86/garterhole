import { Method } from './method.js';
import { huntBells, workingBells, blowsInEachPlace } from './huntBells.js';
import { leadsInCourse, courseLength } from './plainCourse.js';
import { workOf, coursePath } from './pathTrace.js';
import { classify } from './classify.js';
import { countBy } from '../util/collections.js';

/**
 * The numbers that go beside a method in a collection.
 *
 * Place bell order is the useful one and the one nobody works out by hand
 * correctly. It says which place bell a ringer becomes at the end of each lead,
 * which is exactly what a ringer has to know to get through a touch, and it
 * follows from the lead head with no room for opinion.
 */

/**
 * The place bell order: starting from second place bell, which place bell each
 * one becomes at the lead head, until it comes back.
 *
 * @param {Method} method
 * @returns {number[]}
 */
export function placeBellOrder(method) {
  assertMethod(method);
  const head = method.leadHead();
  const hunt = new Set(huntBells(method));
  const order = [];
  let place = 1;
  while (place <= method.stage) {
    if (!hunt.has(place)) {
      break;
    }
    place += 1;
  }
  if (place > method.stage) {
    return [];
  }
  const start = place;
  let current = start;
  do {
    order.push(current);
    current = head.placeOf(current);
  } while (current !== start && order.length <= method.stage);
  return order;
}

/**
 * How many blows a bell spends in each place over a whole course, which is what
 * a band means by saying a method is front work heavy.
 *
 * @param {Method} method
 * @param {number} bell
 * @returns {number[]}
 */
export function blowsOverCourse(method, bell) {
  assertMethod(method);
  const path = coursePath(method, bell).slice(0, -1);
  const counts = new Array(method.stage).fill(0);
  for (const place of path) {
    counts[place - 1] += 1;
  }
  return counts;
}

/**
 * How many of each piece of work a bell does in a course.
 *
 * @param {Method} method
 * @param {number} bell
 * @returns {Record<string, number>}
 */
export function workCounts(method, bell) {
  const counts = countBy(workOf(method, bell), (piece) => piece.kind);
  return Object.fromEntries(
    [...counts.entries()].sort((left, right) => left[0].localeCompare(right[0])),
  );
}

/**
 * Everything a collection prints beside a method.
 *
 * @param {Method} method
 * @returns {{
 *   title: string,
 *   stage: number,
 *   leadLength: number,
 *   leadsInCourse: number,
 *   courseLength: number,
 *   huntBells: number[],
 *   workingBells: number[],
 *   placeBellOrder: number[],
 *   className: string
 * }}
 */
export function methodStats(method) {
  assertMethod(method);
  const facts = classify(method);
  return {
    title: `${method.name} (${method.stageName})`,
    stage: method.stage,
    leadLength: method.leadLength,
    leadsInCourse: leadsInCourse(method),
    courseLength: courseLength(method),
    huntBells: facts.huntBells,
    workingBells: workingBells(method),
    placeBellOrder: placeBellOrder(method),
    className: facts.className,
  };
}

/**
 * True when every bell strikes the same number of blows in every place over a
 * lead, which is what makes a hunt bell path even.
 *
 * @param {Method} method
 * @param {number} bell
 * @returns {boolean}
 */
export function pathIsEven(method, bell) {
  const counts = blowsInEachPlace(method, bell);
  return counts.every((count) => count === counts[0]);
}

/**
 * @param {unknown} method
 */
function assertMethod(method) {
  if (!(method instanceof Method)) {
    throw new TypeError('a Method is needed here');
  }
}
