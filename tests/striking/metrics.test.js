import { describe, it, expect } from 'vitest';
import {
  blowErrors,
  rootMeanSquare,
  accuracy,
  accuracyByBell,
  accuracyByRow,
  worstBell,
  badPatches,
} from '../../src/striking/metrics.js';
import { TimingModel } from '../../src/striking/timingModel.js';
import { splitRows } from '../../src/striking/rowSplitter.js';
import { StrikeStream } from '../../src/striking/stream.js';
import { method } from '../../src/method/method.js';
import { ValidationError } from '../../src/errors.js';

const plainBobMinor = () => method('Plain', 6, 'x1x1x1,2');
const model = () => new TimingModel({ stage: 6, interval: 250 });

/**
 * Strike a lead to the model, then move one bell late in one row.
 *
 * @param {(bell: number, rowIndex: number) => number} shift
 */
function struckLead(shift = () => 0) {
  const lead = plainBobMinor().plainLead();
  const timing = model();
  const strikes = [];
  lead.rows.forEach((row, rowIndex) => {
    row.bells.forEach((bell, place) => {
      strikes.push({
        bell,
        at: timing.expectedAt(rowIndex, place + 1) + shift(bell, rowIndex),
      });
    });
  });
  return splitRows(
    new StrikeStream(
      6,
      strikes.sort((left, right) => left.at - right.at),
    ),
  );
}

describe('blowErrors', () => {
  it('finds no error in perfect striking', () => {
    const errors = blowErrors(struckLead(), model());
    expect(errors).toHaveLength(78);
    expect(errors.every((entry) => entry.error === 0)).toBe(true);
  });

  it('records the bell, row and place of every blow', () => {
    const errors = blowErrors(struckLead(), model());
    expect(errors[0]).toEqual({
      bell: 1,
      rowIndex: 0,
      place: 1,
      at: 0,
      expected: 0,
      error: 0,
    });
  });

  it('signs an early blow negative and a late one positive', () => {
    const late = blowErrors(
      struckLead((bell) => (bell === 6 ? 40 : 0)),
      model(),
    );
    const early = blowErrors(
      struckLead((bell) => (bell === 6 ? -40 : 0)),
      model(),
    );
    expect(late.some((entry) => entry.error > 0)).toBe(true);
    expect(early.some((entry) => entry.error < 0)).toBe(true);
  });

  it('refuses rows or a model it cannot use', () => {
    expect(() => blowErrors(null, model())).toThrow(/list of struck rows/);
    expect(() => blowErrors(struckLead(), {})).toThrow(/a TimingModel is needed/);
  });
});

describe('rootMeanSquare', () => {
  it('is zero for perfect striking', () => {
    expect(rootMeanSquare([0, 0, 0])).toBe(0);
  });

  it('is zero for nothing', () => {
    expect(rootMeanSquare([])).toBe(0);
  });

  it('does not let early and late cancel out', () => {
    expect(rootMeanSquare([-30, 30])).toBe(30);
  });

  it('grows with the size of the errors', () => {
    expect(rootMeanSquare([10, 10])).toBeLessThan(rootMeanSquare([20, 20]));
  });
});

describe('accuracy', () => {
  it('summarises perfect striking', () => {
    const summary = accuracy(blowErrors(struckLead(), model()));
    expect(summary.blows).toBe(78);
    expect(summary.rms).toBe(0);
    expect(summary.meanError).toBe(0);
  });

  it('finds the worst blow', () => {
    const errors = blowErrors(
      struckLead((bell, rowIndex) => (bell === 4 && rowIndex === 3 ? 90 : 0)),
      model(),
    );
    expect(accuracy(errors).worst.bell).toBe(4);
    expect(accuracy(errors).worst.rowIndex).toBe(3);
  });

  it('has no worst blow when nothing was rung', () => {
    expect(accuracy([]).worst).toBeNull();
    expect(accuracy([]).rms).toBe(0);
  });
});

describe('accuracyByBell', () => {
  it('gives every bell a line', () => {
    const summary = accuracyByBell(blowErrors(struckLead(), model()));
    expect([...summary.keys()]).toEqual([1, 2, 3, 4, 5, 6]);
    expect(summary.get(1).blows).toBe(13);
  });

  it('blames only the bell that struck badly', () => {
    const summary = accuracyByBell(
      blowErrors(
        struckLead((bell) => (bell === 5 ? 60 : 0)),
        model(),
      ),
    );
    expect(summary.get(5).rms).toBeGreaterThan(0);
    expect(summary.get(2).rms).toBe(0);
  });
});

describe('accuracyByRow', () => {
  it('gives every row a line in order', () => {
    const byRow = accuracyByRow(blowErrors(struckLead(), model()));
    expect(byRow).toHaveLength(13);
    expect(byRow.map((row) => row.rowIndex)).toEqual([...Array(13).keys()]);
  });

  it('finds the row that went wrong', () => {
    const errors = blowErrors(
      struckLead((bell, rowIndex) => (rowIndex === 4 ? 80 : 0)),
      model(),
    );
    const byRow = accuracyByRow(errors);
    expect(byRow[4].rms).toBeGreaterThan(0);
    expect(byRow[3].rms).toBe(0);
  });
});

describe('worstBell', () => {
  it('names the bell with the worst striking', () => {
    const errors = blowErrors(
      struckLead((bell) => (bell === 3 ? 70 : 0)),
      model(),
    );
    expect(worstBell(errors)).toBe(3);
  });

  it('gives null when nothing was rung', () => {
    expect(worstBell([])).toBeNull();
  });
});

describe('badPatches', () => {
  it('lists the rows above the threshold', () => {
    const errors = blowErrors(
      struckLead((bell, rowIndex) => (rowIndex === 7 ? 100 : 0)),
      model(),
    );
    expect(badPatches(errors, 50)).toEqual([7]);
  });

  it('lists nothing when the ringing is clean', () => {
    expect(badPatches(blowErrors(struckLead(), model()), 50)).toEqual([]);
  });

  it('refuses a threshold that is not a time', () => {
    expect(() => badPatches([], -1)).toThrow(ValidationError);
  });
});
