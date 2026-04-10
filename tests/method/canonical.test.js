import { describe, it, expect } from 'vitest';
import {
  rotations,
  canonicalNotation,
  canonicalForm,
  reversedForm,
  isRotationOfMethod,
  isReverseOfMethod,
  groupBySameLead,
  duplicateSpellings,
} from '../../src/method/canonical.js';
import { method } from '../../src/method/method.js';
import { parseChanges } from '../../src/notation/parser.js';

const plainBobMajor = () => method('Plain', 8, 'x18x18x18x18,12');
/** The same lead, written starting one change further on. */
const rotated = () => method('Rotated', 8, rotations(plainBobMajor().changes)[1]);
const cambridge = () => method('Cambridge', 8, 'x38x14x1258x36x14x58x16x78,12');
const bristol = () => method('Bristol', 8, 'x58x14.58x58.36.14x14.58x14x18,18');

describe('rotations', () => {
  it('gives one rotation per change', () => {
    expect(rotations(parseChanges('x18x12', 8))).toHaveLength(4);
  });

  it('starts each rotation at a different change', () => {
    expect(rotations(parseChanges('x18x12', 8))).toEqual(['x18x12', '18x12x', 'x12x18', '12x18x']);
  });

  it('gives nothing for nothing', () => {
    expect(rotations([])).toEqual([]);
  });
});

describe('canonicalNotation', () => {
  it('is the same for every rotation', () => {
    const changes = parseChanges('x18x12', 8);
    for (const rotation of rotations(changes)) {
      expect(canonicalNotation(parseChanges(rotation, 8))).toBe(canonicalNotation(changes));
    }
  });

  it('is empty for nothing', () => {
    expect(canonicalNotation([])).toBe('');
  });
});

describe('canonicalForm', () => {
  it('keeps the stage so two stages never collide', () => {
    expect(canonicalForm(plainBobMajor()).startsWith('8:')).toBe(true);
  });

  it('is the same for a method written from another starting point', () => {
    expect(canonicalForm(plainBobMajor())).toBe(canonicalForm(rotated()));
  });

  it('is different for two different methods', () => {
    expect(canonicalForm(plainBobMajor())).not.toBe(canonicalForm(cambridge()));
  });

  it('refuses something that is not a method', () => {
    expect(() => canonicalForm('Plain Bob Major')).toThrow(/a Method is needed/);
  });
});

describe('comparing methods', () => {
  it('recognises a rotation', () => {
    expect(isRotationOfMethod(plainBobMajor(), rotated())).toBe(true);
    expect(isRotationOfMethod(plainBobMajor(), cambridge())).toBe(false);
  });

  it('recognises a method as its own reverse when it is double', () => {
    expect(isReverseOfMethod(bristol(), bristol())).toBe(true);
  });

  it('does not call an ordinary method its own reverse', () => {
    expect(isReverseOfMethod(cambridge(), cambridge())).toBe(false);
  });

  it('gives the reversed form of a method', () => {
    expect(reversedForm(plainBobMajor())).not.toBe(canonicalForm(plainBobMajor()));
  });
});

describe('grouping', () => {
  it('puts every spelling of one method together', () => {
    const groups = groupBySameLead([plainBobMajor(), rotated(), cambridge()]);
    expect(groups.size).toBe(2);
    expect([...groups.values()].find((bucket) => bucket.length === 2)).toHaveLength(2);
  });

  it('gives an empty map for nothing', () => {
    expect(groupBySameLead([]).size).toBe(0);
  });

  it('finds the spellings of something already in the list', () => {
    const duplicates = duplicateSpellings([plainBobMajor(), rotated(), cambridge()]);
    expect(duplicates.map((entry) => entry.name)).toEqual(['Rotated']);
  });

  it('finds none in a list of different methods', () => {
    expect(duplicateSpellings([plainBobMajor(), cambridge(), bristol()])).toEqual([]);
  });
});
