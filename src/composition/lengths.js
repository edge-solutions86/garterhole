import { ValidationError } from '../errors.js';

/**
 * What counts as a peal, a quarter peal or a date touch.
 *
 * The lengths are not arbitrary. A peal on fewer than eight bells has to be a
 * whole number of extents, which is why a peal of Minor is 5040 and not 5000:
 * seven extents of 720. On eight and above there are far more rows than a peal
 * needs, so five thousand changes and a round finish is the whole rule.
 *
 * Quarter peals have no whole extent rule, which is why a quarter of Doubles is
 * 1260 changes rather than the eleven extents that would be 1320.
 */

/** The smallest number of changes that can be called a peal. */
export const PEAL_MINIMUM = 5000;

/** The smallest number of changes that can be called a quarter peal. */
export const QUARTER_MINIMUM = 1250;

/** Below this stage a peal must be whole extents. */
export const WHOLE_EXTENT_STAGE = 8;

/**
 * How many rows there are at a stage.
 *
 * @param {number} stage
 * @returns {number}
 */
export function extentLength(stage) {
  if (!Number.isInteger(stage) || stage < 1) {
    throw new ValidationError(`stage ${stage} has no extent`);
  }
  let total = 1;
  for (let bell = 2; bell <= stage; bell += 1) {
    total *= bell;
  }
  return total;
}

/**
 * True when a peal at this stage has to be a whole number of extents.
 *
 * @param {number} stage
 * @returns {boolean}
 */
export function needsWholeExtents(stage) {
  return stage < WHOLE_EXTENT_STAGE;
}

/**
 * The shortest length that counts as a peal at a stage.
 *
 * @param {number} stage
 * @returns {number}
 */
export function pealLength(stage) {
  if (!needsWholeExtents(stage)) {
    return PEAL_MINIMUM;
  }
  const extent = extentLength(stage);
  return Math.ceil(PEAL_MINIMUM / extent) * extent;
}

/**
 * The shortest length that counts as a quarter peal. It does not depend on the
 * stage, which is worth stating rather than leaving to be inferred.
 *
 * @returns {number}
 */
export function quarterLength() {
  return QUARTER_MINIMUM;
}

/**
 * What a length counts as at a stage.
 *
 * @param {number} changes
 * @param {number} stage
 * @returns {'peal' | 'quarter peal' | 'touch'}
 */
export function classifyLength(changes, stage) {
  if (!Number.isInteger(changes) || changes < 0) {
    throw new ValidationError(`a touch cannot be ${changes} changes long`);
  }
  if (checkLength(changes, stage, 'peal').ok) {
    return 'peal';
  }
  if (checkLength(changes, stage, 'quarter peal').ok) {
    return 'quarter peal';
  }
  return 'touch';
}

/**
 * Check a length, and say what is wrong with it when it does not qualify.
 *
 * @param {number} changes
 * @param {number} stage
 * @param {'peal' | 'quarter peal'} wanted
 * @returns {{ ok: boolean, reason: string | null, needed: number }}
 */
export function checkLength(changes, stage, wanted) {
  if (wanted !== 'peal' && wanted !== 'quarter peal') {
    throw new ValidationError(`${JSON.stringify(wanted)} is not a length to check against`);
  }
  const needed = wanted === 'peal' ? pealLength(stage) : quarterLength();
  if (changes < needed) {
    return {
      ok: false,
      needed,
      reason: `a ${wanted} on ${stage} needs at least ${needed} changes, not ${changes}`,
    };
  }
  if (wanted === 'peal' && needsWholeExtents(stage) && changes % extentLength(stage) !== 0) {
    return {
      ok: false,
      needed,
      reason: `on ${stage} a peal must be whole extents of ${extentLength(stage)}`,
    };
  }
  return { ok: true, needed, reason: null };
}

/**
 * How many extents a length comes to, or null when it is not a whole number of
 * them.
 *
 * @param {number} changes
 * @param {number} stage
 * @returns {number | null}
 */
export function extentsIn(changes, stage) {
  const extent = extentLength(stage);
  return changes % extent === 0 ? changes / extent : null;
}

/**
 * Is this a date touch, that is a touch whose length is the date written out?
 * Both the British and the American order are accepted, because a band will
 * take whichever one gives a length they can ring.
 *
 * @param {number} changes
 * @param {Date} date
 * @returns {boolean}
 */
export function isDateTouch(changes, date) {
  if (!(date instanceof Date) || Number.isNaN(date.getTime())) {
    throw new ValidationError('a date is needed to check a date touch');
  }
  const day = date.getDate();
  const month = date.getMonth() + 1;
  const year = date.getFullYear();
  const candidates = new Set([
    Number(`${day}${String(month).padStart(2, '0')}`),
    Number(`${month}${String(day).padStart(2, '0')}`),
    year,
  ]);
  return candidates.has(changes);
}
