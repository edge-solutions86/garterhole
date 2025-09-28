import { describe, it, expect } from 'vitest';
import {
  acceptableBandSizes,
  validatePerformance,
  speedWarnings,
  validateAll,
  describeVerdict,
} from '../../src/records/validate.js';
import { performance } from '../../src/records/performance.js';
import { ValidationError } from '../../src/errors.js';

const TODAY = new Date(Date.UTC(2025, 8, 1));
const six = ['Alice', 'Bob', 'Carol', 'Dan', 'Erin', 'Frank'];

function fields(overrides = {}) {
  return {
    tower: 'St Mary',
    date: new Date(Date.UTC(2025, 4, 18)),
    title: 'Plain Bob Minor',
    changes: 1260,
    band: six,
    conductor: 'Alice',
    ...overrides,
  };
}

const check = (overrides, options = {}) =>
  validatePerformance(performance(fields(overrides)), { today: TODAY, ...options });

describe('acceptableBandSizes', () => {
  it('needs exactly the stage on an even stage', () => {
    expect(acceptableBandSizes(8)).toEqual([8]);
  });

  it('allows a cover on an odd stage', () => {
    expect(acceptableBandSizes(5)).toEqual([5, 6]);
  });
});

describe('validatePerformance', () => {
  it('passes a sound record', () => {
    const verdict = check({});
    expect(verdict.ok).toBe(true);
    expect(verdict.problems).toEqual([]);
    expect(verdict.warnings).toEqual([]);
  });

  it('catches a band of the wrong size', () => {
    const verdict = check({ band: six.slice(0, 5) });
    expect(verdict.ok).toBe(false);
    expect(verdict.problems[0]).toBe('Plain Bob Minor needs a band of 6 and this one has 5');
  });

  it('allows a cover on doubles', () => {
    expect(check({ title: 'Plain Bob Doubles', band: six, changes: 1260 }).ok).toBe(true);
    expect(
      check({ title: 'Plain Bob Doubles', band: six.slice(0, 5), conductor: 'Alice' }).ok,
    ).toBe(true);
  });

  it('catches somebody down for two bells', () => {
    const verdict = check({ band: ['Alice', 'Alice', 'Carol', 'Dan', 'Erin', 'Frank'] });
    expect(verdict.problems).toContain('somebody is down for more than one bell');
  });

  it('warns when no conductor is recorded', () => {
    const verdict = check({ conductor: undefined });
    expect(verdict.ok).toBe(true);
    expect(verdict.warnings).toContain('no conductor is recorded');
  });

  it('catches a date in the future', () => {
    const verdict = check({ date: new Date(Date.UTC(2026, 0, 1)) });
    expect(verdict.problems[0]).toBe('the date 2026-01-01 is in the future');
  });

  it('checks a claimed length when one is given', () => {
    const verdict = check({ changes: 5000 }, { claim: 'peal' });
    expect(verdict.problems[0]).toBe('a peal on 6 needs at least 5040 changes, not 5000');
  });

  it('catches a peal length that is not whole extents', () => {
    const verdict = check({ changes: 5760 }, { claim: 'peal' });
    expect(verdict.ok).toBe(true);
    expect(check({ changes: 5100 }, { claim: 'peal' }).problems[0]).toMatch(/whole extents of 720/);
  });

  it('passes a peal of the right length', () => {
    expect(check({ changes: 5040 }, { claim: 'peal' }).ok).toBe(true);
  });

  it('gives up early on a title it cannot read', () => {
    const verdict = check({ title: 'Nonsense' });
    expect(verdict.ok).toBe(false);
    expect(verdict.problems).toHaveLength(1);
  });

  it('refuses something that is not a performance', () => {
    expect(() => validatePerformance({ title: 'Plain Bob Minor' })).toThrow(ValidationError);
  });
});

describe('speedWarnings', () => {
  it('says nothing when no duration was recorded', () => {
    expect(speedWarnings(performance(fields()))).toEqual([]);
  });

  it('says nothing about a sensible speed', () => {
    expect(speedWarnings(performance(fields({ duration: 45 })))).toEqual([]);
  });

  it('catches a duration that is too short to be true', () => {
    expect(speedWarnings(performance(fields({ duration: 10 })))[0]).toMatch(/faster than anybody/);
  });

  it('catches a duration that is far too long', () => {
    expect(speedWarnings(performance(fields({ duration: 300 })))[0]).toMatch(/slower than anybody/);
  });
});

describe('validateAll', () => {
  it('reports only the records with something wrong', () => {
    const records = [
      performance(fields()),
      performance(fields({ band: six.slice(0, 5) })),
      performance(fields({ conductor: undefined })),
    ];
    const found = validateAll(records, { today: TODAY });
    expect(found).toHaveLength(2);
  });

  it('reports nothing when everything is sound', () => {
    expect(validateAll([performance(fields())], { today: TODAY })).toEqual([]);
  });
});

describe('describeVerdict', () => {
  it('says so when there is nothing wrong', () => {
    expect(describeVerdict(check({}))).toBe('nothing wrong with it');
  });

  it('mentions the warnings when the record still passes', () => {
    expect(describeVerdict(check({ conductor: undefined }))).toBe(
      'nothing wrong with it, but no conductor is recorded',
    );
  });

  it('lists the problems when it does not', () => {
    expect(describeVerdict(check({ band: six.slice(0, 5) }))).toMatch(/needs a band of 6/);
  });
});
