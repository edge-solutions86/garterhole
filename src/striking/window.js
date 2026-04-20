import { ErrorAccumulator } from './accumulator.js';
import { ValidationError } from '../errors.js';

/**
 * Watching the striking change as a touch goes on.
 *
 * One number for a whole quarter peal hides what a band actually wants to know,
 * which is that the ringing was fine for twenty minutes and then fell apart.
 * A rolling window over the rows shows that, and it shows it as a shape rather
 * than as a list of nine hundred numbers.
 *
 * The window is measured in rows rather than in seconds, because a band that
 * slows down should not get a longer window as a consolation.
 */

/**
 * The accuracy of each window of rows.
 *
 * @param {readonly import('./metrics.js').BlowError[]} errors
 * @param {{ rows?: number, step?: number }} [options]
 * @returns {{ from: number, to: number, rms: number, blows: number }[]}
 */
export function rollingAccuracy(errors, options = {}) {
  const rows = options.rows ?? 20;
  const step = options.step ?? rows;
  if (!Number.isInteger(rows) || rows < 1) {
    throw new ValidationError(`a window of ${rows} rows measures nothing`);
  }
  if (!Number.isInteger(step) || step < 1) {
    throw new ValidationError(`a step of ${step} rows would not move`);
  }
  const byRow = groupByRow(errors);
  const rowNumbers = [...byRow.keys()].sort((left, right) => left - right);
  if (rowNumbers.length === 0) {
    return [];
  }
  const last = rowNumbers[rowNumbers.length - 1];
  const windows = [];
  for (let from = rowNumbers[0]; from <= last; from += step) {
    const to = from + rows - 1;
    const accumulator = new ErrorAccumulator();
    for (let row = from; row <= to; row += 1) {
      for (const error of byRow.get(row) ?? []) {
        accumulator.add(error);
      }
    }
    if (accumulator.count > 0) {
      windows.push({ from, to, rms: accumulator.rms, blows: accumulator.count });
    }
  }
  return windows;
}

/**
 * Whether the striking got better or worse over the piece, as the difference
 * between the first window and the last.
 *
 * @param {readonly { rms: number }[]} windows
 * @returns {{ direction: 'better' | 'worse' | 'level', change: number } | null}
 */
export function trend(windows) {
  if (windows.length < 2) {
    return null;
  }
  const change = windows[windows.length - 1].rms - windows[0].rms;
  if (Math.abs(change) < 5) {
    return { direction: 'level', change };
  }
  return { direction: change < 0 ? 'better' : 'worse', change };
}

/**
 * The window the ringing was worst in.
 *
 * @param {readonly { from: number, to: number, rms: number }[]} windows
 * @returns {{ from: number, to: number, rms: number } | null}
 */
export function worstWindow(windows) {
  let worst = null;
  for (const window of windows) {
    if (worst === null || window.rms > worst.rms) {
      worst = window;
    }
  }
  return worst;
}

/**
 * A one line description of how the piece went.
 *
 * @param {readonly { from: number, to: number, rms: number }[]} windows
 * @returns {string}
 */
export function describeTrend(windows) {
  if (windows.length === 0) {
    return 'nothing to measure';
  }
  const moving = trend(windows);
  const worst = worstWindow(windows);
  const opening = moving === null ? 'one window only' : `the striking got ${moving.direction}`;
  return `${opening}, worst between rows ${worst.from} and ${worst.to} at ${Math.round(worst.rms)}ms`;
}

/**
 * @param {readonly import('./metrics.js').BlowError[]} errors
 * @returns {Map<number, number[]>}
 */
function groupByRow(errors) {
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
  return byRow;
}
