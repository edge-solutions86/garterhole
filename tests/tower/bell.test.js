import { describe, it, expect } from 'vitest';
import {
  Bell,
  bellWeighing,
  notes,
  POUNDS_PER_HUNDREDWEIGHT,
  POUNDS_PER_QUARTER,
} from '../../src/tower/bell.js';
import { ValidationError } from '../../src/errors.js';

describe('Bell', () => {
  it('keeps what it was given', () => {
    const bell = new Bell({ number: 8, weightPounds: 2643, note: 'D', founder: 'Taylor' });
    expect(bell.number).toBe(8);
    expect(bell.weightPounds).toBe(2643);
    expect(bell.note).toBe('D');
    expect(bell.founder).toBe('Taylor');
  });

  it('is frozen', () => {
    expect(Object.isFrozen(new Bell({ number: 1, weightPounds: 400 }))).toBe(true);
  });

  it('defaults the details it was not given', () => {
    const bell = new Bell({ number: 1, weightPounds: 400 });
    expect(bell.note).toBeNull();
    expect(bell.founder).toBeNull();
    expect(bell.cast).toBeNull();
  });

  it('refuses a definition that is not an object', () => {
    expect(() => new Bell('tenor')).toThrow(/needs a definition object/);
  });

  it('refuses a bell number that is not a whole number above zero', () => {
    expect(() => new Bell({ number: 0, weightPounds: 400 })).toThrow(/is not a bell number/);
    expect(() => new Bell({ number: 1.5, weightPounds: 400 })).toThrow(ValidationError);
  });

  it('refuses a weight that is not positive', () => {
    expect(() => new Bell({ number: 1, weightPounds: 0 })).toThrow(/cannot weigh 0 pounds/);
  });

  it('refuses a note that is not a note', () => {
    expect(() => new Bell({ number: 1, weightPounds: 400, note: 'H' })).toThrow(/is not a note/);
  });

  it('refuses a casting year outside the range bells were cast in', () => {
    expect(() => new Bell({ number: 1, weightPounds: 400, cast: 1000 })).toThrow(
      /is not a year a bell was cast in/,
    );
  });
});

describe('weight', () => {
  it('breaks pounds into hundredweight, quarters and pounds', () => {
    const bell = new Bell({ number: 8, weightPounds: 2643 });
    expect(bell.weight).toEqual({ hundredweight: 23, quarters: 2, pounds: 11 });
  });

  it('writes the weight the way a tower board does', () => {
    expect(new Bell({ number: 8, weightPounds: 2643 }).weightText).toBe('23-2-11');
  });

  it('handles an exact hundredweight', () => {
    const bell = new Bell({ number: 1, weightPounds: POUNDS_PER_HUNDREDWEIGHT * 4 });
    expect(bell.weightText).toBe('4-0-0');
  });

  it('handles a weight under a hundredweight', () => {
    expect(new Bell({ number: 1, weightPounds: POUNDS_PER_QUARTER * 3 }).weightText).toBe('0-3-0');
  });

  it('prints itself', () => {
    const bell = new Bell({ number: 8, weightPounds: 2643, note: 'D' });
    expect(bell.toString()).toBe('bell 8, 23-2-11 in D');
    expect(new Bell({ number: 1, weightPounds: 448 }).toString()).toBe('bell 1, 4-0-0');
  });
});

describe('bellWeighing', () => {
  it('reads a weight off a tower board', () => {
    const bell = bellWeighing(8, '23-2-11');
    expect(bell.weightPounds).toBe(2643);
    expect(bell.weightText).toBe('23-2-11');
  });

  it('takes the other details as well', () => {
    const bell = bellWeighing(8, '23-2-11', { note: 'D', cast: 1901 });
    expect(bell.note).toBe('D');
    expect(bell.cast).toBe(1901);
  });

  it('round trips every weight it can read', () => {
    for (const weight of ['0-1-1', '4-0-0', '12-3-27', '23-2-11']) {
      expect(bellWeighing(1, weight).weightText).toBe(weight);
    }
  });

  it('refuses a weight with the wrong number of parts', () => {
    expect(() => bellWeighing(1, '23-2')).toThrow(/is not a weight in hundredweight/);
    expect(() => bellWeighing(1, '')).toThrow(/a bell weight must be a non empty string/);
  });

  it('refuses a part that is not a whole number', () => {
    expect(() => bellWeighing(1, '23-2-x')).toThrow(/has a part that is not a whole number/);
    expect(() => bellWeighing(1, '23--1-2')).toThrow(ValidationError);
  });

  it('refuses more quarters or pounds than fit', () => {
    expect(() => bellWeighing(1, '23-4-0')).toThrow(/is more than a hundredweight/);
    expect(() => bellWeighing(1, '23-2-28')).toThrow(/is more than a quarter/);
  });
});

describe('notes', () => {
  it('lists the twelve notes', () => {
    expect(notes()).toHaveLength(12);
    expect(notes()).toContain('C');
    expect(notes()).toContain('A#');
  });

  it('hands back a copy', () => {
    const first = notes();
    first.push('H');
    expect(notes()).toHaveLength(12);
  });
});
