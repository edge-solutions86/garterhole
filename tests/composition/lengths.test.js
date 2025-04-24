import { describe, it, expect } from 'vitest';
import {
  PEAL_MINIMUM,
  QUARTER_MINIMUM,
  extentLength,
  needsWholeExtents,
  pealLength,
  quarterLength,
  classifyLength,
  checkLength,
  extentsIn,
  isDateTouch,
} from '../../src/composition/lengths.js';
import { ValidationError } from '../../src/errors.js';

describe('extentLength', () => {
  it('counts the rows at each stage', () => {
    expect(extentLength(4)).toBe(24);
    expect(extentLength(5)).toBe(120);
    expect(extentLength(6)).toBe(720);
    expect(extentLength(7)).toBe(5040);
    expect(extentLength(8)).toBe(40320);
  });

  it('refuses a stage that is not a whole number', () => {
    expect(() => extentLength(0)).toThrow(ValidationError);
    expect(() => extentLength(6.5)).toThrow(/has no extent/);
  });
});

describe('needsWholeExtents', () => {
  it('applies below major', () => {
    expect(needsWholeExtents(5)).toBe(true);
    expect(needsWholeExtents(6)).toBe(true);
    expect(needsWholeExtents(7)).toBe(true);
  });

  it('does not apply from major upwards', () => {
    expect(needsWholeExtents(8)).toBe(false);
    expect(needsWholeExtents(12)).toBe(false);
  });
});

describe('pealLength', () => {
  it('is five thousand and forty on doubles and minor', () => {
    expect(pealLength(5)).toBe(5040);
    expect(pealLength(6)).toBe(5040);
  });

  it('is one extent on triples', () => {
    expect(pealLength(7)).toBe(5040);
  });

  it('is five thousand from major upwards', () => {
    expect(pealLength(8)).toBe(PEAL_MINIMUM);
    expect(pealLength(12)).toBe(5000);
  });

  it('rounds up to whole extents on minimus', () => {
    expect(pealLength(4)).toBe(5016);
  });
});

describe('quarterLength', () => {
  it('does not depend on the stage', () => {
    expect(quarterLength()).toBe(QUARTER_MINIMUM);
    expect(quarterLength()).toBe(1250);
  });
});

describe('classifyLength', () => {
  it('calls a full length a peal', () => {
    expect(classifyLength(5040, 6)).toBe('peal');
    expect(classifyLength(5056, 8)).toBe('peal');
  });

  it('calls a shorter length a quarter peal', () => {
    expect(classifyLength(1260, 5)).toBe('quarter peal');
    expect(classifyLength(1280, 8)).toBe('quarter peal');
  });

  it('calls anything shorter a touch', () => {
    expect(classifyLength(720, 6)).toBe('touch');
    expect(classifyLength(0, 8)).toBe('touch');
  });

  it('will not call a part extent on minor a peal', () => {
    expect(classifyLength(5000, 6)).toBe('quarter peal');
  });

  it('refuses a length that is not a whole number', () => {
    expect(() => classifyLength(-1, 8)).toThrow(/cannot be -1 changes long/);
    expect(() => classifyLength(1.5, 8)).toThrow(ValidationError);
  });
});

describe('checkLength', () => {
  it('accepts a length that qualifies', () => {
    expect(checkLength(5040, 6, 'peal')).toEqual({ ok: true, needed: 5040, reason: null });
  });

  it('says how far short a length is', () => {
    expect(checkLength(4000, 8, 'peal').reason).toBe(
      'a peal on 8 needs at least 5000 changes, not 4000',
    );
  });

  it('says when a length is not whole extents', () => {
    expect(checkLength(5100, 6, 'peal').reason).toBe('on 6 a peal must be whole extents of 720');
  });

  it('does not apply the extent rule to a quarter', () => {
    expect(checkLength(1260, 6, 'quarter peal').ok).toBe(true);
  });

  it('refuses a length it does not know how to check', () => {
    expect(() => checkLength(5040, 6, 'half peal')).toThrow(/is not a length to check against/);
  });
});

describe('extentsIn', () => {
  it('counts whole extents', () => {
    expect(extentsIn(5040, 6)).toBe(7);
    expect(extentsIn(720, 6)).toBe(1);
  });

  it('gives null when the length is not whole extents', () => {
    expect(extentsIn(5000, 6)).toBeNull();
  });
});

describe('isDateTouch', () => {
  const date = new Date(2025, 6, 4);

  it('accepts the day and month written either way round', () => {
    expect(isDateTouch(407, date)).toBe(true);
    expect(isDateTouch(704, date)).toBe(true);
  });

  it('accepts the year on its own', () => {
    expect(isDateTouch(2025, date)).toBe(true);
  });

  it('rejects a length that is not the date', () => {
    expect(isDateTouch(1260, date)).toBe(false);
  });

  it('refuses something that is not a date', () => {
    expect(() => isDateTouch(2025, '2025-07-04')).toThrow(/a date is needed/);
    expect(() => isDateTouch(2025, new Date('nonsense'))).toThrow(ValidationError);
  });
});
