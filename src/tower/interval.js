import { ValidationError } from '../errors.js';

/**
 * Half open intervals of time, and sets of them.
 *
 * Availability, practice slots and service ringing are all runs of time with a
 * start and an end, and the questions asked of them are all the same: do these
 * two overlap, what do they have in common, and what is left when one is taken
 * out of the other. Doing that arithmetic once here is much safer than doing it
 * again in every module that needs it.
 *
 * Intervals include their start and exclude their end, so two intervals that
 * meet at a point do not overlap.
 */
export class Interval {
  /**
   * @param {number} start
   * @param {number} end
   */
  constructor(start, end) {
    if (typeof start !== 'number' || !Number.isFinite(start)) {
      throw new ValidationError(`${start} is not a moment in time`);
    }
    if (typeof end !== 'number' || !Number.isFinite(end)) {
      throw new ValidationError(`${end} is not a moment in time`);
    }
    if (end <= start) {
      throw new ValidationError(`an interval cannot end at ${end} having started at ${start}`);
    }
    this.start = start;
    this.end = end;
    Object.freeze(this);
  }

  /**
   * @returns {number}
   */
  get length() {
    return this.end - this.start;
  }

  /**
   * @param {number} moment
   * @returns {boolean}
   */
  contains(moment) {
    return moment >= this.start && moment < this.end;
  }

  /**
   * @param {Interval} other
   * @returns {boolean}
   */
  overlaps(other) {
    return this.start < other.end && other.start < this.end;
  }

  /**
   * True when this interval holds the whole of another.
   *
   * @param {Interval} other
   * @returns {boolean}
   */
  covers(other) {
    return this.start <= other.start && this.end >= other.end;
  }

  /**
   * The part two intervals have in common, or null when they have none.
   *
   * @param {Interval} other
   * @returns {Interval | null}
   */
  intersect(other) {
    const start = Math.max(this.start, other.start);
    const end = Math.min(this.end, other.end);
    return end > start ? new Interval(start, end) : null;
  }

  /**
   * @param {Interval} other
   * @returns {boolean}
   */
  equals(other) {
    return other instanceof Interval && other.start === this.start && other.end === this.end;
  }

  /**
   * @returns {string}
   */
  toString() {
    return `[${this.start}, ${this.end})`;
  }
}

/**
 * A set of intervals held in order and never overlapping. Adding an interval
 * that touches one already in the set merges the two, which is what makes the
 * arithmetic below terminate.
 */
export class IntervalSet {
  /**
   * @param {Iterable<Interval>} [intervals]
   */
  constructor(intervals = []) {
    /** @type {Interval[]} */
    this.intervals = [];
    for (const interval of intervals) {
      this.add(interval);
    }
  }

  /**
   * @param {Interval} interval
   * @returns {IntervalSet} this
   */
  add(interval) {
    if (!(interval instanceof Interval)) {
      throw new ValidationError('only an Interval can go into an interval set');
    }
    let start = interval.start;
    let end = interval.end;
    const kept = [];
    for (const existing of this.intervals) {
      if (existing.end < start || existing.start > end) {
        kept.push(existing);
        continue;
      }
      start = Math.min(start, existing.start);
      end = Math.max(end, existing.end);
    }
    kept.push(new Interval(start, end));
    this.intervals = kept.sort((left, right) => left.start - right.start);
    return this;
  }

  /**
   * @param {number} moment
   * @returns {boolean}
   */
  contains(moment) {
    return this.intervals.some((interval) => interval.contains(moment));
  }

  /**
   * True when the whole of an interval is in the set.
   *
   * @param {Interval} interval
   * @returns {boolean}
   */
  covers(interval) {
    return this.intervals.some((existing) => existing.covers(interval));
  }

  /**
   * The parts two sets have in common.
   *
   * @param {IntervalSet} other
   * @returns {IntervalSet}
   */
  intersect(other) {
    const result = new IntervalSet();
    for (const mine of this.intervals) {
      for (const theirs of other.intervals) {
        const shared = mine.intersect(theirs);
        if (shared !== null) {
          result.add(shared);
        }
      }
    }
    return result;
  }

  /**
   * How much time the set holds altogether.
   *
   * @returns {number}
   */
  get total() {
    return this.intervals.reduce((sum, interval) => sum + interval.length, 0);
  }

  /**
   * @returns {number}
   */
  get size() {
    return this.intervals.length;
  }

  /**
   * @returns {IterableIterator<Interval>}
   */
  [Symbol.iterator]() {
    return this.intervals[Symbol.iterator]();
  }

  /**
   * @returns {string}
   */
  toString() {
    return this.intervals.map((interval) => interval.toString()).join(' ');
  }
}

/**
 * @param {number} start
 * @param {number} end
 * @returns {Interval}
 */
export function interval(start, end) {
  return new Interval(start, end);
}
