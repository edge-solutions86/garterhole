/**
 * Stages are the number of bells a method is rung on. Ringers name them rather
 * than count them, and the names are not derived from the number in any regular
 * way, so the table is the definition.
 */
const STAGE_NAMES = Object.freeze({
  3: 'Singles',
  4: 'Minimus',
  5: 'Doubles',
  6: 'Minor',
  7: 'Triples',
  8: 'Major',
  9: 'Caters',
  10: 'Royal',
  11: 'Cinques',
  12: 'Maximus',
  13: 'Sextuples',
  14: 'Fourteen',
  15: 'Septuples',
  16: 'Sixteen',
});

/**
 * The smallest stage that has a name and a working method literature.
 */
export const MIN_STAGE = 3;

/**
 * The largest stage this library will build rows for. Sixteen is the practical
 * ceiling for a tower; anything above it is handbell territory and uses a
 * different symbol set.
 */
export const MAX_STAGE = 16;

/**
 * @param {number} stage
 * @returns {boolean} true when the stage is one this library can work with
 */
export function isSupportedStage(stage) {
  return Number.isInteger(stage) && stage >= MIN_STAGE && stage <= MAX_STAGE;
}

/**
 * @param {number} stage
 * @returns {string} the ringing name for the stage
 */
export function stageName(stage) {
  const name = STAGE_NAMES[stage];
  if (name === undefined) {
    throw new RangeError(`no stage name for ${stage} bells`);
  }
  return name;
}

/**
 * Look a stage up by its ringing name. Comparison ignores case so that input
 * typed by a ringer in a hurry still resolves.
 *
 * @param {string} name
 * @returns {number} the stage
 */
export function stageFromName(name) {
  const wanted = String(name).trim().toLowerCase();
  for (const [stage, stageLabel] of Object.entries(STAGE_NAMES)) {
    if (stageLabel.toLowerCase() === wanted) {
      return Number(stage);
    }
  }
  throw new RangeError(`unknown stage name ${JSON.stringify(name)}`);
}

/**
 * Odd stages are rung with the tenor covering, so the number of bells that
 * actually change places is one less than the number of bells in the tower.
 *
 * @param {number} stage
 * @returns {boolean}
 */
export function isOddStage(stage) {
  return stage % 2 === 1;
}

/**
 * The number of bells taking part in the changes. On an odd stage the cover
 * bell stands behind and is not part of the permutation.
 *
 * @param {number} stage
 * @returns {number}
 */
export function changingBells(stage) {
  return isOddStage(stage) ? stage - 1 : stage;
}

/**
 * Every stage this library supports, ascending.
 *
 * @returns {number[]}
 */
export function supportedStages() {
  return Object.keys(STAGE_NAMES)
    .map(Number)
    .sort((a, b) => a - b);
}
