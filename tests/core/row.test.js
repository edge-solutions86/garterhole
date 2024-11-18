import { describe, it, expect } from 'vitest';
import { Row, rounds } from '../../src/core/row.js';

describe('Row construction', () => {
  it('keeps the bells in striking order', () => {
    const row = new Row([2, 1, 4, 3]);
    expect(row.bells).toEqual([2, 1, 4, 3]);
    expect(row.stage).toBe(4);
  });

  it('copies the array it was given', () => {
    const bells = [2, 1, 4, 3];
    const row = new Row(bells);
    bells[0] = 9;
    expect(row.bells[0]).toBe(2);
  });

  it('is frozen once built', () => {
    const row = new Row([1, 2, 3, 4]);
    expect(Object.isFrozen(row)).toBe(true);
    expect(Object.isFrozen(row.bells)).toBe(true);
  });

  it('refuses an empty row', () => {
    expect(() => new Row([])).toThrow(/at least one bell/);
  });

  it('refuses a repeated bell', () => {
    expect(() => new Row([1, 2, 2, 4])).toThrow(/appears more than once/);
  });

  it('refuses a bell outside the stage', () => {
    expect(() => new Row([1, 2, 3, 5])).toThrow(/outside 1\.\.4/);
    expect(() => new Row([0, 2, 3, 4])).toThrow(RangeError);
  });

  it('refuses a bell that is not a whole number', () => {
    expect(() => new Row([1, 2, 3.5, 4])).toThrow(RangeError);
  });
});

describe('Row lookup', () => {
  const row = new Row([3, 1, 4, 2]);

  it('reads the bell in a place', () => {
    expect(row.bellAt(1)).toBe(3);
    expect(row.bellAt(4)).toBe(2);
  });

  it('reads the place of a bell', () => {
    expect(row.placeOf(3)).toBe(1);
    expect(row.placeOf(2)).toBe(4);
  });

  it('refuses a place outside the row', () => {
    expect(() => row.bellAt(0)).toThrow(RangeError);
    expect(() => row.bellAt(5)).toThrow(/outside 1\.\.4/);
  });

  it('refuses a bell that is not ringing', () => {
    expect(() => row.placeOf(7)).toThrow(/not in a row of 4/);
  });

  it('agrees with itself in both directions', () => {
    for (let place = 1; place <= row.stage; place += 1) {
      expect(row.placeOf(row.bellAt(place))).toBe(place);
    }
  });
});

describe('rounds', () => {
  it('puts every bell in its own place', () => {
    expect(rounds(6).toString()).toBe('123456');
    expect(rounds(12).toString()).toBe('1234567890ET');
  });

  it('is recognised as rounds', () => {
    expect(rounds(8).isRounds()).toBe(true);
    expect(new Row([2, 1, 3, 4, 5, 6, 7, 8]).isRounds()).toBe(false);
  });

  it('refuses an unsupported stage', () => {
    expect(() => rounds(2)).toThrow(/not supported/);
    expect(() => rounds(17)).toThrow(/not supported/);
  });
});

describe('Row equality and printing', () => {
  it('compares by content', () => {
    expect(new Row([2, 1, 3, 4]).equals(new Row([2, 1, 3, 4]))).toBe(true);
    expect(new Row([2, 1, 3, 4]).equals(new Row([1, 2, 3, 4]))).toBe(false);
  });

  it('is not equal to a row at another stage', () => {
    expect(rounds(6).equals(rounds(8))).toBe(false);
  });

  it('is not equal to something that is not a row', () => {
    expect(rounds(6).equals('123456')).toBe(false);
    expect(rounds(6).equals(null)).toBe(false);
  });

  it('prints with the bell alphabet', () => {
    expect(new Row([12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1]).toString()).toBe('TE0987654321');
  });

  it('uses the printed form as its key', () => {
    const row = new Row([3, 1, 2]);
    expect(row.key()).toBe(row.toString());
  });

  it('hands out a mutable copy of the bells', () => {
    const row = rounds(4);
    const copy = row.toArray();
    copy[0] = 4;
    expect(row.bells[0]).toBe(1);
  });
});
