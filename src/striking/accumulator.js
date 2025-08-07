import { ValidationError } from '../errors.js';

/**
 * Summing errors without holding on to them.
 *
 * The per bell and per row breakdowns started as three copies of the same
 * grouping loop, each building an array of every error and then folding it. For
 * a peal that is two hundred thousand numbers held in memory to produce a
 * handful of averages.
 *
 * An accumulator keeps a count, a sum and a sum of squares, which is everything
 * the mean and the root mean square need, and it does it in constant space. It
 * also means the analysis can run against a live recording rather than waiting
 * for the band to finish.
 */
export class ErrorAccumulator {
  constructor() {
    this.count = 0;
    this.total = 0;
    this.totalSquares = 0;
    this.largest = null;
  }

  /**
   * Take one error.
   *
   * @param {number} error
   * @param {unknown} [source] whatever the error came from, kept if it is the worst
   * @returns {ErrorAccumulator} this, so that adds can be chained
   */
  add(error, source) {
    if (typeof error !== 'number' || !Number.isFinite(error)) {
      throw new ValidationError(`${error} is not an error in milliseconds`);
    }
    this.count += 1;
    this.total += error;
    this.totalSquares += error * error;
    if (this.largest === null || Math.abs(error) > Math.abs(this.largest.error)) {
      this.largest = { error, source: source ?? null };
    }
    return this;
  }

  /**
   * The mean error, keeping its sign, or zero when nothing has been added.
   *
   * @returns {number}
   */
  get meanError() {
    return this.count === 0 ? 0 : this.total / this.count;
  }

  /**
   * The root mean square, which is the number a competition is judged on.
   *
   * @returns {number}
   */
  get rms() {
    return this.count === 0 ? 0 : Math.sqrt(this.totalSquares / this.count);
  }

  /**
   * The worst error seen, or null.
   *
   * @returns {{ error: number, source: unknown } | null}
   */
  get worst() {
    return this.largest;
  }

  /**
   * The summary the reports use.
   *
   * @returns {{ blows: number, rms: number, meanError: number }}
   */
  summary() {
    return { blows: this.count, rms: this.rms, meanError: this.meanError };
  }
}

/**
 * A set of accumulators keyed on something, which is what every breakdown in
 * the analysis is.
 */
export class AccumulatorSet {
  constructor() {
    /** @type {Map<unknown, ErrorAccumulator>} */
    this.byKey = new Map();
  }

  /**
   * @param {unknown} key
   * @param {number} error
   * @param {unknown} [source]
   * @returns {AccumulatorSet}
   */
  add(key, error, source) {
    let accumulator = this.byKey.get(key);
    if (accumulator === undefined) {
      accumulator = new ErrorAccumulator();
      this.byKey.set(key, accumulator);
    }
    accumulator.add(error, source);
    return this;
  }

  /**
   * The summaries, in key order.
   *
   * @param {(left: unknown, right: unknown) => number} [order]
   * @returns {Map<unknown, { blows: number, rms: number, meanError: number }>}
   */
  summaries(order = (left, right) => (left < right ? -1 : left > right ? 1 : 0)) {
    const keys = [...this.byKey.keys()].sort(order);
    const summaries = new Map();
    for (const key of keys) {
      summaries.set(key, this.byKey.get(key).summary());
    }
    return summaries;
  }

  /**
   * The key with the worst root mean square, or null when nothing was added.
   *
   * @returns {unknown}
   */
  worstKey() {
    let worst = null;
    let worstRms = -1;
    for (const [key, accumulator] of this.byKey) {
      if (accumulator.rms > worstRms) {
        worst = key;
        worstRms = accumulator.rms;
      }
    }
    return worst;
  }
}
