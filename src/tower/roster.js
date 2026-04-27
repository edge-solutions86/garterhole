import { startOfDay } from '../util/clock.js';
import { sortBy } from '../util/collections.js';
import { assertNonEmptyString } from '../util/assert.js';
import { ValidationError } from '../errors.js';

/**
 * Who came to practice.
 *
 * A tower captain keeps this in a notebook and uses it for two things: knowing
 * who has stopped coming, so that somebody can ring them up, and knowing who
 * has been often enough to be asked to a quarter peal.
 *
 * Attendance is recorded per day rather than per practice, because a ringer who
 * comes to the morning service and the evening practice on the same Sunday has
 * come once as far as any of these questions are concerned.
 */
export class Roster {
  constructor() {
    /** @type {Map<string, Set<string>>} */
    this.byRinger = new Map();
  }

  /**
   * Record that somebody came.
   *
   * @param {string} ringerId
   * @param {Date} when
   * @returns {Roster} this
   */
  attended(ringerId, when) {
    assertNonEmptyString(ringerId, 'a ringer id');
    if (!(when instanceof Date) || Number.isNaN(when.getTime())) {
      throw new ValidationError('attendance needs the date it happened');
    }
    const day = startOfDay(when).toISOString().slice(0, 10);
    let days = this.byRinger.get(ringerId);
    if (days === undefined) {
      days = new Set();
      this.byRinger.set(ringerId, days);
    }
    days.add(day);
    return this;
  }

  /**
   * How many days somebody has been.
   *
   * @param {string} ringerId
   * @returns {number}
   */
  countFor(ringerId) {
    return this.byRinger.get(ringerId)?.size ?? 0;
  }

  /**
   * The days somebody came, earliest first.
   *
   * @param {string} ringerId
   * @returns {string[]}
   */
  daysFor(ringerId) {
    return [...(this.byRinger.get(ringerId) ?? [])].sort();
  }

  /**
   * The last day somebody came, or null.
   *
   * @param {string} ringerId
   * @returns {string | null}
   */
  lastSeen(ringerId) {
    const days = this.daysFor(ringerId);
    return days.length === 0 ? null : days[days.length - 1];
  }

  /**
   * Who came on a day.
   *
   * @param {Date} when
   * @returns {string[]}
   */
  whoCame(when) {
    const day = startOfDay(when).toISOString().slice(0, 10);
    const came = [];
    for (const [ringerId, days] of this.byRinger) {
      if (days.has(day)) {
        came.push(ringerId);
      }
    }
    return came.sort();
  }

  /**
   * Everybody, most regular first.
   *
   * @returns {{ ringerId: string, attended: number, lastSeen: string }[]}
   */
  attendance() {
    const lines = [...this.byRinger.keys()].map((ringerId) => ({
      ringerId,
      attended: this.countFor(ringerId),
      lastSeen: this.lastSeen(ringerId),
    }));
    return sortBy(
      lines,
      (line) => -line.attended,
      (line) => line.ringerId,
    );
  }

  /**
   * Who has not been since a date, which is the list somebody rings round.
   *
   * @param {Date} since
   * @returns {{ ringerId: string, lastSeen: string }[]}
   */
  missingSince(since) {
    if (!(since instanceof Date) || Number.isNaN(since.getTime())) {
      throw new ValidationError('a date is needed to ask who is missing');
    }
    const day = startOfDay(since).toISOString().slice(0, 10);
    return this.attendance()
      .filter((line) => line.lastSeen !== null && line.lastSeen < day)
      .map((line) => ({ ringerId: line.ringerId, lastSeen: line.lastSeen }));
  }

  /**
   * Every day anybody came, earliest first.
   *
   * @returns {string[]}
   */
  days() {
    const days = new Set();
    for (const theirs of this.byRinger.values()) {
      for (const day of theirs) {
        days.add(day);
      }
    }
    return [...days].sort();
  }
}
