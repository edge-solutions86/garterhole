import { ValidationError } from '../errors.js';
import { mean } from '../util/collections.js';

/**
 * Where every blow ought to have fallen.
 *
 * Ringing is not evenly spaced. Rows come in pairs, a handstroke and a
 * backstroke, and there is an extra gap before each handstroke: the handstroke
 * gap, which is what makes ringing sound like ringing rather than like a
 * metronome. The gap is traditionally one blow long, so a whole pull of eight
 * bells takes seventeen blow lengths rather than sixteen.
 *
 * The model is two numbers, the blow interval and the handstroke gap, and every
 * ideal time follows from them.
 */
export class TimingModel {
  /**
   * @param {{ stage: number, interval: number, handstrokeGap?: number, start?: number }} settings
   */
  constructor(settings) {
    if (settings === null || typeof settings !== 'object') {
      throw new ValidationError('a timing model needs its settings');
    }
    const { stage, interval } = settings;
    if (!Number.isInteger(stage) || stage < 1) {
      throw new ValidationError(`a timing model cannot be built for ${stage} bells`);
    }
    if (typeof interval !== 'number' || !(interval > 0)) {
      throw new ValidationError(`a blow interval of ${interval} is not a length of time`);
    }
    const handstrokeGap = settings.handstrokeGap ?? interval;
    if (typeof handstrokeGap !== 'number' || handstrokeGap < 0) {
      throw new ValidationError(`a handstroke gap of ${handstrokeGap} is not a length of time`);
    }
    this.stage = stage;
    this.interval = interval;
    this.handstrokeGap = handstrokeGap;
    this.start = settings.start ?? 0;
    Object.freeze(this);
  }

  /**
   * When a row ought to start.
   *
   * @param {number} rowIndex counted from zero, the first row being a handstroke
   * @returns {number}
   */
  rowStart(rowIndex) {
    if (!Number.isInteger(rowIndex) || rowIndex < 0) {
      throw new ValidationError(`row ${rowIndex} is not a row of a touch`);
    }
    return (
      this.start +
      rowIndex * this.stage * this.interval +
      Math.floor(rowIndex / 2) * this.handstrokeGap
    );
  }

  /**
   * When a blow ought to fall.
   *
   * @param {number} rowIndex
   * @param {number} place counted from one
   * @returns {number}
   */
  expectedAt(rowIndex, place) {
    if (!Number.isInteger(place) || place < 1 || place > this.stage) {
      throw new ValidationError(`place ${place} is outside 1..${this.stage}`);
    }
    return this.rowStart(rowIndex) + (place - 1) * this.interval;
  }

  /**
   * Every ideal time for a run of rows, in striking order.
   *
   * @param {number} rows
   * @returns {number[]}
   */
  idealTimes(rows) {
    if (!Number.isInteger(rows) || rows < 0) {
      throw new ValidationError(`${rows} is not a number of rows`);
    }
    const times = [];
    for (let rowIndex = 0; rowIndex < rows; rowIndex += 1) {
      for (let place = 1; place <= this.stage; place += 1) {
        times.push(this.expectedAt(rowIndex, place));
      }
    }
    return times;
  }

  /**
   * How long a whole pull takes, which is the number a band feels as the speed.
   *
   * @returns {number}
   */
  get wholePull() {
    return 2 * this.stage * this.interval + this.handstrokeGap;
  }

  /**
   * The peal speed this model implies, in hours and minutes for five thousand
   * changes. Bands talk about their speed this way rather than in milliseconds.
   *
   * @param {number} [changes]
   * @returns {number} minutes
   */
  pealMinutes(changes = 5000) {
    const perRow = this.wholePull / 2;
    return (changes * perRow) / 60000;
  }
}

/**
 * Fit a model to rows that were actually struck.
 *
 * The interval comes from the gaps inside rows, which the handstroke gap does
 * not touch. The handstroke gap is then whatever is left over between a
 * backstroke row and the handstroke that follows it.
 *
 * @param {readonly import('./rowSplitter.js').StruckRow[]} rows
 * @param {number} stage
 * @returns {TimingModel}
 */
export function fitModel(rows, stage) {
  if (!Array.isArray(rows) || rows.length < 2) {
    throw new ValidationError('at least two rows are needed to fit a timing model');
  }
  const withinRow = [];
  for (const row of rows) {
    for (let index = 1; index < row.strikes.length; index += 1) {
      withinRow.push(row.strikes[index].at - row.strikes[index - 1].at);
    }
  }
  const interval = mean(withinRow);
  if (interval === null || !(interval > 0)) {
    throw new ValidationError('these rows have no measurable blow interval');
  }
  const gaps = [];
  for (let index = 1; index < rows.length; index += 1) {
    if (index % 2 === 0) {
      gaps.push(rows[index].start - rows[index - 1].end - interval);
    }
  }
  const handstrokeGap = gaps.length === 0 ? interval : Math.max(0, mean(gaps));
  return new TimingModel({
    stage,
    interval,
    handstrokeGap,
    start: rows[0].strikes[0].at,
  });
}

/**
 * A model for a band that wants to ring at a given peal speed.
 *
 * @param {number} stage
 * @param {number} minutes how long five thousand changes should take
 * @returns {TimingModel}
 */
export function modelForPealSpeed(stage, minutes) {
  if (typeof minutes !== 'number' || !(minutes > 0)) {
    throw new ValidationError(`${minutes} minutes is not a peal speed`);
  }
  const perRow = (minutes * 60000) / 5000;
  const interval = (2 * perRow) / (2 * stage + 1);
  return new TimingModel({ stage, interval, handstrokeGap: interval });
}
