import { describe, it, expect } from 'vitest';
import { IntervalSet, interval } from '../../src/tower/interval.js';
import { ValidationError } from '../../src/errors.js';

describe('Interval', () => {
  it('keeps its start and end and measures itself', () => {
    const slot = interval(10, 30);
    expect(slot.start).toBe(10);
    expect(slot.end).toBe(30);
    expect(slot.length).toBe(20);
  });

  it('is frozen', () => {
    expect(Object.isFrozen(interval(0, 1))).toBe(true);
  });

  it('includes its start and excludes its end', () => {
    const slot = interval(10, 20);
    expect(slot.contains(10)).toBe(true);
    expect(slot.contains(19)).toBe(true);
    expect(slot.contains(20)).toBe(false);
  });

  it('refuses an end that is not after the start', () => {
    expect(() => interval(10, 10)).toThrow(/cannot end at 10 having started at 10/);
    expect(() => interval(10, 5)).toThrow(ValidationError);
  });

  it('refuses a moment that is not a number', () => {
    expect(() => interval('10', 20)).toThrow(/is not a moment in time/);
    expect(() => interval(10, Number.NaN)).toThrow(ValidationError);
  });
});

describe('comparing intervals', () => {
  it('finds an overlap', () => {
    expect(interval(0, 10).overlaps(interval(5, 15))).toBe(true);
    expect(interval(0, 10).overlaps(interval(10, 20))).toBe(false);
  });

  it('finds one interval inside another', () => {
    expect(interval(0, 20).covers(interval(5, 15))).toBe(true);
    expect(interval(5, 15).covers(interval(0, 20))).toBe(false);
    expect(interval(0, 20).covers(interval(0, 20))).toBe(true);
  });

  it('takes the part two share', () => {
    expect(interval(0, 10).intersect(interval(5, 15)).toString()).toBe('[5, 10)');
  });

  it('gives null when they share nothing', () => {
    expect(interval(0, 10).intersect(interval(10, 20))).toBeNull();
  });

  it('compares by content', () => {
    expect(interval(0, 10).equals(interval(0, 10))).toBe(true);
    expect(interval(0, 10).equals(interval(0, 11))).toBe(false);
    expect(interval(0, 10).equals('0-10')).toBe(false);
  });
});

describe('IntervalSet', () => {
  it('starts empty', () => {
    const set = new IntervalSet();
    expect(set.size).toBe(0);
    expect(set.total).toBe(0);
  });

  it('keeps intervals in order', () => {
    const set = new IntervalSet([interval(30, 40), interval(0, 10)]);
    expect(set.toString()).toBe('[0, 10) [30, 40)');
  });

  it('merges intervals that overlap', () => {
    const set = new IntervalSet([interval(0, 10), interval(5, 20)]);
    expect(set.size).toBe(1);
    expect(set.toString()).toBe('[0, 20)');
  });

  it('merges intervals that meet exactly', () => {
    const set = new IntervalSet([interval(0, 10), interval(10, 20)]);
    expect(set.toString()).toBe('[0, 20)');
  });

  it('merges a run of intervals into one', () => {
    const set = new IntervalSet([interval(0, 10), interval(30, 40), interval(5, 35)]);
    expect(set.toString()).toBe('[0, 40)');
  });

  it('adds up the time it holds', () => {
    expect(new IntervalSet([interval(0, 10), interval(20, 25)]).total).toBe(15);
  });

  it('refuses anything that is not an interval', () => {
    expect(() => new IntervalSet([{ start: 0, end: 1 }])).toThrow(/only an Interval/);
  });
});

describe('reading an interval set', () => {
  const set = () => new IntervalSet([interval(0, 10), interval(20, 30)]);

  it('says whether it holds a moment', () => {
    expect(set().contains(5)).toBe(true);
    expect(set().contains(15)).toBe(false);
  });

  it('says whether it covers a whole interval', () => {
    expect(set().covers(interval(2, 8))).toBe(true);
    expect(set().covers(interval(8, 22))).toBe(false);
  });

  it('iterates in order', () => {
    expect([...set()].map((slot) => slot.start)).toEqual([0, 20]);
  });
});

describe('intersecting sets', () => {
  it('keeps only what both hold', () => {
    const left = new IntervalSet([interval(0, 10), interval(20, 30)]);
    const right = new IntervalSet([interval(5, 25)]);
    expect(left.intersect(right).toString()).toBe('[5, 10) [20, 25)');
  });

  it('gives nothing when they share nothing', () => {
    const left = new IntervalSet([interval(0, 10)]);
    const right = new IntervalSet([interval(20, 30)]);
    expect(left.intersect(right).size).toBe(0);
  });

  it('gives everything when one covers the other', () => {
    const left = new IntervalSet([interval(0, 30)]);
    const right = new IntervalSet([interval(5, 10)]);
    expect(left.intersect(right).toString()).toBe('[5, 10)');
  });
});
