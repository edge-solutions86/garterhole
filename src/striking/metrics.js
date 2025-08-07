import { TimingModel } from './timingModel.js';
import { ErrorAccumulator, AccumulatorSet } from './accumulator.js';
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
  const accumulator = new ErrorAccumulator();
  for (const error of errors) {
    accumulator.add(error);
  }
  return accumulator.rms;
}

/**
 * The overall accuracy of a piece of ringing.
 *
 * @param {readonly BlowError[]} errors
 * @returns {{ blows: number, rms: number, meanError: number, worst: BlowError | null }}
 */
export function accuracy(errors) {
  const accumulator = new ErrorAccumulator();
  for (const entry of errors) {
    accumulator.add(entry.error, entry);
  }
  return {
    ...accumulator.summary(),
    worst: accumulator.worst === null ? null : accumulator.worst.source,
  };
}

/**
 * Accuracy broken down by bell, which is what a band actually wants to see.
 *
 * @param {readonly BlowError[]} errors
 * @returns {Map<number, { blows: number, rms: number, meanError: number }>}
 */
export function accuracyByBell(errors) {
  return byKey(errors, (entry) => entry.bell).summaries((left, right) => left - right);
}

/**
 * Accuracy broken down by row, which is how a bad patch is found.
 *
 * @param {readonly BlowError[]} errors
 * @returns {{ rowIndex: number, rms: number }[]}
 */
export function accuracyByRow(errors) {
  const summaries = byKey(errors, (entry) => entry.rowIndex).summaries(
    (left, right) => left - right,
  );
  return [...summaries.entries()].map(([rowIndex, summary]) => ({ rowIndex, rms: summary.rms }));
}

/**
 * @param {readonly BlowError[]} errors
 * @param {(entry: BlowError) => unknown} keyOf
 * @returns {AccumulatorSet}
 */
function byKey(errors, keyOf) {
  const set = new AccumulatorSet();
  for (const entry of errors) {
    set.add(keyOf(entry), entry.error, entry);
  }
  return set;
}

/**
 * The bell that struck worst, or null when nothing was rung.
 *
 * @param {readonly BlowError[]} errors
 * @returns {number | null}
 */
export function worstBell(errors) {
  return byKey(errors, (entry) => entry.bell).worstKey();
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
