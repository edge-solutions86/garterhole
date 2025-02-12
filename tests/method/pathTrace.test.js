import { describe, it, expect } from 'vitest';
import {
  coursePath,
  describePath,
  turnPoints,
  workOf,
  pathByLead,
  samePathRotated,
} from '../../src/method/pathTrace.js';
import { method } from '../../src/method/method.js';

const plainBobMinor = () => method('Plain Bob', 6, 'x1x1x1,2');
const cambridgeMinor = () => method('Cambridge Surprise', 6, 'x36x14x12x36x14x56,12');

/** @param {{kind: string, place: number}[]} pieces */
const shape = (pieces) => pieces.map((piece) => `${piece.kind}@${piece.place}`);

describe('coursePath', () => {
  it('has one entry per row of the course', () => {
    expect(coursePath(plainBobMinor(), 2)).toHaveLength(61);
    expect(coursePath(cambridgeMinor(), 2)).toHaveLength(121);
  });

  it('starts and finishes in the same place', () => {
    const path = coursePath(plainBobMinor(), 4);
    expect(path[0]).toBe(4);
    expect(path[path.length - 1]).toBe(4);
  });

  it('refuses something that is not a method', () => {
    expect(() => coursePath({}, 2)).toThrow(/a Method is needed/);
  });
});

describe('turnPoints', () => {
  it('marks a reversal', () => {
    expect(turnPoints([1, 2, 1, 2])).toEqual([false, true, true, false]);
  });

  it('marks nothing in a straight run', () => {
    expect(turnPoints([1, 2, 3, 4])).toEqual([false, false, false, false]);
  });

  it('does not treat a repeated blow as a reversal', () => {
    expect(turnPoints([1, 2, 2, 1])).toEqual([false, false, false, false]);
  });
});

describe('describePath', () => {
  it('calls two blows at the front a lead', () => {
    expect(shape(describePath([2, 1, 1, 2], 6))).toEqual(['hunt@2', 'lead@1']);
  });

  it('calls two blows at the back a lie', () => {
    expect(shape(describePath([5, 6, 6, 5], 6))).toEqual(['hunt@5', 'lie@6']);
  });

  it('calls two blows anywhere else a place', () => {
    expect(shape(describePath([2, 3, 3, 2], 6))).toEqual(['hunt@2', 'place@3']);
  });

  it('calls a pair of turns one place apart a dodge', () => {
    expect(shape(describePath([5, 4, 3, 4, 3, 2], 6))).toEqual([
      'hunt@5',
      'hunt@4',
      'dodge@3',
      'hunt@3',
    ]);
  });

  it('calls a lone turn a point', () => {
    expect(shape(describePath([3, 4, 3, 2, 1], 6))).toEqual([
      'hunt@3',
      'point@4',
      'hunt@3',
      'hunt@2',
    ]);
  });
});

describe('workOf', () => {
  it('reads the plain bob minor blue line', () => {
    expect(shape(workOf(plainBobMinor(), 2))).toEqual([
      'lead@1',
      'lie@6',
      'dodge@3',
      'lead@1',
      'lie@6',
      'dodge@5',
      'lead@1',
      'dodge@5',
      'lie@6',
      'lead@1',
      'dodge@3',
      'lie@6',
      'lead@1',
      'place@2',
    ]);
  });

  it('gives the treble nothing but leading and lying', () => {
    const kinds = new Set(workOf(plainBobMinor(), 1).map((piece) => piece.kind));
    expect([...kinds].sort()).toEqual(['lead', 'lie']);
  });

  it('finds the treble bob front work in cambridge', () => {
    expect(shape(workOf(cambridgeMinor(), 1)).slice(0, 4)).toEqual([
      'dodge@1',
      'dodge@3',
      'dodge@5',
      'lie@6',
    ]);
  });

  it('leaves out the plain hunting', () => {
    expect(workOf(plainBobMinor(), 3).every((piece) => piece.kind !== 'hunt')).toBe(true);
  });
});

describe('pathByLead', () => {
  it('gives one line per lead', () => {
    const lines = pathByLead(plainBobMinor(), 2);
    expect(lines).toHaveLength(5);
    expect(lines[0]).toHaveLength(12);
    expect(lines[0]).toEqual([2, 1, 1, 2, 3, 4, 5, 6, 6, 5, 4, 3]);
  });

  it('gives one line per lead of a surprise method', () => {
    const lines = pathByLead(cambridgeMinor(), 2);
    expect(lines).toHaveLength(5);
    expect(lines[0]).toHaveLength(24);
  });
});

describe('samePathRotated', () => {
  it('finds that every working bell rings the same line in plain bob', () => {
    const bob = plainBobMinor();
    const second = coursePath(bob, 2).slice(0, -1);
    for (const bell of [3, 4, 5, 6]) {
      expect(samePathRotated(second, coursePath(bob, bell).slice(0, -1))).toBe(true);
    }
  });

  it('does not confuse the treble with a working bell', () => {
    const bob = plainBobMinor();
    expect(samePathRotated(coursePath(bob, 1).slice(0, -1), coursePath(bob, 2).slice(0, -1))).toBe(
      false,
    );
  });

  it('refuses to compare paths of different lengths', () => {
    expect(samePathRotated([1, 2], [1, 2, 3])).toBe(false);
  });
});
