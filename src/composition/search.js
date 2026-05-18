import { Method } from '../method/method.js';
import { standardCalls } from './callSet.js';
import { RowBlock } from '../core/rowBlock.js';
import { rounds } from '../core/row.js';
import { TruthTracker } from './prover.js';
import { writeChanges } from '../notation/format.js';
import { ValidationError } from '../errors.js';

/**
 * Looking for a touch.
 *
 * This is a depth first search over the calls, one lead at a time, keeping the
 * rows in a truth tracker as it goes. A branch that repeats a row is abandoned
 * at once rather than being rung out and proved afterwards, which is the whole
 * reason the tracker exists.
 *
 * It is not a composing program. It will find the touches of a hundred and
 * twenty changes that a tower rings on a practice night, and it will not find a
 * peal composition, because the search space of a peal is far past what a depth
 * first walk can cover. Saying so here is better than letting somebody find out
 * by leaving it running overnight.
 */

/** The most leads a search will ring down one branch. */
export const MAX_DEPTH = 60;

/**
 * @typedef {{
 *   calling: string,
 *   changes: number,
 *   leads: number,
 *   notation: string
 * }} FoundTouch
 */

/**
 * Search for true touches that come round.
 *
 * @param {{
 *   method: Method,
 *   calls?: Record<string, import('./call.js').Call>,
 *   lengths?: readonly number[],
 *   maxLeads?: number,
 *   limit?: number
 * }} request
 * @returns {FoundTouch[]}
 */
export function searchTouches(request) {
  if (!(request?.method instanceof Method)) {
    throw new ValidationError('a Method is needed to search for touches of it');
  }
  const method = request.method;
  const calls = request.calls ?? standardCalls(method);
  const maxLeads = request.maxLeads ?? 12;
  const limit = request.limit ?? 20;
  if (!Number.isInteger(maxLeads) || maxLeads < 1 || maxLeads > MAX_DEPTH) {
    throw new ValidationError(`a search of ${maxLeads} leads is outside 1..${MAX_DEPTH}`);
  }
  if (!Number.isInteger(limit) || limit < 1) {
    throw new ValidationError(`a search that returns ${limit} touches is not a search`);
  }
  const wanted = request.lengths === undefined ? null : new Set(request.lengths);
  const start = rounds(method.stage);
  const tracker = new TruthTracker();
  tracker.add(start);
  /** @type {FoundTouch[]} */
  const found = [];
  const order = Object.values(calls);
  walk({ method, order, maxLeads, limit, wanted, found, tracker }, start, [], 0);
  return found;
}

/**
 * Ring one lead, and either report a touch or carry on.
 *
 * @param {object} search
 * @param {import('../core/row.js').Row} row
 * @param {string[]} calling
 * @param {number} leads
 */
function walk(search, row, calling, leads) {
  if (search.found.length >= search.limit || leads >= search.maxLeads) {
    return;
  }
  for (const call of search.order) {
    const changes = call.applyToLead(search.method);
    const lead = RowBlock.fromChanges(row, changes);
    const added = [];
    let stayedTrue = true;
    for (let index = 1; index < lead.length; index += 1) {
      const next = lead.at(index);
      if (index === lead.length - 1 && next.isRounds()) {
        break;
      }
      if (!search.tracker.add(next)) {
        stayedTrue = false;
        added.push(next);
        break;
      }
      added.push(next);
    }
    const nowCalling = [...calling, call.symbol];
    if (stayedTrue && lead.last().isRounds() && leads + 1 >= 2) {
      report(search, nowCalling, (leads + 1) * search.method.leadLength);
    } else if (stayedTrue) {
      walk(search, lead.last(), nowCalling, leads + 1);
    }
    undo(search.tracker, added);
  }
}

/**
 * @param {object} search
 * @param {readonly string[]} calling
 * @param {number} changes
 */
function report(search, calling, changes) {
  if (search.wanted !== null && !search.wanted.has(changes)) {
    return;
  }
  search.found.push({
    calling: calling.join(''),
    changes,
    leads: calling.length,
    notation: writeChanges(search.method.changes),
  });
}

/**
 * Take the rows of a lead back out of the tracker, so that the next branch
 * starts from where this one did.
 *
 * @param {TruthTracker} tracker
 * @param {readonly import('../core/row.js').Row[]} rows
 */
function undo(tracker, rows) {
  for (const row of rows) {
    const key = row.key();
    const at = tracker.seen.get(key);
    if (at === undefined) {
      continue;
    }
    at.pop();
    tracker.count -= 1;
    if (at.length === 0) {
      tracker.seen.delete(key);
    }
  }
}

/**
 * The shortest touch the search found, or null.
 *
 * @param {readonly FoundTouch[]} found
 * @returns {FoundTouch | null}
 */
export function shortest(found) {
  let best = null;
  for (const touch of found) {
    if (best === null || touch.changes < best.changes) {
      best = touch;
    }
  }
  return best;
}
