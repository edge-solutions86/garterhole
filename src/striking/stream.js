import { isSupportedStage } from '../core/stage.js';
import { countBy } from '../util/collections.js';
import { ValidationError } from '../errors.js';

/**
 * A strike stream is what comes out of a recorder in the tower: one entry per
 * blow, saying which bell struck and when, in milliseconds from the start of
 * the recording.
 *
 * Everything downstream assumes the stream is sorted and that every bell of the
 * stage appears. Both are checked here rather than being taken on trust,
 * because a dropped blow shifts every row after it and the analysis would
 * quietly report the whole touch as badly struck.
 */
export class StrikeStream {
  /**
   * @param {number} stage
   * @param {readonly { bell: number, at: number }[]} strikes
   */
  constructor(stage, strikes) {
    if (!isSupportedStage(stage)) {
      throw new ValidationError(`stage ${stage} is not supported`);
    }
    if (!Array.isArray(strikes)) {
      throw new ValidationError('a strike stream needs an array of strikes');
    }
    const kept = strikes.map((strike, index) => readStrike(strike, index, stage));
    for (let index = 1; index < kept.length; index += 1) {
      if (kept[index].at < kept[index - 1].at) {
        throw new ValidationError(
          `strike ${index} is at ${kept[index].at} which is before strike ${index - 1}`,
          { details: { index } },
        );
      }
    }
    this.stage = stage;
    /** @type {readonly { bell: number, at: number }[]} */
    this.strikes = Object.freeze(kept);
    Object.freeze(this);
  }

  /**
   * @returns {number}
   */
  get length() {
    return this.strikes.length;
  }

  /**
   * How long the recording runs, in milliseconds.
   *
   * @returns {number}
   */
  get duration() {
    if (this.strikes.length === 0) {
      return 0;
    }
    return this.strikes[this.strikes.length - 1].at - this.strikes[0].at;
  }

  /**
   * The gaps between one blow and the next.
   *
   * @returns {number[]}
   */
  gaps() {
    const gaps = [];
    for (let index = 1; index < this.strikes.length; index += 1) {
      gaps.push(this.strikes[index].at - this.strikes[index - 1].at);
    }
    return gaps;
  }

  /**
   * How many blows each bell struck.
   *
   * @returns {Map<number, number>}
   */
  blowsPerBell() {
    return countBy(this.strikes, (strike) => strike.bell);
  }

  /**
   * The bells that struck fewer blows than the rest, which is how a dropped
   * blow or a bell that stopped ringing shows up.
   *
   * @returns {number[]}
   */
  bellsWithMissingBlows() {
    const counts = this.blowsPerBell();
    const expected = Math.max(...counts.values(), 0);
    const missing = [];
    for (let bell = 1; bell <= this.stage; bell += 1) {
      if ((counts.get(bell) ?? 0) < expected) {
        missing.push(bell);
      }
    }
    return missing;
  }

  /**
   * The strikes between two times, the start included and the end not.
   *
   * @param {number} from
   * @param {number} to
   * @returns {{ bell: number, at: number }[]}
   */
  between(from, to) {
    return this.strikes.filter((strike) => strike.at >= from && strike.at < to);
  }

  /**
   * @returns {IterableIterator<{ bell: number, at: number }>}
   */
  [Symbol.iterator]() {
    return this.strikes[Symbol.iterator]();
  }
}

/**
 * Build a stream from whatever the recorder produced, sorting it into time
 * order first. Sorting rather than refusing is right here: two bells can be
 * recorded in either order when they strike within a millisecond of each other,
 * and that is not an error.
 *
 * @param {number} stage
 * @param {readonly { bell: number, at: number }[]} strikes
 * @returns {StrikeStream}
 */
export function streamFrom(stage, strikes) {
  if (!Array.isArray(strikes)) {
    throw new ValidationError('a strike stream needs an array of strikes');
  }
  const sorted = [...strikes].sort((left, right) => left.at - right.at);
  return new StrikeStream(stage, sorted);
}

/**
 * @param {unknown} strike
 * @param {number} index
 * @param {number} stage
 * @returns {{ bell: number, at: number }}
 */
function readStrike(strike, index, stage) {
  if (strike === null || typeof strike !== 'object') {
    throw new ValidationError(`strike ${index} is not an object`);
  }
  const { bell, at } = strike;
  if (!Number.isInteger(bell) || bell < 1 || bell > stage) {
    throw new ValidationError(`strike ${index} names bell ${bell}, which is not on ${stage}`);
  }
  if (typeof at !== 'number' || !Number.isFinite(at)) {
    throw new ValidationError(`strike ${index} has no time`);
  }
  return Object.freeze({ bell, at });
}
