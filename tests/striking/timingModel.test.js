import { describe, it, expect } from 'vitest';
import { TimingModel, fitModel, modelForPealSpeed } from '../../src/striking/timingModel.js';
import { splitRows } from '../../src/striking/rowSplitter.js';
import { StrikeStream } from '../../src/striking/stream.js';
import { plainCourse } from '../../src/method/plainCourse.js';
import { method } from '../../src/method/method.js';
import { ValidationError } from '../../src/errors.js';

const plainBobMinor = () => method('Plain', 6, 'x1x1x1,2');

/**
 * Strike a block exactly as a model says it should be struck.
 *
 * @param {import('../../src/core/rowBlock.js').RowBlock} block
 * @param {TimingModel} model
 */
function strikeTo(block, model) {
  const strikes = [];
  block.rows.forEach((row, rowIndex) => {
    row.bells.forEach((bell, place) => {
      strikes.push({ bell, at: model.expectedAt(rowIndex, place + 1) });
    });
  });
  return new StrikeStream(block.stage, strikes);
}

describe('TimingModel', () => {
  const model = () => new TimingModel({ stage: 6, interval: 250 });

  it('defaults the handstroke gap to one blow', () => {
    expect(model().handstrokeGap).toBe(250);
  });

  it('starts the first row at the start time', () => {
    expect(model().rowStart(0)).toBe(0);
    expect(new TimingModel({ stage: 6, interval: 250, start: 1000 }).rowStart(0)).toBe(1000);
  });

  it('puts no gap between a handstroke row and its backstroke', () => {
    expect(model().rowStart(1)).toBe(1500);
  });

  it('puts the handstroke gap before the next handstroke', () => {
    expect(model().rowStart(2)).toBe(3250);
    expect(model().rowStart(3)).toBe(4750);
  });

  it('spaces the blows within a row evenly', () => {
    expect(model().expectedAt(0, 1)).toBe(0);
    expect(model().expectedAt(0, 6)).toBe(1250);
  });

  it('measures a whole pull', () => {
    expect(model().wholePull).toBe(3250);
  });

  it('gives a peal speed in minutes', () => {
    expect(Math.round(model().pealMinutes())).toBe(135);
  });

  it('lists the ideal times for a run of rows', () => {
    expect(model().idealTimes(2)).toHaveLength(12);
    expect(model().idealTimes(0)).toEqual([]);
  });

  it('refuses settings that make no sense', () => {
    expect(() => new TimingModel(null)).toThrow(/needs its settings/);
    expect(() => new TimingModel({ stage: 0, interval: 250 })).toThrow(/cannot be built for 0/);
    expect(() => new TimingModel({ stage: 6, interval: 0 })).toThrow(/not a length of time/);
    expect(() => new TimingModel({ stage: 6, interval: 250, handstrokeGap: -1 })).toThrow(
      ValidationError,
    );
  });

  it('refuses a row or place outside the model', () => {
    expect(() => model().rowStart(-1)).toThrow(/is not a row of a touch/);
    expect(() => model().expectedAt(0, 7)).toThrow(/outside 1\.\.6/);
    expect(() => model().idealTimes(-1)).toThrow(/is not a number of rows/);
  });
});

describe('fitModel', () => {
  it('recovers the model a stream was struck to', () => {
    const original = new TimingModel({ stage: 6, interval: 250, handstrokeGap: 250 });
    const rows = splitRows(strikeTo(plainCourse(plainBobMinor()), original));
    const fitted = fitModel(rows, 6);
    expect(fitted.interval).toBeCloseTo(250, 5);
    expect(fitted.handstrokeGap).toBeCloseTo(250, 5);
  });

  it('recovers a different handstroke gap', () => {
    const original = new TimingModel({ stage: 6, interval: 200, handstrokeGap: 400 });
    const rows = splitRows(strikeTo(plainCourse(plainBobMinor()), original));
    const fitted = fitModel(rows, 6);
    expect(fitted.interval).toBeCloseTo(200, 5);
    expect(fitted.handstrokeGap).toBeCloseTo(400, 5);
  });

  it('takes the start time from the first blow', () => {
    const original = new TimingModel({ stage: 6, interval: 250, start: 5000 });
    const rows = splitRows(strikeTo(plainBobMinor().plainLead(), original));
    expect(fitModel(rows, 6).start).toBe(5000);
  });

  it('refuses too few rows to fit anything', () => {
    expect(() => fitModel([], 6)).toThrow(/at least two rows/);
  });

  it('refuses rows with no time between the blows', () => {
    const flat = [
      { strikes: [{ at: 0 }, { at: 0 }], start: 0, end: 0 },
      { strikes: [{ at: 0 }, { at: 0 }], start: 0, end: 0 },
    ];
    expect(() => fitModel(flat, 2)).toThrow(/no measurable blow interval/);
  });
});

describe('modelForPealSpeed', () => {
  it('builds a model that rings a peal in the time asked for', () => {
    const model = modelForPealSpeed(8, 180);
    expect(model.pealMinutes()).toBeCloseTo(180, 6);
  });

  it('gets slower as the peal time goes up', () => {
    expect(modelForPealSpeed(8, 180).interval).toBeGreaterThan(modelForPealSpeed(8, 160).interval);
  });

  it('refuses a speed that is not a time', () => {
    expect(() => modelForPealSpeed(8, 0)).toThrow(/is not a peal speed/);
  });
});
