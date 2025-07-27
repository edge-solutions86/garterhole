import { StrikeStream, streamFrom } from './stream.js';
import { splitRows, splitRowsLeniently, handstrokes } from './rowSplitter.js';
import { fitModel } from './timingModel.js';
import { blowErrors, accuracy, accuracyByBell, badPatches } from './metrics.js';
import { findFaults, countFaults } from './faults.js';
import { ValidationError } from '../errors.js';

/**
 * A whole piece of ringing, analysed end to end.
 *
 * This is the front door of the striking package: hand it what the recorder
 * produced and it gives back everything the other modules can work out. The
 * band is not interested in the intermediate objects, only in the answer.
 *
 * The timing model is fitted to the ringing rather than assumed, because a
 * band rings at the speed it rings at and judging it against somebody else's
 * speed measures the wrong thing.
 */
export class StrikingSession {
  /**
   * @param {{
   *   stream: StrikeStream,
   *   model?: import('./timingModel.js').TimingModel,
   *   thresholds?: Record<string, number>,
   *   lenient?: boolean
   * }} options
   */
  constructor(options) {
    if (options === null || typeof options !== 'object') {
      throw new ValidationError('a striking session needs its options');
    }
    if (!(options.stream instanceof StrikeStream)) {
      throw new ValidationError('a striking session needs a StrikeStream');
    }
    const lenient = options.lenient ?? false;
    const split = lenient
      ? splitRowsLeniently(options.stream)
      : { rows: splitRows(options.stream), stoppedAt: null, reason: null };
    if (split.rows.length < 2) {
      throw new ValidationError('a striking session needs at least two rows to say anything');
    }
    this.stream = options.stream;
    this.rows = split.rows;
    this.stoppedAt = split.stoppedAt;
    this.stoppedReason = split.reason;
    this.model = options.model ?? fitModel(split.rows, options.stream.stage);
    this.thresholds = options.thresholds ?? {};
    this.errors = blowErrors(split.rows, this.model);
    Object.freeze(this);
  }

  /**
   * @returns {number}
   */
  get rowCount() {
    return this.rows.length;
  }

  /**
   * The overall accuracy of the ringing.
   *
   * @returns {ReturnType<typeof accuracy>}
   */
  accuracy() {
    return accuracy(this.errors);
  }

  /**
   * Accuracy for each bell.
   *
   * @returns {ReturnType<typeof accuracyByBell>}
   */
  byBell() {
    return accuracyByBell(this.errors);
  }

  /**
   * The named faults.
   *
   * @returns {ReturnType<typeof findFaults>}
   */
  faults() {
    return findFaults(this.errors, this.thresholds);
  }

  /**
   * How many faults of each kind.
   *
   * @returns {Record<string, number>}
   */
  faultCounts() {
    return countFaults(this.faults());
  }

  /**
   * The rows worth going back and listening to.
   *
   * @param {number} [threshold]
   * @returns {number[]}
   */
  worstRows(threshold = 50) {
    return badPatches(this.errors, threshold);
  }

  /**
   * Whether each row was a handstroke.
   *
   * @returns {boolean[]}
   */
  strokes() {
    return handstrokes(this.rows);
  }

  /**
   * The rows the band actually rang, which the composition checker can compare
   * against what they meant to ring.
   *
   * @returns {import('../core/row.js').Row[]}
   */
  rungRows() {
    return this.rows.map((row) => row.row);
  }
}

/**
 * Build a session straight from raw strike data.
 *
 * @param {number} stage
 * @param {readonly { bell: number, at: number }[]} strikes
 * @param {{ model?: object, thresholds?: Record<string, number>, lenient?: boolean }} [options]
 * @returns {StrikingSession}
 */
export function analyseStriking(stage, strikes, options = {}) {
  return new StrikingSession({ stream: streamFrom(stage, strikes), ...options });
}
