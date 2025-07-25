import { describe, it, expect } from 'vitest';
import {
  CLIP,
  GAP,
  DRIFT,
  DEFAULT_THRESHOLDS,
  findFaults,
  countFaults,
  faultsByBell,
  describeFault,
} from '../../src/striking/faults.js';
import { ValidationError } from '../../src/errors.js';

/**
 * @param {number} bell
 * @param {number} rowIndex
 * @param {number} error
 */
const blow = (bell, rowIndex, error) => ({
  bell,
  rowIndex,
  place: 1,
  at: 0,
  expected: 0,
  error,
});

describe('findFaults', () => {
  it('finds nothing in clean ringing', () => {
    expect(findFaults([blow(1, 0, 0), blow(2, 0, 10)])).toEqual([]);
  });

  it('names a blow struck too soon a clip', () => {
    const faults = findFaults([blow(3, 2, -80)]);
    expect(faults).toHaveLength(1);
    expect(faults[0].kind).toBe(CLIP);
    expect(faults[0].bell).toBe(3);
    expect(faults[0].size).toBe(-80);
  });

  it('names a blow left too late a gap', () => {
    expect(findFaults([blow(3, 2, 80)])[0].kind).toBe(GAP);
  });

  it('leaves a blow inside the threshold alone', () => {
    expect(findFaults([blow(3, 2, 40)])).toEqual([]);
    expect(findFaults([blow(3, 2, -40)])).toEqual([]);
  });

  it('takes thresholds of its own', () => {
    expect(findFaults([blow(3, 2, 40)], { gap: 30 })).toHaveLength(1);
  });

  it('sorts the faults into ringing order', () => {
    const faults = findFaults([blow(2, 5, 90), blow(1, 1, 90)]);
    expect(faults.map((fault) => fault.rowIndex)).toEqual([1, 5]);
  });

  it('refuses something that is not a list of errors', () => {
    expect(() => findFaults(null)).toThrow(/list of blow errors/);
  });

  it('refuses a threshold that is not a positive number', () => {
    expect(() => findFaults([], { gap: 0 })).toThrow(/must be a positive number/);
    expect(() => findFaults([], { clip: -1 })).toThrow(ValidationError);
  });
});

describe('drift', () => {
  it('reports a run of blows on the same side', () => {
    const errors = [0, 1, 2, 3].map((row) => blow(4, row, 40));
    const faults = findFaults(errors);
    expect(faults.some((fault) => fault.kind === DRIFT)).toBe(true);
  });

  it('needs the run to be long enough', () => {
    const errors = [0, 1, 2].map((row) => blow(4, row, 40));
    expect(findFaults(errors).some((fault) => fault.kind === DRIFT)).toBe(false);
  });

  it('breaks a run when the bell comes back', () => {
    const errors = [blow(4, 0, 40), blow(4, 1, 40), blow(4, 2, 0), blow(4, 3, 40), blow(4, 4, 40)];
    expect(findFaults(errors).some((fault) => fault.kind === DRIFT)).toBe(false);
  });

  it('breaks a run when the bell changes side', () => {
    const errors = [blow(4, 0, 40), blow(4, 1, 40), blow(4, 2, -40), blow(4, 3, -40)];
    expect(findFaults(errors).some((fault) => fault.kind === DRIFT)).toBe(false);
  });

  it('keeps each bell separate', () => {
    const errors = [0, 1, 2, 3].flatMap((row) => [blow(4, row, 40), blow(5, row, 0)]);
    const drifts = findFaults(errors).filter((fault) => fault.kind === DRIFT);
    expect(drifts).toHaveLength(1);
    expect(drifts[0].bell).toBe(4);
  });

  it('measures a drift in blows rather than milliseconds', () => {
    const errors = [0, 1, 2, 3, 4].map((row) => blow(4, row, 40));
    const drift = findFaults(errors).find((fault) => fault.kind === DRIFT);
    expect(drift.size).toBe(5);
  });
});

describe('counting and grouping', () => {
  const faults = () => findFaults([blow(2, 0, 90), blow(2, 1, -90), blow(3, 2, 90)]);

  it('counts each kind', () => {
    expect(countFaults(faults())).toEqual({ [CLIP]: 1, [GAP]: 2, [DRIFT]: 0 });
  });

  it('counts nothing for no faults', () => {
    expect(countFaults([])).toEqual({ [CLIP]: 0, [GAP]: 0, [DRIFT]: 0 });
  });

  it('groups by bell in order', () => {
    const byBell = faultsByBell(faults());
    expect([...byBell.keys()]).toEqual([2, 3]);
    expect(byBell.get(2)).toHaveLength(2);
  });
});

describe('describeFault', () => {
  it('describes a clip', () => {
    expect(describeFault({ kind: CLIP, bell: 3, rowIndex: 4, place: 1, size: -82 })).toBe(
      'bell 3 clipped by 82ms in row 5',
    );
  });

  it('describes a gap', () => {
    expect(describeFault({ kind: GAP, bell: 3, rowIndex: 4, place: 1, size: 82 })).toBe(
      'bell 3 left a 82ms gap in row 5',
    );
  });

  it('describes a drift in blows', () => {
    expect(describeFault({ kind: DRIFT, bell: 3, rowIndex: 4, place: 1, size: 6 })).toBe(
      'bell 3 drifted for 6 blows from row 5',
    );
  });
});

describe('DEFAULT_THRESHOLDS', () => {
  it('is frozen so nobody edits it by accident', () => {
    expect(Object.isFrozen(DEFAULT_THRESHOLDS)).toBe(true);
  });
});
