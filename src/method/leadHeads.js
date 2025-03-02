import { Method } from './method.js';
import { orderOf } from '../core/cycles.js';
import { groupBy } from '../util/collections.js';

/**
 * Two methods can be spliced lead by lead when a lead of one finishes where a
 * lead of the other would have. That is exactly the condition that they are on
 * the same stage and have the same lead head, and it is the first thing a band
 * ringing a spliced quarter needs to know.
 *
 * Nothing here reads a recorded lead head code. The lead head is rung out of
 * the notation, which is the only version that cannot be wrong.
 */

/**
 * The lead head as a string, which is what the grouping is keyed on.
 *
 * @param {Method} method
 * @returns {string}
 */
export function leadHeadSignature(method) {
  assertMethod(method);
  return `${method.stage}:${method.leadHead()}`;
}

/**
 * The places made by the change at the lead end, which is where a call goes.
 *
 * @param {Method} method
 * @returns {number[]}
 */
export function leadEndPlaces(method) {
  assertMethod(method);
  return [...method.leadEndChange.places];
}

/**
 * How many leads the plain course runs to.
 *
 * @param {Method} method
 * @returns {number}
 */
export function leadHeadOrder(method) {
  assertMethod(method);
  return orderOf(method.leadHead());
}

/**
 * True when a lead of one method finishes on the row a lead of the other would
 * have finished on.
 *
 * @param {Method} left
 * @param {Method} right
 * @returns {boolean}
 */
export function sharesLeadHead(left, right) {
  assertMethod(left);
  assertMethod(right);
  return left.stage === right.stage && left.leadHead().equals(right.leadHead());
}

/**
 * Group methods by the lead head they finish on.
 *
 * @param {readonly Method[]} methods
 * @returns {Map<string, Method[]>}
 */
export function groupByLeadHead(methods) {
  return groupBy(methods, (method) => leadHeadSignature(method));
}

/**
 * The methods in a list that can be spliced with a given method at a lead head.
 * The method itself is left out, since splicing a method with itself is just
 * ringing it.
 *
 * @param {Method} method
 * @param {readonly Method[]} candidates
 * @returns {Method[]}
 */
export function spliceableWith(method, candidates) {
  assertMethod(method);
  return candidates.filter(
    (candidate) => candidate !== method && sharesLeadHead(method, candidate),
  );
}

/**
 * Splicing at every lead needs the lead lengths to match as well, otherwise the
 * treble is in the wrong place when the change of method comes. This is the
 * stricter test, and it is the one a band ringing spliced actually wants.
 *
 * @param {Method} left
 * @param {Method} right
 * @returns {boolean}
 */
export function spliceableEveryLead(left, right) {
  return sharesLeadHead(left, right) && left.leadLength === right.leadLength;
}

/**
 * A short description of the lead head for a report: the row, how many leads a
 * course runs to, and the places made at the lead end.
 *
 * @param {Method} method
 * @returns {{ leadHead: string, leads: number, leadEnd: string, leadLength: number }}
 */
export function describeLeadHead(method) {
  assertMethod(method);
  return {
    leadHead: method.leadHead().toString(),
    leads: leadHeadOrder(method),
    leadEnd: method.leadEndChange.toString(),
    leadLength: method.leadLength,
  };
}

/**
 * @param {unknown} method
 */
function assertMethod(method) {
  if (!(method instanceof Method)) {
    throw new TypeError('a Method is needed here');
  }
}
