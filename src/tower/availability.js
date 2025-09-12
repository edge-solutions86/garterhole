import { Interval, IntervalSet } from './interval.js';
import { assertNonEmptyString } from '../util/assert.js';
import { ValidationError } from '../errors.js';

/**
 * Who is free when.
 *
 * A band's diary is a set of intervals per ringer. The questions asked of it
 * are always the same two: who can come to this practice, and when could we
 * hold one that everybody can come to.
 *
 * A ringer with nothing written down is treated as unavailable rather than
 * available. That is the safer way round: a plan built on a ringer who never
 * said they were coming falls apart in the tower, and a plan that leaves
 * somebody out only needs a phone call.
 */
export class Diary {
  constructor() {
    /** @type {Map<string, IntervalSet>} */
    this.byRinger = new Map();
  }

  /**
   * Write down that a ringer is free for a stretch of time.
   *
   * @param {string} ringerId
   * @param {Interval} slot
   * @returns {Diary} this
   */
  free(ringerId, slot) {
    assertNonEmptyString(ringerId, 'a ringer id');
    if (!(slot instanceof Interval)) {
      throw new ValidationError('a diary entry needs an Interval');
    }
    let set = this.byRinger.get(ringerId);
    if (set === undefined) {
      set = new IntervalSet();
      this.byRinger.set(ringerId, set);
    }
    set.add(slot);
    return this;
  }

  /**
   * Everything a ringer has said they are free for.
   *
   * @param {string} ringerId
   * @returns {IntervalSet}
   */
  availabilityOf(ringerId) {
    return this.byRinger.get(ringerId) ?? new IntervalSet();
  }

  /**
   * True when a ringer is free for the whole of a slot.
   *
   * @param {string} ringerId
   * @param {Interval} slot
   * @returns {boolean}
   */
  isFree(ringerId, slot) {
    return this.availabilityOf(ringerId).covers(slot);
  }

  /**
   * Who could come to a practice at a given time, in id order.
   *
   * @param {Interval} slot
   * @returns {string[]}
   */
  whoIsFree(slot) {
    const free = [];
    for (const ringerId of this.byRinger.keys()) {
      if (this.isFree(ringerId, slot)) {
        free.push(ringerId);
      }
    }
    return free.sort();
  }

  /**
   * How many of the band could come, which is the number a captain looks at
   * before deciding whether to cancel.
   *
   * @param {Interval} slot
   * @returns {number}
   */
  countFree(slot) {
    return this.whoIsFree(slot).length;
  }

  /**
   * The times everybody named is free at once.
   *
   * @param {readonly string[]} ringerIds
   * @returns {IntervalSet}
   */
  commonTime(ringerIds) {
    if (!Array.isArray(ringerIds) || ringerIds.length === 0) {
      throw new ValidationError('naming nobody has no common time');
    }
    let shared = this.availabilityOf(ringerIds[0]);
    for (const ringerId of ringerIds.slice(1)) {
      shared = shared.intersect(this.availabilityOf(ringerId));
    }
    return shared;
  }

  /**
   * The best time to hold a practice of a given length, judged by how many of
   * the band could come. Ties go to the earliest slot, so that a captain asked
   * to explain the choice has an answer.
   *
   * @param {readonly Interval[]} candidates
   * @param {number} [needed] the smallest band worth ringing with
   * @returns {{ slot: Interval, band: string[] } | null}
   */
  bestSlot(candidates, needed = 1) {
    if (!Array.isArray(candidates)) {
      throw new ValidationError('a list of candidate slots is needed');
    }
    let best = null;
    for (const slot of candidates) {
      const band = this.whoIsFree(slot);
      if (band.length < needed) {
        continue;
      }
      if (best === null || band.length > best.band.length) {
        best = { slot, band };
      }
    }
    return best;
  }

  /**
   * Every ringer with anything written down.
   *
   * @returns {string[]}
   */
  ringers() {
    return [...this.byRinger.keys()].sort();
  }
}
