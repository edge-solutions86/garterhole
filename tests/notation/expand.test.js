import { describe, it, expect } from 'vitest';
import {
  expandNotation,
  expandSections,
  mirrorSection,
  isPalindromic,
  findSymmetryPoint,
  rotateToAxis,
} from '../../src/notation/expand.js';
import { parseChanges, parseNotation } from '../../src/notation/parser.js';

const PLAIN_BOB_MINOR = 'x1x1x1,2';
const CAMBRIDGE_MINOR = 'x3x4x2x3x4x5,2';
const GRANDSIRE_DOUBLES = '3,1.5.1.5.1';

describe('mirrorSection', () => {
  it('writes a section out and then backwards without its last change', () => {
    const section = parseChanges('x1x1x1', 6);
    const mirrored = mirrorSection(section);
    expect(mirrored.map(String)).toEqual([
      'x',
      '16',
      'x',
      '16',
      'x',
      '16',
      'x',
      '16',
      'x',
      '16',
      'x',
    ]);
  });

  it('leaves a single change alone', () => {
    expect(mirrorSection(parseChanges('12', 8)).map(String)).toEqual(['12']);
  });

  it('gives nothing back for nothing', () => {
    expect(mirrorSection([])).toEqual([]);
  });
});

describe('expandNotation', () => {
  it('expands plain bob minor to a whole lead of twelve changes', () => {
    const changes = expandNotation(PLAIN_BOB_MINOR, 6);
    expect(changes).toHaveLength(12);
    expect(changes.map(String).join('.')).toBe('x.16.x.16.x.16.x.16.x.16.x.12');
  });

  it('expands cambridge surprise minor to twenty four changes', () => {
    const changes = expandNotation(CAMBRIDGE_MINOR, 6);
    expect(changes).toHaveLength(24);
    expect(changes.map(String).slice(0, 6).join('.')).toBe('x.36.x.14.x.12');
    expect(changes[23].toString()).toBe('12');
  });

  it('expands grandsire doubles', () => {
    const changes = expandNotation(GRANDSIRE_DOUBLES, 5);
    expect(changes.map(String).join('.')).toBe('3.1.5.1.5.1.5.1.5.1');
  });

  it('leaves notation with no comma exactly as written', () => {
    const changes = expandNotation('x18x18x18x18', 8);
    expect(changes.map(String)).toEqual(['x', '18', 'x', '18', 'x', '18', 'x', '18']);
  });

  it('passes a bad symbol on from the parser', () => {
    expect(() => expandNotation('x1?x1', 6)).toThrow(/not part of place notation/);
  });
});

describe('expandSections', () => {
  it('takes parsed sections directly', () => {
    const parsed = parseNotation(PLAIN_BOB_MINOR, 6);
    expect(expandSections(parsed.sections)).toHaveLength(12);
  });

  it('copies rather than returning the section it was given', () => {
    const parsed = parseNotation('x18x18', 8);
    const expanded = expandSections(parsed.sections);
    expect(expanded).not.toBe(parsed.sections[0]);
    expect(expanded.map(String)).toEqual(parsed.sections[0].map(String));
  });

  it('refuses nothing to expand', () => {
    expect(() => expandSections([])).toThrow('there is nothing to expand');
    expect(() => expandSections(null)).toThrow('there is nothing to expand');
  });
});

describe('palindromes', () => {
  it('recognises an expanded lead as palindromic', () => {
    expect(isPalindromic(expandNotation(PLAIN_BOB_MINOR, 6))).toBe(true);
    expect(isPalindromic(expandNotation(CAMBRIDGE_MINOR, 6))).toBe(true);
    expect(isPalindromic(expandNotation(GRANDSIRE_DOUBLES, 5))).toBe(true);
  });

  it('rejects a run of changes with no axis', () => {
    expect(isPalindromic(parseChanges('16.14.12', 6))).toBe(false);
    expect(findSymmetryPoint(parseChanges('16.14.12', 6))).toBeNull();
  });

  it('says nothing is not a palindrome', () => {
    expect(findSymmetryPoint([])).toBeNull();
    expect(isPalindromic([])).toBe(false);
  });

  it('finds the first axis of a lead', () => {
    expect(findSymmetryPoint(expandNotation(PLAIN_BOB_MINOR, 6))).toBe(5);
  });
});

describe('rotateToAxis', () => {
  it('moves the axis change to the end', () => {
    const changes = parseChanges('x18x12', 8);
    const rotated = rotateToAxis(changes, 1);
    expect(rotated.map(String)).toEqual(['x', '12', 'x', '18']);
  });

  it('leaves a lead alone when the axis is already last', () => {
    const changes = parseChanges('x18x12', 8);
    expect(rotateToAxis(changes, 3).map(String)).toEqual(changes.map(String));
  });

  it('keeps a rotated lead palindromic', () => {
    const changes = expandNotation(CAMBRIDGE_MINOR, 6);
    const axis = findSymmetryPoint(changes);
    expect(isPalindromic(rotateToAxis(changes, axis))).toBe(true);
  });

  it('refuses an axis outside the lead', () => {
    expect(() => rotateToAxis(parseChanges('x18', 8), 5)).toThrow(/outside 0\.\.1/);
    expect(() => rotateToAxis(parseChanges('x18', 8), -1)).toThrow(RangeError);
  });
});
