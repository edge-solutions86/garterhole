import { rounds } from '../core/row.js';
import { RowBlock } from '../core/rowBlock.js';
import { Method } from '../method/method.js';
import { standardCalls, callBySymbol } from './callSet.js';
import { positionOfCall } from './callingPosition.js';
import { PLAIN_SYMBOL } from './touchParser.js';
import { ValidationError } from '../errors.js';

/**
 * Ringing a touch. The instructions say what to call; this works out what the
 * bells actually do.
 *
 * A touch written as one symbol per lead is straightforward: ring the lead the
 * symbol names and move on. A touch written as calling positions is not, because
 * the position says where the observation bell has to be when the call comes,
 * and getting there takes as many plain leads as it takes.
 */

/** A touch that has not come round by here has gone wrong. */
export const MAX_LEADS = 2000;

/**
 * @typedef {{
 *   symbol: string,
 *   position: string | null,
 *   leadHead: import('../core/row.js').Row,
 *   changes: import('../core/change.js').Change[]
 * }} RungLead
 */

/**
 * Ring a touch.
 *
 * @param {{
 *   method: Method,
 *   touch: { style: string, instructions: { symbol: string, position?: string }[] },
 *   calls?: Record<string, import('./call.js').Call>,
 *   observation?: number,
 *   start?: import('../core/row.js').Row,
 *   ringToRounds?: boolean
 * }} options
 * @returns {{ block: RowBlock, leads: RungLead[], comesRound: boolean }}
 */
export function expandTouch(options) {
  const method = options.method;
  if (!(method instanceof Method)) {
    throw new ValidationError('a Method is needed to ring a touch');
  }
  const touch = options.touch;
  if (touch === null || typeof touch !== 'object' || !Array.isArray(touch.instructions)) {
    throw new ValidationError('a parsed touch is needed to ring it');
  }
  const calls = options.calls ?? standardCalls(method);
  const observation = options.observation ?? method.stage;
  const start = options.start ?? rounds(method.stage);
  const ringToRounds = options.ringToRounds ?? touch.style === 'positions';

  /** @type {RungLead[]} */
  const leads = [];
  let block = new RowBlock([start]);
  for (const instruction of touch.instructions) {
    const called = callFor(calls, instruction.symbol);
    if (instruction.position === undefined) {
      block = ringOneLead(block, method, called, null, leads);
      continue;
    }
    block = ringToPosition(block, method, calls, called, instruction.position, observation, leads);
  }
  if (ringToRounds) {
    block = ringHome(block, method, calls, leads, start);
  }
  return { block, leads, comesRound: block.last().equals(start) };
}

/**
 * The number of changes a touch runs to.
 *
 * @param {{ block: RowBlock }} rung
 * @returns {number}
 */
export function touchLength(rung) {
  return rung.block.changeCount;
}

/**
 * @param {Record<string, import('./call.js').Call>} calls
 * @param {string} symbol
 * @returns {import('./call.js').Call}
 */
function callFor(calls, symbol) {
  if (symbol === PLAIN_SYMBOL) {
    return calls.plain;
  }
  const found = callBySymbol(calls, symbol);
  if (found === undefined) {
    throw new ValidationError(`no call is written ${JSON.stringify(symbol)}`, {
      details: { symbol },
    });
  }
  return found;
}

/**
 * @param {RowBlock} block
 * @param {Method} method
 * @param {import('./call.js').Call} called
 * @param {string | null} position
 * @param {RungLead[]} leads
 * @returns {RowBlock}
 */
function ringOneLead(block, method, called, position, leads) {
  const changes = called.applyToLead(method);
  const lead = RowBlock.fromChanges(block.last(), changes);
  leads.push({ symbol: called.symbol, position, leadHead: lead.last(), changes });
  return block.concat(lead);
}

/**
 * Ring plain leads until the call would put the observation bell where the
 * calling position says, then ring the called lead.
 *
 * @param {RowBlock} block
 * @param {Method} method
 * @param {Record<string, import('./call.js').Call>} calls
 * @param {import('./call.js').Call} called
 * @param {string} position
 * @param {number} observation
 * @param {RungLead[]} leads
 * @returns {RowBlock}
 */
function ringToPosition(block, method, calls, called, position, observation, leads) {
  let current = block;
  for (let attempt = 0; attempt <= method.stage * 2; attempt += 1) {
    const trial = RowBlock.fromChanges(current.last(), called.applyToLead(method));
    if (positionOfCall(trial.last(), observation) === position) {
      leads.push({
        symbol: called.symbol,
        position,
        leadHead: trial.last(),
        changes: called.applyToLead(method),
      });
      return current.concat(trial);
    }
    current = ringOneLead(current, method, calls.plain, null, leads);
    if (current.changeCount > MAX_LEADS * method.leadLength) {
      break;
    }
  }
  throw new ValidationError(
    `the observation bell never reaches ${JSON.stringify(position)} in this method`,
    { details: { position, observation } },
  );
}

/**
 * Ring plain leads until the touch comes back to where it started.
 *
 * @param {RowBlock} block
 * @param {Method} method
 * @param {Record<string, import('./call.js').Call>} calls
 * @param {RungLead[]} leads
 * @param {import('../core/row.js').Row} start
 * @returns {RowBlock}
 */
function ringHome(block, method, calls, leads, start) {
  let current = block;
  let rung = 0;
  while (!current.last().equals(start) && rung < MAX_LEADS) {
    current = ringOneLead(current, method, calls.plain, null, leads);
    rung += 1;
  }
  if (!current.last().equals(start)) {
    throw new ValidationError('the touch never comes round', {
      details: { leads: leads.length },
    });
  }
  return current;
}
