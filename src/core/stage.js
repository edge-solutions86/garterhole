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
 * @param {number} stage
 * @returns {boolean}
 */
export function isOddStage(stage) {
  return stage % 2 === 1;
}

/**
 * True when the stage is normally rung with a bell covering behind.
 *
 * The cover bell is not part of the stage. Doubles is five bells changing, and
 * the tower rings it on six with the tenor behind. Counting the cover into the
 * stage is a mistake that makes the extent of Doubles come out as twenty four
 * rows instead of a hundred and twenty.
 *
 * @param {number} stage
 * @returns {boolean}
 */
export function hasCover(stage) {
  return isOddStage(stage);
}

/**
 * How many bells the tower has to have to ring the stage, which is one more
 * than the stage when a bell covers.
 *
 * @param {number} stage
 * @returns {number}
 */
export function bellsInTower(stage) {
  return hasCover(stage) ? stage + 1 : stage;
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
