import { Row } from '../core/row.js';
import { StrikeStream } from './stream.js';
import { ValidationError } from '../errors.js';

/**
 * Turning a stream of blows into rows.
 *
 * The bells strike in order, so every run of `stage` blows is a row and the
 * order they struck in is the row itself. That is the whole idea, and it works
 * as long as nobody drops a blow. When somebody does, the run of blows is no
 * longer a permutation, and saying so at once is far better than carrying on
 * and reporting every later row as badly struck.
 */

/**
 * @typedef {{
 *   row: Row,
 *   index: number,
 *   strikes: readonly { bell: number, at: number }[],
 *   start: number,
 *   end: number
 * }} StruckRow
 */

/**
 * Split a stream into rows.
 *
 * @param {StrikeStream} stream
 * @returns {StruckRow[]}
 */
export function splitRows(stream) {
  if (!(stream instanceof StrikeStream)) {
    throw new ValidationError('a StrikeStream is needed to split it into rows');
  }
  const stage = stream.stage;
  const rows = [];
  for (let start = 0; start + stage <= stream.length; start += stage) {
    const strikes = stream.strikes.slice(start, start + stage);
    rows.push({
      row: rowFrom(strikes, start / stage),
      index: start / stage,
      strikes: Object.freeze(strikes),
      start: strikes[0].at,
      end: strikes[strikes.length - 1].at,
    });
  }
  return rows;
}

/**
 * How many blows are left over at the end of a stream, which is a partial row
 * the analysis should ignore.
 *
 * @param {StrikeStream} stream
 * @returns {number}
 */
export function trailingBlows(stream) {
  return stream.length % stream.stage;
}

/**
 * Split a stream and report the first row that is not a permutation instead of
 * throwing, so that a recorder with one bad row still gives an analysis of
 * everything before it.
 *
 * @param {StrikeStream} stream
 * @returns {{ rows: StruckRow[], stoppedAt: number | null, reason: string | null }}
 */
export function splitRowsLeniently(stream) {
  if (!(stream instanceof StrikeStream)) {
    throw new ValidationError('a StrikeStream is needed to split it into rows');
  }
  const stage = stream.stage;
  const rows = [];
  for (let start = 0; start + stage <= stream.length; start += stage) {
    const strikes = stream.strikes.slice(start, start + stage);
    const index = start / stage;
    try {
      rows.push({
        row: rowFrom(strikes, index),
        index,
        strikes: Object.freeze(strikes),
        start: strikes[0].at,
        end: strikes[strikes.length - 1].at,
      });
    } catch (error) {
      return { rows, stoppedAt: index, reason: error.message };
    }
  }
  return { rows, stoppedAt: null, reason: null };
}

/**
 * The time each row took, from its first blow to the first blow of the next.
 *
 * @param {readonly StruckRow[]} rows
 * @returns {number[]} one entry shorter than the rows, since the last has no next
 */
export function rowDurations(rows) {
  const durations = [];
  for (let index = 1; index < rows.length; index += 1) {
    durations.push(rows[index].start - rows[index - 1].start);
  }
  return durations;
}

/**
 * Whether each row was struck at a handstroke, given that the first row of a
 * touch is. Handstroke and backstroke alternate all the way through.
 *
 * @param {readonly StruckRow[]} rows
 * @returns {boolean[]}
 */
export function handstrokes(rows) {
  return rows.map((row) => row.index % 2 === 0);
}

/**
 * @param {readonly { bell: number, at: number }[]} strikes
 * @param {number} index
 * @returns {Row}
 */
function rowFrom(strikes, index) {
  try {
    return new Row(strikes.map((strike) => strike.bell));
  } catch (error) {
    throw new ValidationError(`row ${index} is not a permutation: ${error.message}`, {
      details: { index, bells: strikes.map((strike) => strike.bell) },
    });
  }
}
