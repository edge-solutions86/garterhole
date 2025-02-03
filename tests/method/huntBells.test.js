import { describe, it, expect } from 'vitest';
import {
  huntBells,
  workingBells,
  isPrinciple,
  pathOf,
  dodgePlaces,
  placesMade,
  huntPathKind,
  blowsInEachPlace,
} from '../../src/method/huntBells.js';
import { method } from '../../src/method/method.js';

const plainBobMinor = () => method('Plain Bob', 6, 'x1x1x1,2');
const cambridgeMinor = () => method('Cambridge Surprise', 6, 'x36x14x12x36x14x56,12');
const doubleNorwich = () => method('Double Norwich Court Bob', 8, 'x14x36x58x18,18');
const grandsireDoubles = () => method('Grandsire', 5, '3,1.5.1.5.1');

/** No bell is left in its own place by the lead head, so nothing hunts. */
const principle = () => method('Test Principle', 5, '3.1.5.3.1.3.5.1.3.1');

/** Written for the tests: the treble makes a place in thirds. */
const treblePlace = () => method('Test Treble Place', 6, 'x16.34.16x16x16x16x12');

describe('huntBells', () => {
  it('finds the treble in plain bob', () => {
    expect(huntBells(plainBobMinor())).toEqual([1]);
  });

  it('finds two hunt bells in grandsire', () => {
    expect(huntBells(grandsireDoubles())).toEqual([1, 2]);
  });

  it('finds none in a principle', () => {
    expect(huntBells(principle())).toEqual([]);
    expect(isPrinciple(principle())).toBe(true);
  });

  it('says a treble dominated method is not a principle', () => {
    expect(isPrinciple(plainBobMinor())).toBe(false);
    expect(isPrinciple(cambridgeMinor())).toBe(false);
  });

  it('refuses something that is not a method', () => {
    expect(() => huntBells('Plain Bob')).toThrow(/a Method is needed/);
    expect(() => pathOf(null, 1)).toThrow(TypeError);
  });
});

describe('workingBells', () => {
  it('lists everything that is not hunting', () => {
    expect(workingBells(plainBobMinor())).toEqual([2, 3, 4, 5, 6]);
    expect(workingBells(grandsireDoubles())).toEqual([3, 4, 5]);
  });

  it('lists every bell in a principle', () => {
    expect(workingBells(principle())).toEqual([1, 2, 3, 4, 5]);
  });
});

describe('pathOf', () => {
  it('traces plain hunt for the treble in plain bob minor', () => {
    expect(pathOf(plainBobMinor(), 1)).toEqual([1, 2, 3, 4, 5, 6, 6, 5, 4, 3, 2, 1, 1]);
  });

  it('traces the treble hunting up to eighths in double norwich', () => {
    expect(pathOf(doubleNorwich(), 1)).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 8, 7, 6, 5, 4, 3, 2, 1, 1]);
  });

  it('traces treble bob hunting in cambridge', () => {
    expect(pathOf(cambridgeMinor(), 1).slice(0, 8)).toEqual([1, 2, 1, 2, 3, 4, 3, 4]);
  });

  it('traces a working bell over one lead', () => {
    const path = pathOf(plainBobMinor(), 2);
    expect(path).toHaveLength(13);
    expect(path[0]).toBe(2);
    expect(path[path.length - 1]).toBe(4);
  });
});

describe('dodgePlaces', () => {
  it('finds nothing in plain hunt', () => {
    expect(dodgePlaces(pathOf(plainBobMinor(), 1), 6)).toEqual([]);
    expect(dodgePlaces(pathOf(doubleNorwich(), 1), 8)).toEqual([]);
  });

  it('finds every turn in treble bob hunting', () => {
    expect(dodgePlaces(pathOf(cambridgeMinor(), 1), 6)).toEqual([2, 4, 3, 5, 5, 3, 4, 2]);
  });

  it('ignores the turns at the front and the back', () => {
    expect(dodgePlaces([1, 2, 3, 2, 1], 3)).toEqual([]);
  });
});

describe('placesMade', () => {
  it('finds nothing for a treble that never makes a place', () => {
    expect(placesMade(pathOf(plainBobMinor(), 1), 6)).toEqual([]);
    expect(placesMade(pathOf(cambridgeMinor(), 1), 6)).toEqual([]);
  });

  it('finds a place made in the middle of a path', () => {
    expect(placesMade([1, 2, 3, 3, 2, 1], 6)).toEqual([3]);
  });

  it('ignores the two blows at the front and the back', () => {
    expect(placesMade([1, 1, 2, 3, 3], 3)).toEqual([]);
  });
});

describe('huntPathKind', () => {
  it('calls the treble in plain bob plain hunting', () => {
    expect(huntPathKind(plainBobMinor(), 1)).toBe('plain');
    expect(huntPathKind(doubleNorwich(), 1)).toBe('plain');
  });

  it('calls the treble in a surprise method treble bob', () => {
    expect(huntPathKind(cambridgeMinor(), 1)).toBe('treble bob');
  });

  it('calls a path with a place in it treble place', () => {
    expect(huntPathKind(treblePlace(), 1)).toBe('treble place');
  });

  it('calls both grandsire hunt bells plain', () => {
    expect(huntPathKind(grandsireDoubles(), 1)).toBe('plain');
    expect(huntPathKind(grandsireDoubles(), 2)).toBe('plain');
  });
});

describe('blowsInEachPlace', () => {
  it('spreads a plain hunting treble evenly', () => {
    expect(blowsInEachPlace(plainBobMinor(), 1)).toEqual([2, 2, 2, 2, 2, 2]);
  });

  it('spreads a treble bob treble evenly too', () => {
    expect(blowsInEachPlace(cambridgeMinor(), 1)).toEqual([4, 4, 4, 4, 4, 4]);
  });

  it('counts one lead of a working bell', () => {
    const counts = blowsInEachPlace(plainBobMinor(), 5);
    expect(counts.reduce((total, count) => total + count, 0)).toBe(12);
  });
});
