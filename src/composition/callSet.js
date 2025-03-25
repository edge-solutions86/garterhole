import { Call, call } from './call.js';
import { huntBells } from '../method/huntBells.js';
import { Method } from '../method/method.js';
import { bellSymbol } from '../core/bells.js';
import { ValidationError } from '../errors.js';

/**
 * The calls a band expects to be given for a method, without anybody having to
 * write them down.
 *
 * Nearly every treble dominated method takes the same two calls: a bob that
 * makes fourths and a single that makes fourths with a pair of places under it.
 * Methods with two hunt bells, which is the Grandsire family, take a different
 * pair, and those two families cover almost everything a tower rings.
 */

/**
 * The bob and single used with a method that has one hunt bell.
 *
 * @param {number} stage
 * @returns {{ bob: Call, single: Call }}
 */
export function fourthsPlaceCalls(stage) {
  if (stage < 5) {
    throw new ValidationError(`fourths place calls need at least five bells, not ${stage}`);
  }
  return {
    bob: call('Bob', '-', stage, `14`),
    single: call('Single', 's', stage, `1234`),
  };
}

/**
 * The bob and single used with the Grandsire family, which has two hunt bells.
 * The bob makes thirds; the single makes thirds and holds the two bells under
 * it as well.
 *
 * @param {number} stage
 * @returns {{ bob: Call, single: Call }}
 */
export function thirdsPlaceCalls(stage) {
  if (stage < 5) {
    throw new ValidationError(`thirds place calls need at least five bells, not ${stage}`);
  }
  return {
    bob: call('Bob', '-', stage, `3`),
    single: call('Single', 's', stage, `123`),
  };
}

/**
 * The calls that go with a method, chosen from the number of hunt bells it has.
 *
 * @param {Method} method
 * @returns {{ plain: Call, bob: Call, single: Call }}
 */
export function standardCalls(method) {
  if (!(method instanceof Method)) {
    throw new ValidationError('a Method is needed to choose its calls');
  }
  const hunts = huntBells(method);
  const pair = hunts.length >= 2 ? thirdsPlaceCalls(method.stage) : fourthsPlaceCalls(method.stage);
  return { plain: Call.plain(method.stage), ...pair };
}

/**
 * A call that makes a place at a named position, built for the methods that do
 * not take either standard pair. The place is written with the bell alphabet so
 * that a caller can read the notation back.
 *
 * @param {number} stage
 * @param {number} place
 * @param {{ name?: string, symbol?: string }} [naming]
 * @returns {Call}
 */
export function placeCall(stage, place, naming = {}) {
  const name = naming.name ?? `${bellSymbol(place)}ths place bob`;
  const symbol = naming.symbol ?? '-';
  return call(name, symbol, stage, `1${bellSymbol(place)}`);
}

/**
 * Look a call up by its symbol among a set of calls.
 *
 * @param {Record<string, Call>} calls
 * @param {string} symbol
 * @returns {Call | undefined}
 */
export function callBySymbol(calls, symbol) {
  for (const candidate of Object.values(calls)) {
    if (candidate.symbol === symbol) {
      return candidate;
    }
  }
  return undefined;
}

/**
 * Every symbol in a set of calls, which is what the touch reader needs before
 * it can read anything.
 *
 * @param {Record<string, Call>} calls
 * @returns {string[]}
 */
export function symbolsOf(calls) {
  return Object.values(calls).map((candidate) => candidate.symbol);
}
