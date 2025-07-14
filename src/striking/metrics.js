import { TimingModel } from './timingModel.js';
import { mean } from '../util/collections.js';
import { ValidationError } from '../errors.js';

/**
 * How far every blow was from where it should have been.
 *
 * The error of a blow is the time it actually struck minus the time the model
 * says it should have. Early is negative and late is positive, and keeping the
 * sign matters: a band that is uniformly late is running slow, while a band
 * whose errors cancel out is ringing unevenly. The two need different advice.
 */

/**
 * @typedef {{
 *   bell: number,
 *   rowIndex: number,
 *   place: number,
 *   at: number,
 *   expected: number,
 *   error: number
 * }} BlowError
 */

/**
 * Work out the error of every blow.
 *
 * @param {readonly import('./rowSplitter.js').StruckRow[]} rows
 * @param {TimingModel} model
 * @returns {BlowError[]}
 */
export function blowErrors(rows, model) {
  if (!Array.isArray(rows)) {
    throw new ValidationError('a list of struck rows is needed');
  }
  if (!(model instanceof TimingModel)) {
    throw new ValidationError('a TimingModel is needed to measure against');
  }
  const errors = [];
  for (const row of rows) {
    row.strikes.forEach((strike, index) => {
      const place = index + 1;
      const expected = model.expectedAt(row.index, place);
      errors.push({
        bell: strike.bell,
        rowIndex: row.index,
        place,
        at: strike.at,
        expected,
        error: strike.at - expected,
      });
    });
  }
  return errors;
}

/**
 * The root mean square of a list of errors, which is the single number a
 * striking competition is decided on.
 *
 * @param {readonly number[]} errors
 * @returns {number}
 */
export function rootMeanSquare(errors) {
  if (errors.length === 0) {
    return 0;
  }
  const squares = errors.reduce((total, error) => total + error * error, 0);
  return Math.sqrt(squares / errors.length);
}

/**
 * The overall accuracy of a piece of ringing.
 *
 * @param {readonly BlowError[]} errors
 * @returns {{ blows: number, rms: number, meanError: number, worst: BlowError | null }}
 */
export function accuracy(errors) {
  const values = errors.map((entry) => entry.error);
  let worst = null;
  for (const entry of errors) {
    if (worst === null || Math.abs(entry.error) > Math.abs(worst.error)) {
      worst = entry;
    }
  }
  return {
    blows: errors.length,
    rms: rootMeanSquare(values),
    meanError: mean(values) ?? 0,
    worst,
  };
}

/**
 * Accuracy broken down by bell, which is what a band actually wants to see.
 *
 * @param {readonly BlowError[]} errors
 * @returns {Map<number, { blows: number, rms: number, meanError: number }>}
 */
export function accuracyByBell(errors) {
  /** @type {Map<number, number[]>} */
  const byBell = new Map();
  for (const entry of errors) {
    const bucket = byBell.get(entry.bell);
    if (bucket === undefined) {
      byBell.set(entry.bell, [entry.error]);
    } else {
      bucket.push(entry.error);
    }
  }
  const summary = new Map();
  for (const [bell, values] of [...byBell.entries()].sort((left, right) => left[0] - right[0])) {
    summary.set(bell, {
      blows: values.length,
      rms: rootMeanSquare(values),
      meanError: mean(values) ?? 0,
    });
  }
  return summary;
}

/**
 * Accuracy broken down by row, which is how a bad patch is found.
 *
 * @param {readonly BlowError[]} errors
 * @returns {{ rowIndex: number, rms: number }[]}
 */
export function accuracyByRow(errors) {
  /** @type {Map<number, number[]>} */
  const byRow = new Map();
  for (const entry of errors) {
    const bucket = byRow.get(entry.rowIndex);
    if (bucket === undefined) {
      byRow.set(entry.rowIndex, [entry.error]);
    } else {
      bucket.push(entry.error);
    }
  }
  return [...byRow.entries()]
    .sort((left, right) => left[0] - right[0])
    .map(([rowIndex, values]) => ({ rowIndex, rms: rootMeanSquare(values) }));
}

/**
 * The bell that struck worst, or null when nothing was rung.
 *
 * @param {readonly BlowError[]} errors
 * @returns {number | null}
 */
export function worstBell(errors) {
  let worst = null;
  let worstRms = -1;
  for (const [bell, summary] of accuracyByBell(errors)) {
    if (summary.rms > worstRms) {
      worst = bell;
      worstRms = summary.rms;
    }
  }
  return worst;
}

/**
 * The rows whose accuracy is worse than a threshold, so that a band can go and
 * listen to the bad patches rather than the whole recording.
 *
 * @param {readonly BlowError[]} errors
 * @param {number} threshold in milliseconds
 * @returns {number[]} the row indexes
 */
export function badPatches(errors, threshold) {
  if (typeof threshold !== 'number' || threshold < 0) {
    throw new ValidationError(`${threshold} is not a threshold in milliseconds`);
  }
  return accuracyByRow(errors)
    .filter((row) => row.rms > threshold)
    .map((row) => row.rowIndex);
}
