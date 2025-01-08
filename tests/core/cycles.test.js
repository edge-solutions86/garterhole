import { describe, it, expect } from 'vitest';
import {
  cycles,
  cycleLengths,
  orderOf,
  parity,
  isInCourse,
  fixedBells,
  writeCycles,
} from '../../src/core/cycles.js';
import { rounds } from '../../src/core/row.js';
import { parseRow } from '../../src/core/rowParse.js';
import { order } from '../../src/core/transposition.js';

describe('cycles', () => {
  it('gives every bell its own cycle in rounds', () => {
    expect(cycles(rounds(4))).toEqual([[1], [2], [3], [4]]);
  });

  it('finds a three cycle', () => {
    expect(cycles(parseRow('23145'))).toEqual([[1, 2, 3], [4], [5]]);
  });

  it('finds two cycles at once', () => {
    expect(cycles(parseRow('214536'))).toEqual([[1, 2], [3, 4, 5], [6]]);
  });

  it('starts each cycle at its lowest bell', () => {
    for (const cycle of cycles(parseRow('13527486'))) {
      expect(cycle[0]).toBe(Math.min(...cycle));
    }
  });

  it('finds the seven cycle in a plain bob major lead head', () => {
    expect(cycles(parseRow('13527486'))).toEqual([[1], [2, 3, 5, 7, 8, 6, 4]]);
  });

  it('refuses something that is not a row', () => {
    expect(() => cycles('12345')).toThrow(/a row is needed/);
  });
});

describe('cycleLengths', () => {
  it('sorts longest first', () => {
    expect(cycleLengths(parseRow('214536'))).toEqual([3, 2, 1]);
  });

  it('adds up to the stage', () => {
    const row = parseRow('1234567890ET');
    expect(cycleLengths(row).reduce((total, length) => total + length, 0)).toBe(12);
  });
});

describe('orderOf', () => {
  it('says rounds has order one', () => {
    expect(orderOf(rounds(8))).toBe(1);
  });

  it('takes the least common multiple of the cycle lengths', () => {
    expect(orderOf(parseRow('214536'))).toBe(6);
    expect(orderOf(parseRow('13527486'))).toBe(7);
  });

  it('agrees with walking the group', () => {
    for (const text of ['13527486', '214365', '23145', '1234567890ET', '654321']) {
      const row = parseRow(text);
      expect(orderOf(row)).toBe(order(row));
    }
  });
});

describe('parity', () => {
  it('calls rounds even', () => {
    expect(parity(rounds(6))).toBe('even');
    expect(isInCourse(rounds(6))).toBe(true);
  });

  it('calls a single swap odd', () => {
    expect(parity(parseRow('214365'))).toBe('odd');
    expect(parity(parseRow('213456'))).toBe('odd');
  });

  it('calls a three cycle even', () => {
    expect(parity(parseRow('23145'))).toBe('even');
  });

  it('follows the number of pairs each change swaps rather than alternating', () => {
    const seen = ['214365', '241635', '426153', '462513'].map((text) => parity(parseRow(text)));
    expect(seen).toEqual(['odd', 'odd', 'even', 'even']);
  });

  it('splits the course heads into an in course half and an out of course half', () => {
    expect(isInCourse(parseRow('13527486'))).toBe(true);
    expect(isInCourse(parseRow('13526487'))).toBe(false);
  });
});

describe('fixedBells', () => {
  it('lists every bell in rounds', () => {
    expect(fixedBells(rounds(5))).toEqual([1, 2, 3, 4, 5]);
  });

  it('lists the treble in a plain bob lead head', () => {
    expect(fixedBells(parseRow('13527486'))).toEqual([1]);
  });

  it('lists nothing when every bell moves', () => {
    expect(fixedBells(parseRow('214365'))).toEqual([]);
  });
});

describe('writeCycles', () => {
  it('leaves out the bells that do not move', () => {
    expect(writeCycles(parseRow('214536'))).toBe('(12)(345)');
  });

  it('writes an empty pair of brackets for rounds', () => {
    expect(writeCycles(rounds(8))).toBe('()');
  });

  it('uses the bell alphabet above nine', () => {
    expect(writeCycles(parseRow('1234567890ET'))).toBe('()');
  });
});
