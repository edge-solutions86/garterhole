import { describe, it, expect } from 'vitest';
import { rollingAccuracy, trend, worstWindow, describeTrend } from '../../src/striking/window.js';
import { ValidationError } from '../../src/errors.js';

/**
 * Errors for a run of rows, six blows to a row.
 *
 * @param {(rowIndex: number) => number} sizeOf
 * @param {number} rows
 */
function errors(sizeOf, rows = 60) {
  const made = [];
  for (let rowIndex = 0; rowIndex < rows; rowIndex += 1) {
    for (let place = 1; place <= 6; place += 1) {
      made.push({ bell: place, rowIndex, place, at: 0, expected: 0, error: sizeOf(rowIndex) });
    }
  }
  return made;
}

describe('rollingAccuracy', () => {
  it('gives one window per stretch of rows', () => {
    const windows = rollingAccuracy(
      errors(() => 0),
      { rows: 20 },
    );
    expect(windows).toHaveLength(3);
    expect(windows[0]).toMatchObject({ from: 0, to: 19, blows: 120 });
  });

  it('measures each window on its own', () => {
    const windows = rollingAccuracy(
      errors((rowIndex) => (rowIndex < 20 ? 0 : 60)),
      { rows: 20 },
    );
    expect(windows[0].rms).toBe(0);
    expect(windows[1].rms).toBe(60);
  });

  it('slides by the step it is given', () => {
    const windows = rollingAccuracy(
      errors(() => 0, 30),
      { rows: 20, step: 10 },
    );
    expect(windows.map((window) => window.from)).toEqual([0, 10, 20]);
  });

  it('leaves out a window with nothing in it', () => {
    const windows = rollingAccuracy(
      errors(() => 0, 5),
      { rows: 20 },
    );
    expect(windows).toHaveLength(1);
  });

  it('gives nothing for no errors', () => {
    expect(rollingAccuracy([])).toEqual([]);
  });

  it('refuses a window or a step that measures nothing', () => {
    expect(() => rollingAccuracy([], { rows: 0 })).toThrow(/measures nothing/);
    expect(() => rollingAccuracy([], { step: 0 })).toThrow(ValidationError);
  });
});

describe('trend', () => {
  it('says the striking got worse', () => {
    const windows = rollingAccuracy(
      errors((rowIndex) => (rowIndex < 20 ? 0 : 80)),
      { rows: 20 },
    );
    expect(trend(windows).direction).toBe('worse');
  });

  it('says the striking got better', () => {
    const windows = rollingAccuracy(
      errors((rowIndex) => (rowIndex < 20 ? 80 : 0)),
      { rows: 20 },
    );
    expect(trend(windows).direction).toBe('better');
  });

  it('calls a small change level', () => {
    const windows = rollingAccuracy(
      errors((rowIndex) => (rowIndex < 20 ? 20 : 22)),
      { rows: 20 },
    );
    expect(trend(windows).direction).toBe('level');
  });

  it('gives null when there is only one window', () => {
    expect(trend([{ rms: 10 }])).toBeNull();
    expect(trend([])).toBeNull();
  });
});

describe('worstWindow', () => {
  it('finds the worst stretch', () => {
    const windows = rollingAccuracy(
      errors((rowIndex) => (rowIndex >= 20 && rowIndex < 40 ? 90 : 0)),
      { rows: 20 },
    );
    expect(worstWindow(windows).from).toBe(20);
  });

  it('gives null when there are no windows', () => {
    expect(worstWindow([])).toBeNull();
  });
});

describe('describeTrend', () => {
  it('says how the piece went', () => {
    const windows = rollingAccuracy(
      errors((rowIndex) => (rowIndex < 20 ? 0 : 80)),
      { rows: 20 },
    );
    const line = describeTrend(windows);
    expect(line).toContain('the striking got worse');
    expect(line).toContain('worst between rows');
  });

  it('says so when there is nothing to measure', () => {
    expect(describeTrend([])).toBe('nothing to measure');
  });

  it('mentions when there is only one window', () => {
    const windows = rollingAccuracy(
      errors(() => 30, 10),
      { rows: 20 },
    );
    expect(describeTrend(windows)).toContain('one window only');
  });
});
