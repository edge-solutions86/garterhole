import { describe, it, expect } from 'vitest';
import { StrikingSession, analyseStriking } from '../../src/striking/session.js';
import { TimingModel } from '../../src/striking/timingModel.js';
import { StrikeStream } from '../../src/striking/stream.js';
import { plainCourse } from '../../src/method/plainCourse.js';
import { method } from '../../src/method/method.js';
import { ValidationError } from '../../src/errors.js';

const plainBobMinor = () => method('Plain', 6, 'x1x1x1,2');

/**
 * Strike a plain course, optionally moving one bell.
 *
 * @param {(bell: number, rowIndex: number) => number} [shift]
 */
function courseStrikes(shift = () => 0) {
  const block = plainCourse(plainBobMinor());
  const model = new TimingModel({ stage: 6, interval: 250 });
  const strikes = [];
  block.rows.forEach((row, rowIndex) => {
    row.bells.forEach((bell, place) => {
      strikes.push({ bell, at: model.expectedAt(rowIndex, place + 1) + shift(bell, rowIndex) });
    });
  });
  return strikes;
}

describe('StrikingSession', () => {
  it('splits the stream and fits a model', () => {
    const session = analyseStriking(6, courseStrikes());
    expect(session.rowCount).toBe(61);
    expect(session.model.interval).toBeCloseTo(250, 5);
  });

  it('is frozen', () => {
    expect(Object.isFrozen(analyseStriking(6, courseStrikes()))).toBe(true);
  });

  it('finds perfect ringing perfect', () => {
    const session = analyseStriking(6, courseStrikes());
    expect(session.accuracy().rms).toBeCloseTo(0, 6);
    expect(session.faults()).toEqual([]);
  });

  it('blames the bell that struck badly', () => {
    const session = analyseStriking(
      6,
      courseStrikes((bell) => (bell === 4 ? 90 : 0)),
    );
    expect(session.byBell().get(4).rms).toBeGreaterThan(session.byBell().get(2).rms);
    expect(session.faults().some((fault) => fault.bell === 4)).toBe(true);
  });

  it('counts the faults by kind', () => {
    const session = analyseStriking(
      6,
      courseStrikes((bell) => (bell === 4 ? 90 : 0)),
    );
    expect(session.faultCounts().gap).toBeGreaterThan(0);
  });

  it('lists the rows worth listening to again', () => {
    const session = analyseStriking(
      6,
      courseStrikes((bell, rowIndex) => (rowIndex === 10 ? 200 : 0)),
    );
    expect(session.worstRows(50)).toContain(10);
  });

  it('says which rows were handstrokes', () => {
    expect(analyseStriking(6, courseStrikes()).strokes().slice(0, 3)).toEqual([true, false, true]);
  });

  it('gives back the rows that were rung', () => {
    const session = analyseStriking(6, courseStrikes());
    expect(session.rungRows()[0].toString()).toBe('123456');
    expect(session.rungRows()).toHaveLength(61);
  });

  it('takes a model of its own instead of fitting one', () => {
    const model = new TimingModel({ stage: 6, interval: 300 });
    const session = analyseStriking(6, courseStrikes(), { model });
    expect(session.model).toBe(model);
    expect(session.accuracy().rms).toBeGreaterThan(0);
  });

  it('refuses options it cannot use', () => {
    expect(() => new StrikingSession(null)).toThrow(/needs its options/);
    expect(() => new StrikingSession({ stream: [] })).toThrow(/needs a StrikeStream/);
  });

  it('refuses a recording too short to say anything about', () => {
    const strikes = courseStrikes().slice(0, 6);
    expect(() => analyseStriking(6, strikes)).toThrow(/at least two rows/);
  });
});

describe('lenient sessions', () => {
  it('stops at the first row that is not a permutation', () => {
    const good = courseStrikes().slice(0, 12);
    const bad = [
      { bell: 1, at: 20000 },
      { bell: 1, at: 20250 },
      { bell: 3, at: 20500 },
      { bell: 4, at: 20750 },
      { bell: 5, at: 21000 },
      { bell: 6, at: 21250 },
    ];
    const session = new StrikingSession({
      stream: new StrikeStream(6, [...good, ...bad]),
      lenient: true,
    });
    expect(session.rowCount).toBe(2);
    expect(session.stoppedAt).toBe(2);
    expect(session.stoppedReason).toMatch(/not a permutation/);
  });

  it('reports nothing stopped it when the stream is clean', () => {
    const session = analyseStriking(6, courseStrikes(), { lenient: true });
    expect(session.stoppedAt).toBeNull();
    expect(session.stoppedReason).toBeNull();
  });

  it('still refuses a bad row when it is not lenient', () => {
    const strikes = [
      { bell: 1, at: 0 },
      { bell: 1, at: 250 },
      { bell: 3, at: 500 },
      { bell: 4, at: 750 },
      { bell: 5, at: 1000 },
      { bell: 6, at: 1250 },
    ];
    expect(() => analyseStriking(6, [...strikes, ...strikes])).toThrow(ValidationError);
  });
});
