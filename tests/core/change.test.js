import { describe, it, expect } from 'vitest';
import { Change } from '../../src/core/change.js';
import { rounds } from '../../src/core/row.js';
import { parseRow } from '../../src/core/rowParse.js';
import { NotationError } from '../../src/errors.js';

describe('Change construction', () => {
  it('sorts and de-duplicates the places it is given', () => {
    const change = new Change(8, [8, 7, 1, 1, 2]);
    expect(change.places).toEqual([1, 2, 7, 8]);
  });

  it('is frozen', () => {
    const change = new Change(6, [1, 6]);
    expect(Object.isFrozen(change)).toBe(true);
    expect(Object.isFrozen(change.places)).toBe(true);
  });

  it('refuses a place outside the stage', () => {
    expect(() => new Change(6, [7])).toThrow(/place 7 is outside 1\.\.6/);
    expect(() => new Change(6, [0])).toThrow(RangeError);
  });

  it('refuses an unsupported stage', () => {
    expect(() => new Change(2, [])).toThrow(/stage 2 is not supported/);
  });

  it('refuses places that leave an odd gap', () => {
    expect(() => new Change(6, [1])).toThrow(NotationError);
    expect(() => new Change(6, [1])).toThrow('places 1 would leave 5 bells above place 1 on 6');
    expect(() => new Change(6, [2])).toThrow('places 2 would leave 1 bell below place 2 on 6');
    expect(() => new Change(8, [1, 3])).toThrow(
      'places 1,3 would leave 1 bell between places 1 and 3 on 8',
    );
  });

  it('refuses a cross change at an odd stage', () => {
    expect(() => Change.cross(5)).toThrow('a cross change would leave 5 bells in the change on 5');
  });

  it('accepts a cross change at an even stage', () => {
    expect(Change.cross(8).isCross()).toBe(true);
    expect(Change.cross(8).places).toEqual([]);
  });
});

describe('implied places', () => {
  it('adds the back place when the notation leaves one off', () => {
    expect(Change.withImpliedPlaces(6, [1]).places).toEqual([1, 6]);
    expect(Change.withImpliedPlaces(6, [3]).places).toEqual([3, 6]);
  });

  it('adds the front place when the notation leaves one off', () => {
    expect(Change.withImpliedPlaces(8, [2]).places).toEqual([1, 2]);
  });

  it('leaves a complete change alone', () => {
    expect(Change.withImpliedPlaces(8, [1, 8]).places).toEqual([1, 8]);
    expect(Change.withImpliedPlaces(6, [3, 4]).places).toEqual([3, 4]);
  });

  it('makes the back place at an odd stage with no places written', () => {
    expect(Change.withImpliedPlaces(5, []).places).toEqual([5]);
  });

  it('leaves a cross change alone at an even stage', () => {
    expect(Change.withImpliedPlaces(8, []).isCross()).toBe(true);
  });

  it('still refuses places that cannot be completed', () => {
    expect(() => Change.withImpliedPlaces(8, [3, 4, 6])).toThrow(NotationError);
  });
});

describe('applying a change', () => {
  it('swaps every pair for a cross change', () => {
    expect(Change.cross(4).apply(rounds(4)).toString()).toBe('2143');
    expect(Change.cross(8).apply(rounds(8)).toString()).toBe('21436587');
  });

  it('holds the bells that make places', () => {
    expect(new Change(4, [1, 4]).apply(rounds(4)).toString()).toBe('1324');
  });

  it('rings the first two changes of plain bob minor', () => {
    const first = Change.cross(6).apply(rounds(6));
    expect(first.toString()).toBe('214365');
    const second = Change.withImpliedPlaces(6, [1]).apply(first);
    expect(second.toString()).toBe('241635');
  });

  it('is its own inverse', () => {
    const change = Change.withImpliedPlaces(8, [3]);
    const row = parseRow('17583624');
    expect(change.apply(change.apply(row)).equals(row)).toBe(true);
  });

  it('refuses a row at another stage', () => {
    expect(() => Change.cross(6).apply(rounds(8))).toThrow(
      'a change on 6 cannot be applied to a row of 8',
    );
  });

  it('refuses something that is not a row', () => {
    expect(() => Change.cross(6).apply('123456')).toThrow(/applied to a Row/);
  });
});

describe('change identity', () => {
  it('reports the places it makes', () => {
    const change = new Change(8, [1, 8]);
    expect(change.makesPlace(1)).toBe(true);
    expect(change.makesPlace(8)).toBe(true);
    expect(change.makesPlace(4)).toBe(false);
  });

  it('compares by stage and places', () => {
    expect(new Change(8, [1, 2]).equals(new Change(8, [2, 1]))).toBe(true);
    expect(new Change(8, [1, 2]).equals(new Change(8, [1, 8]))).toBe(false);
    expect(new Change(8, [1, 2]).equals(new Change(6, [1, 2]))).toBe(false);
    expect(new Change(8, [1, 2]).equals('12')).toBe(false);
  });

  it('writes itself as place notation', () => {
    expect(Change.cross(8).toString()).toBe('x');
    expect(new Change(8, [1, 2]).toString()).toBe('12');
    expect(new Change(12, [1, 12]).toString()).toBe('1T');
    expect(new Change(10, [1, 10]).toString()).toBe('10');
  });
});
