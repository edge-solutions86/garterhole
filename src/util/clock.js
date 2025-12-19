import { ValidationError } from '../errors.js';

/**
 * Telling the time.
 *
 * Nothing in this library reads the wall clock directly. Everything that needs
 * the time is handed a clock, and the tests hand it a clock that does not move
 * unless they move it.
 *
 * That is not testing dogma. A striking analysis, a rate limiter and a lock all
 * behave differently at different times of day, and a test that only passes
 * before midnight is a test that will fail at the worst possible moment.
 */

/**
 * The real clock.
 *
 * @returns {{ now: () => number, date: () => Date }}
 */
export function systemClock() {
  return {
    now: () => Date.now(),
    date: () => new Date(),
  };
}

/**
 * A clock that stands still until it is told to move.
 */
export class FixedClock {
  /**
   * @param {number | Date} [start]
   */
  constructor(start = 0) {
    this.moment = start instanceof Date ? start.getTime() : start;
    if (typeof this.moment !== 'number' || !Number.isFinite(this.moment)) {
      throw new ValidationError(`${start} is not a moment to start a clock at`);
    }
  }

  /**
   * @returns {number}
   */
  now() {
    return this.moment;
  }

  /**
   * @returns {Date}
   */
  date() {
    return new Date(this.moment);
  }

  /**
   * Move the clock forward.
   *
   * @param {number} milliseconds
   * @returns {FixedClock} this
   */
  advance(milliseconds) {
    if (typeof milliseconds !== 'number' || !Number.isFinite(milliseconds) || milliseconds < 0) {
      throw new ValidationError(`a clock cannot move forward by ${milliseconds}`);
    }
    this.moment += milliseconds;
    return this;
  }

  /**
   * Put the clock at a particular moment.
   *
   * @param {number | Date} moment
   * @returns {FixedClock} this
   */
  set(moment) {
    this.moment = moment instanceof Date ? moment.getTime() : moment;
    return this;
  }
}

/** A second, in milliseconds. */
export const SECOND = 1000;
/** A minute, in milliseconds. */
export const MINUTE = 60 * SECOND;
/** An hour, in milliseconds. */
export const HOUR = 60 * MINUTE;
/** A day, in milliseconds. */
export const DAY = 24 * HOUR;

/**
 * A length of time written the way a person would say it.
 *
 * @param {number} milliseconds
 * @returns {string}
 */
export function describeDuration(milliseconds) {
  if (typeof milliseconds !== 'number' || !Number.isFinite(milliseconds) || milliseconds < 0) {
    throw new ValidationError(`${milliseconds} is not a length of time`);
  }
  if (milliseconds < SECOND) {
    return `${Math.round(milliseconds)}ms`;
  }
  if (milliseconds < MINUTE) {
    return `${(milliseconds / SECOND).toFixed(1)}s`;
  }
  const minutes = Math.floor(milliseconds / MINUTE);
  if (minutes < 60) {
    return `${minutes}m`;
  }
  const hours = Math.floor(minutes / 60);
  return `${hours}h ${minutes - hours * 60}m`;
}

/**
 * The start of the day a moment falls in, which is what a diary groups by.
 *
 * @param {Date} moment
 * @returns {Date}
 */
export function startOfDay(moment) {
  if (!(moment instanceof Date) || Number.isNaN(moment.getTime())) {
    throw new ValidationError('a date is needed to find the start of its day');
  }
  return new Date(
    Date.UTC(moment.getUTCFullYear(), moment.getUTCMonth(), moment.getUTCDate(), 0, 0, 0, 0),
  );
}
