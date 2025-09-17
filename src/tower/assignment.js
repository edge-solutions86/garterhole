import { Tower } from './tower.js';
import { Ringer } from './ringer.js';
import { sortBy } from '../util/collections.js';
import { ValidationError } from '../errors.js';

/**
 * Putting the band on the bells.
 *
 * Every ringer can take some of the bells and not others: the tenor is too
 * heavy for some, the method is beyond others. That makes this a matching
 * problem, and a greedy pass at it fails on exactly the cases that matter, when
 * one ringer is the only person who can take one bell.
 *
 * So it backtracks, and it fills the hardest bell first. Ordering the bells by
 * how few people can take them is what turns a search that would run for
 * minutes into one that finishes at once.
 */

/**
 * @typedef {{
 *   tower: Tower,
 *   band: readonly Ringer[],
 *   stage: number,
 *   title?: string,
 *   competency?: import('./competency.js').CompetencyRegister,
 *   level?: string,
 *   conductor?: boolean
 * }} AssignmentRequest
 */

/**
 * Which bells a ringer could take, given the weight and the method.
 *
 * @param {AssignmentRequest} request
 * @param {Ringer} member
 * @returns {number[]}
 */
export function bellsOpenTo(request, member) {
  const bells = [];
  const wanted = ringingBells(request);
  for (const number of wanted) {
    if (!member.canHandle(request.tower.bell(number))) {
      continue;
    }
    if (!knowsTheMethod(request, member)) {
      continue;
    }
    bells.push(number);
  }
  return bells;
}

/**
 * The bells that have to be filled: the stage, plus a cover when the stage is
 * odd and the tower has a bell spare.
 *
 * @param {AssignmentRequest} request
 * @returns {number[]}
 */
export function ringingBells(request) {
  assertRequest(request);
  const { tower, stage } = request;
  if (!tower.canRing(stage)) {
    throw new ValidationError(`${tower.name} cannot ring ${stage} bells`);
  }
  const count = tower.canCover(stage) ? stage + 1 : stage;
  const bells = [];
  for (let number = 1; number <= count; number += 1) {
    bells.push(number);
  }
  return bells;
}

/**
 * Put the band on the bells, or say why it cannot be done.
 *
 * @param {AssignmentRequest} request
 * @returns {{ assignment: Map<number, string>, conductor: string | null }}
 */
export function assignBells(request) {
  const bells = ringingBells(request);
  if (request.band.length < bells.length) {
    throw new ValidationError(
      `${bells.length} bells need ringing and only ${request.band.length} are here`,
      { details: { needed: bells.length, present: request.band.length } },
    );
  }
  /** @type {Map<number, Ringer[]>} */
  const options = new Map();
  for (const number of bells) {
    const able = request.band.filter((member) => bellsOpenTo(request, member).includes(number));
    if (able.length === 0) {
      throw new ValidationError(`nobody here can take bell ${number}`, {
        details: { bell: number },
      });
    }
    options.set(number, able);
  }
  const order = sortBy(bells, (number) => options.get(number).length);
  const assignment = new Map();
  const taken = new Set();
  if (!fill(order, 0, options, assignment, taken)) {
    throw new ValidationError('there is no way to put this band on these bells', {
      details: { bells: bells.length },
    });
  }
  const conductor = findConductor(request, assignment);
  if (request.conductor === true && conductor === null) {
    throw new ValidationError('nobody on the bells can conduct');
  }
  return { assignment: sortAssignment(assignment), conductor };
}

/**
 * Try to fill the bells one at a time, going back when a choice leaves a later
 * bell with nobody.
 *
 * @param {readonly number[]} order
 * @param {number} index
 * @param {Map<number, Ringer[]>} options
 * @param {Map<number, string>} assignment
 * @param {Set<string>} taken
 * @returns {boolean}
 */
function fill(order, index, options, assignment, taken) {
  if (index === order.length) {
    return true;
  }
  const number = order[index];
  for (const member of options.get(number)) {
    if (taken.has(member.id)) {
      continue;
    }
    assignment.set(number, member.id);
    taken.add(member.id);
    if (fill(order, index + 1, options, assignment, taken)) {
      return true;
    }
    assignment.delete(number);
    taken.delete(member.id);
  }
  return false;
}

/**
 * @param {AssignmentRequest} request
 * @param {Map<number, string>} assignment
 * @returns {string | null}
 */
function findConductor(request, assignment) {
  const onTheBells = new Set(assignment.values());
  for (const member of request.band) {
    if (onTheBells.has(member.id) && member.conducts) {
      return member.id;
    }
  }
  return null;
}

/**
 * @param {AssignmentRequest} request
 * @param {Ringer} member
 * @returns {boolean}
 */
function knowsTheMethod(request, member) {
  if (request.competency === undefined || request.title === undefined) {
    return true;
  }
  return request.competency.canRing(member.id, request.title, request.level ?? 'inside');
}

/**
 * @param {Map<number, string>} assignment
 * @returns {Map<number, string>}
 */
function sortAssignment(assignment) {
  return new Map([...assignment.entries()].sort((left, right) => left[0] - right[0]));
}

/**
 * @param {unknown} request
 */
function assertRequest(request) {
  if (request === null || typeof request !== 'object') {
    throw new ValidationError('an assignment needs a request object');
  }
  if (!(request.tower instanceof Tower)) {
    throw new ValidationError('an assignment needs a Tower');
  }
  if (!Array.isArray(request.band) || request.band.length === 0) {
    throw new ValidationError('an assignment needs a band');
  }
  for (const member of request.band) {
    if (!(member instanceof Ringer)) {
      throw new ValidationError('everybody in the band must be a Ringer');
    }
  }
}
