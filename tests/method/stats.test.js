import { describe, it, expect } from 'vitest';
import {
  placeBellOrder,
  blowsOverCourse,
  workCounts,
  methodStats,
  pathIsEven,
} from '../../src/method/stats.js';
import { method } from '../../src/method/method.js';

const plainBobMinor = () => method('Plain', 6, 'x1x1x1,2');
const plainBobMajor = () => method('Plain', 8, 'x18x18x18x18,12');
const cambridge = () => method('Cambridge', 8, 'x38x14x1258x36x14x58x16x78,12');
const principle = () => method('Test Principle', 5, '3.1.5.3.1.3.5.1.3.1');

describe('placeBellOrder', () => {
  it('starts at the first working place bell', () => {
    expect(placeBellOrder(plainBobMinor())[0]).toBe(2);
  });

  it('comes back to where it started', () => {
    const order = placeBellOrder(plainBobMinor());
    expect(order).toHaveLength(5);
    expect(new Set(order).size).toBe(5);
  });

  it('has one entry per working bell', () => {
    expect(placeBellOrder(plainBobMajor())).toHaveLength(7);
    expect(placeBellOrder(cambridge())).toHaveLength(7);
  });

  it('gives the plain bob minor order', () => {
    expect(placeBellOrder(plainBobMinor())).toEqual([2, 4, 6, 5, 3]);
  });

  it('gives nothing when every bell hunts', () => {
    expect(placeBellOrder(method('Test', 8, 'x18x18x18x18,18'))).toEqual([]);
  });

  it('works for a principle, where every bell is working', () => {
    expect(placeBellOrder(principle())[0]).toBe(1);
  });
});

describe('blowsOverCourse', () => {
  it('counts a whole course', () => {
    const counts = blowsOverCourse(plainBobMinor(), 2);
    expect(counts.reduce((total, count) => total + count, 0)).toBe(60);
  });

  it('spreads a working bell over every place', () => {
    expect(blowsOverCourse(plainBobMinor(), 2).every((count) => count > 0)).toBe(true);
  });

  it('keeps the treble even', () => {
    const counts = blowsOverCourse(plainBobMinor(), 1);
    expect(new Set(counts).size).toBe(1);
  });
});

describe('workCounts', () => {
  it('counts each kind of work', () => {
    const counts = workCounts(plainBobMinor(), 2);
    expect(counts.lead).toBe(5);
    expect(counts.dodge).toBe(4);
    expect(counts.place).toBe(1);
  });

  it('gives the treble only leads and lies', () => {
    expect(Object.keys(workCounts(plainBobMinor(), 1)).sort()).toEqual(['lead', 'lie']);
  });
});

describe('methodStats', () => {
  it('gathers everything a collection prints', () => {
    const stats = methodStats(cambridge());
    expect(stats.title).toBe('Cambridge (Major)');
    expect(stats.leadLength).toBe(32);
    expect(stats.leadsInCourse).toBe(7);
    expect(stats.courseLength).toBe(224);
    expect(stats.className).toBe('Surprise');
  });

  it('names the hunt and working bells', () => {
    const stats = methodStats(plainBobMajor());
    expect(stats.huntBells).toEqual([1]);
    expect(stats.workingBells).toEqual([2, 3, 4, 5, 6, 7, 8]);
  });

  it('refuses something that is not a method', () => {
    expect(() => methodStats('Plain Bob Major')).toThrow(/a Method is needed/);
  });
});

describe('pathIsEven', () => {
  it('is true for a hunt bell', () => {
    expect(pathIsEven(plainBobMinor(), 1)).toBe(true);
    expect(pathIsEven(cambridge(), 1)).toBe(true);
  });

  it('is true for a working bell in plain bob, which plain hunts all lead', () => {
    expect(pathIsEven(plainBobMinor(), 2)).toBe(true);
  });

  it('is false for a bell that spends longer in some places than others', () => {
    const surprise = cambridge();
    const uneven = [2, 3, 4, 5, 6, 7, 8].some((bell) => !pathIsEven(surprise, bell));
    expect(uneven).toBe(true);
  });
});
