import { describe, it, expect } from 'vitest';
import {
  reverseChange,
  reverseChanges,
  isRotationOf,
  isPalindromic,
  isDouble,
  hasRotationalSymmetry,
  symmetryOf,
} from '../../src/method/symmetry.js';
import { method } from '../../src/method/method.js';
import { Change } from '../../src/core/change.js';
import { parseChanges } from '../../src/notation/parser.js';

const plainBobMinor = () => method('Plain Bob', 6, 'x1x1x1,2');
const cambridgeMajor = () => method('Cambridge Surprise', 8, 'x38x14x1258x36x14x58x16x78,12');
const bristolMajor = () => method('Bristol Surprise', 8, 'x58x14.58x58.36.14x14.58x14x18,18');
const doubleNorwich = () => method('Double Norwich Court Bob', 8, 'x14x36x58x18,18');

describe('reverseChange', () => {
  it('turns a place at the front into a place at the back', () => {
    expect(reverseChange(new Change(8, [1, 2])).toString()).toBe('78');
  });

  it('leaves a change that is already symmetric alone', () => {
    expect(reverseChange(new Change(8, [1, 8])).toString()).toBe('18');
    expect(reverseChange(Change.cross(8)).toString()).toBe('x');
  });

  it('is its own inverse', () => {
    const change = new Change(8, [1, 4]);
    expect(reverseChange(reverseChange(change)).equals(change)).toBe(true);
  });

  it('reverses a whole lead', () => {
    expect(reverseChanges(parseChanges('x12x18', 8)).map(String)).toEqual(['x', '78', 'x', '18']);
  });
});

describe('isRotationOf', () => {
  it('finds a rotation', () => {
    const left = parseChanges('x18x12', 8);
    const right = parseChanges('x12x18', 8);
    expect(isRotationOf(left, right)).toBe(true);
  });

  it('finds a run equal to itself', () => {
    const changes = parseChanges('x18x12', 8);
    expect(isRotationOf(changes, changes)).toBe(true);
  });

  it('rejects a different run', () => {
    expect(isRotationOf(parseChanges('x18x12', 8), parseChanges('x18x16', 8))).toBe(false);
  });

  it('rejects runs of different lengths', () => {
    expect(isRotationOf(parseChanges('x18', 8), parseChanges('x18x12', 8))).toBe(false);
  });
});

describe('isPalindromic', () => {
  it('finds the standard methods palindromic', () => {
    expect(isPalindromic(plainBobMinor())).toBe(true);
    expect(isPalindromic(cambridgeMajor())).toBe(true);
    expect(isPalindromic(bristolMajor())).toBe(true);
  });

  it('rejects a lead with no axis', () => {
    expect(isPalindromic(method('Asymmetric', 6, '16.14.12'))).toBe(false);
  });
});

describe('isDouble', () => {
  it('finds the double methods', () => {
    expect(isDouble(bristolMajor())).toBe(true);
    expect(isDouble(doubleNorwich())).toBe(true);
  });

  it('rejects methods that are not double', () => {
    expect(isDouble(plainBobMinor())).toBe(false);
    expect(isDouble(cambridgeMajor())).toBe(false);
  });
});

describe('hasRotationalSymmetry', () => {
  it('finds it in the double methods', () => {
    expect(hasRotationalSymmetry(bristolMajor())).toBe(true);
    expect(hasRotationalSymmetry(doubleNorwich())).toBe(true);
  });

  it('does not find it in plain bob', () => {
    expect(hasRotationalSymmetry(plainBobMinor())).toBe(false);
  });
});

describe('symmetryOf', () => {
  it('reports every symmetry a method has', () => {
    expect(symmetryOf(bristolMajor())).toEqual({
      palindromic: true,
      double: true,
      rotational: true,
      names: ['palindromic', 'double', 'rotational'],
    });
  });

  it('reports only palindromic for plain bob', () => {
    expect(symmetryOf(plainBobMinor()).names).toEqual(['palindromic']);
  });

  it('refuses something that is not a method', () => {
    expect(() => symmetryOf('Plain Bob')).toThrow(/a Method is needed/);
  });
});
