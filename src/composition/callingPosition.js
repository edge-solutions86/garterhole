import { isSupportedStage } from '../core/stage.js';
import { ValidationError } from '../errors.js';

/**
 * Calling positions. A conductor says "wrong" rather than "a bob at the end of
 * the fourth lead", because the position tells the band where the observation
 * bell is and the lead number does not.
 *
 * The convention here is the usual one: the position is named by the place the
 * observation bell occupies in the lead head that follows the call. Home is at
 * the back, Wrong two places under it, and Middle two under that. The front
 * three places are Before, In and Out.
 *
 * Fourths and Middle land on the same place at Major, and Fifths and Wrong do
 * the same at Minor. Where two names collide the one nearer the back wins,
 * which is what a band would say out loud.
 */

const NAMES = Object.freeze({
  B: 'Before',
  I: 'In',
  O: 'Out',
  F: 'Fourths',
  V: 'Fifths',
  M: 'Middle',
  W: 'Wrong',
  H: 'Home',
});

/**
 * The place each named position sits at, for a stage. Positions that fall
 * outside the stage are left out.
 *
 * @param {number} stage
 * @returns {Map<string, number>}
 */
export function positionPlaces(stage) {
  assertStage(stage);
  const wanted = [
    ['B', 1],
    ['I', 2],
    ['O', 3],
    ['F', 4],
    ['V', 5],
    ['M', stage - 4],
    ['W', stage - 2],
    ['H', stage],
  ];
  const places = new Map();
  for (const [code, place] of wanted) {
    if (place >= 1 && place <= stage) {
      places.set(code, place);
    }
  }
  return places;
}

/**
 * The code for each place, with the collisions resolved in favour of the name
 * nearer the back.
 *
 * @param {number} stage
 * @returns {Map<number, string>}
 */
export function placeCodes(stage) {
  const codes = new Map();
  for (const [code, place] of positionPlaces(stage)) {
    codes.set(place, code);
  }
  return codes;
}

/**
 * The position code for a place, or the place written as a number when it has
 * no name.
 *
 * @param {number} stage
 * @param {number} place
 * @returns {string}
 */
export function positionAt(stage, place) {
  assertStage(stage);
  if (!Number.isInteger(place) || place < 1 || place > stage) {
    throw new ValidationError(`place ${place} is outside 1..${stage}`);
  }
  return placeCodes(stage).get(place) ?? String(place);
}

/**
 * The place a named position sits at.
 *
 * @param {number} stage
 * @param {string} code
 * @returns {number}
 */
export function placeForPosition(stage, code) {
  const places = positionPlaces(stage);
  const place = places.get(code);
  if (place !== undefined) {
    return place;
  }
  const asNumber = Number(code);
  if (Number.isInteger(asNumber) && asNumber >= 1 && asNumber <= stage) {
    return asNumber;
  }
  throw new ValidationError(`${JSON.stringify(code)} is not a calling position on ${stage}`, {
    details: { stage, code },
  });
}

/**
 * The long name of a position code.
 *
 * @param {string} code
 * @returns {string}
 */
export function positionName(code) {
  return NAMES[code] ?? code;
}

/**
 * Every position that has a name at a stage, from the front to the back.
 *
 * @param {number} stage
 * @returns {{ code: string, place: number, name: string }[]}
 */
export function namedPositions(stage) {
  const codes = placeCodes(stage);
  return [...codes.entries()]
    .sort((left, right) => left[0] - right[0])
    .map(([place, code]) => ({ code, place, name: positionName(code) }));
}

/**
 * The position a call was made at, worked out from where the observation bell
 * ended up.
 *
 * @param {import('../core/row.js').Row} leadHead the row after the call
 * @param {number} observation the bell being watched, usually the tenor
 * @returns {string}
 */
export function positionOfCall(leadHead, observation) {
  const place = leadHead.placeOf(observation);
  return positionAt(leadHead.stage, place);
}

/**
 * @param {number} stage
 */
function assertStage(stage) {
  if (!isSupportedStage(stage)) {
    throw new ValidationError(`stage ${stage} is not supported`);
  }
}
