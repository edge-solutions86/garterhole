import { describe, it, expect } from 'vitest';
import {
  verdict,
  headline,
  bellTable,
  tendency,
  faultLines,
  reportLines,
} from '../../src/striking/report.js';
import { analyseStriking } from '../../src/striking/session.js';
import { TimingModel } from '../../src/striking/timingModel.js';
import { plainCourse } from '../../src/method/plainCourse.js';
import { method } from '../../src/method/method.js';
import { ValidationError } from '../../src/errors.js';

const plainBobMinor = () => method('Plain', 6, 'x1x1x1,2');

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

const clean = () => analyseStriking(6, courseStrikes());
const ragged = () =>
  analyseStriking(
    6,
    courseStrikes((bell) => (bell === 4 ? 120 : 0)),
  );

describe('verdict', () => {
  it('grades the ringing', () => {
    expect(verdict(5)).toBe('excellent');
    expect(verdict(30)).toBe('good');
    expect(verdict(55)).toBe('fair');
    expect(verdict(120)).toBe('ragged');
  });

  it('refuses something that is not an accuracy', () => {
    expect(() => verdict(-1)).toThrow(ValidationError);
    expect(() => verdict('20')).toThrow(/is not a striking accuracy/);
  });
});

describe('headline', () => {
  it('opens with how much was rung and how well', () => {
    expect(headline(clean())).toBe('61 rows rung, excellent striking at 0ms');
  });

  it('grades poor ringing lower', () => {
    expect(headline(ragged())).not.toContain('excellent');
  });

  it('refuses something that is not a session', () => {
    expect(() => headline({})).toThrow(/a StrikingSession is needed/);
  });
});

describe('tendency', () => {
  it('calls a small mean error even', () => {
    expect(tendency(0)).toBe('even');
    expect(tendency(-8)).toBe('even');
  });

  it('names the direction of a larger one', () => {
    expect(tendency(-45)).toBe('45ms early');
    expect(tendency(45)).toBe('45ms late');
  });
});

describe('bellTable', () => {
  it('has a heading and a line per bell', () => {
    const table = bellTable(clean());
    expect(table).toHaveLength(7);
    expect(table[0].startsWith('bell')).toBe(true);
  });

  it('names every bell of the stage', () => {
    const table = bellTable(clean());
    expect(table[1].startsWith('1')).toBe(true);
    expect(table[6].startsWith('6')).toBe(true);
  });
});

describe('faultLines', () => {
  it('says so when there is nothing to report', () => {
    expect(faultLines(clean())).toEqual(['no faults worth reporting']);
  });

  it('describes the faults it found', () => {
    expect(faultLines(ragged())[0]).toContain('bell 4');
  });

  it('stops at the limit and says how many were left', () => {
    const lines = faultLines(ragged(), 3);
    expect(lines).toHaveLength(4);
    expect(lines[3].startsWith('and ')).toBe(true);
  });

  it('refuses something that is not a session', () => {
    expect(() => faultLines(null)).toThrow(ValidationError);
  });
});

describe('reportLines', () => {
  it('puts the headline first and the table after', () => {
    const lines = reportLines(clean());
    expect(lines[0]).toContain('rows rung');
    expect(lines[1]).toBe('');
    expect(lines[2].startsWith('bell')).toBe(true);
  });

  it('mentions a recording that stopped making sense', () => {
    const strikes = courseStrikes().slice(0, 12);
    const broken = [
      { bell: 1, at: 90000 },
      { bell: 1, at: 90250 },
      { bell: 3, at: 90500 },
      { bell: 4, at: 90750 },
      { bell: 5, at: 91000 },
      { bell: 6, at: 91250 },
    ];
    const session = analyseStriking(6, [...strikes, ...broken], { lenient: true });
    expect(reportLines(session)[1]).toBe('the recording stopped making sense at row 3');
  });
});
