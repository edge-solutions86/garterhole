import { describe, it, expect } from 'vitest';
import {
  sectionOf,
  crossSections,
  makesInternalPlace,
  isLittle,
  isDifferential,
  baseClass,
  classify,
} from '../../src/method/classify.js';
import { method } from '../../src/method/method.js';
import { Change } from '../../src/core/change.js';

const named = (name, stage, notation) => method(name, stage, notation);

const METHODS = {
  plainBobMinor: named('Plain Bob', 6, 'x1x1x1,2'),
  plainBobMajor: named('Plain Bob', 8, 'x18x18x18x18,12'),
  grandsireDoubles: named('Grandsire', 5, '3,1.5.1.5.1'),
  doubleNorwich: named('Double Norwich Court Bob', 8, 'x14x36x58x18,18'),
  cambridgeMinor: named('Cambridge Surprise', 6, 'x36x14x12x36x14x56,12'),
  cambridgeMajor: named('Cambridge Surprise', 8, 'x38x14x1258x36x14x58x16x78,12'),
  yorkshireMajor: named('Yorkshire Surprise', 8, 'x38x14x58x16x12x38x14x78,12'),
  bristolMajor: named('Bristol Surprise', 8, 'x58x14.58x58.36.14x14.58x14x18,18'),
  kentMajor: named('Kent Treble Bob', 8, '34x34.18x12x18x12x18x12x18,18'),
  littleBobMajor: named('Little Bob', 8, 'x18x14x18x12'),
};

/** Written for the tests: an internal place at some cross sections but not all. */
const delight = () => named('Test Delight', 6, '34x34.16x12x36x14x56,12');
/** Written for the tests: no bell is left in its place by the lead head. */
const principle = () => named('Test Principle', 5, '3.1.5.3.1.3.5.1.3.1');
/** Written for the tests: two hunt bells and working bells in cycles of different length. */
const differential = () => named('Test Differential', 8, 'x38x18x12x18,18');
/** Written for the tests: every bell hunts and nothing dodges. */
const everyBellHunts = () => named('Test Place', 8, 'x18x18x18x18,18');

describe('sectionOf', () => {
  it('pairs the places up from the front', () => {
    expect([1, 2, 3, 4, 5, 6].map(sectionOf)).toEqual([1, 1, 2, 2, 3, 3]);
  });
});

describe('crossSections', () => {
  it('marks where the path moves between pairs', () => {
    expect(crossSections([1, 2, 1, 2, 3, 4, 3, 4])).toEqual([3]);
  });

  it('marks nothing while the path stays in one pair', () => {
    expect(crossSections([1, 2, 1, 2])).toEqual([]);
  });

  it('marks every step of plain hunt', () => {
    expect(crossSections([1, 2, 3, 4, 5, 6])).toEqual([1, 3]);
  });
});

describe('makesInternalPlace', () => {
  it('ignores places at the front and the back', () => {
    expect(makesInternalPlace(new Change(8, [1, 8]))).toBe(false);
    expect(makesInternalPlace(Change.cross(8))).toBe(false);
  });

  it('finds a place in the middle', () => {
    expect(makesInternalPlace(new Change(8, [1, 4]))).toBe(true);
    expect(makesInternalPlace(new Change(8, [3, 4]))).toBe(true);
  });
});

describe('isLittle', () => {
  it('finds a treble that never reaches the back', () => {
    expect(isLittle(METHODS.littleBobMajor, 1)).toBe(true);
  });

  it('does not call an ordinary method little', () => {
    expect(isLittle(METHODS.plainBobMajor, 1)).toBe(false);
    expect(isLittle(METHODS.cambridgeMajor, 1)).toBe(false);
  });
});

describe('isDifferential', () => {
  it('is false when every working bell rings the same line', () => {
    expect(isDifferential(METHODS.plainBobMinor)).toBe(false);
    expect(isDifferential(METHODS.cambridgeMajor)).toBe(false);
    expect(isDifferential(METHODS.grandsireDoubles)).toBe(false);
  });

  it('is true when the working bells fall into cycles of different lengths', () => {
    expect(isDifferential(differential())).toBe(true);
  });

  it('is false when there are no working bells at all', () => {
    expect(isDifferential(everyBellHunts())).toBe(false);
  });
});

describe('baseClass', () => {
  it('calls a plain method with dodges a Bob method', () => {
    expect(baseClass(METHODS.plainBobMinor)).toBe('Bob');
    expect(baseClass(METHODS.plainBobMajor)).toBe('Bob');
    expect(baseClass(METHODS.grandsireDoubles)).toBe('Bob');
    expect(baseClass(METHODS.doubleNorwich)).toBe('Bob');
  });

  it('calls a plain method with no dodges a Place method', () => {
    expect(baseClass(everyBellHunts())).toBe('Place');
  });

  it('calls a treble bob method with a place at every cross section Surprise', () => {
    expect(baseClass(METHODS.cambridgeMinor)).toBe('Surprise');
    expect(baseClass(METHODS.cambridgeMajor)).toBe('Surprise');
    expect(baseClass(METHODS.yorkshireMajor)).toBe('Surprise');
    expect(baseClass(METHODS.bristolMajor)).toBe('Surprise');
  });

  it('calls a treble bob method with no internal places Treble Bob', () => {
    expect(baseClass(METHODS.kentMajor)).toBe('Treble Bob');
  });

  it('calls a treble bob method with some internal places Delight', () => {
    expect(baseClass(delight())).toBe('Delight');
  });

  it('calls a method with no hunt bell a Principle', () => {
    expect(baseClass(principle())).toBe('Principle');
  });

  it('refuses something that is not a method', () => {
    expect(() => baseClass('Plain Bob')).toThrow(/a Method is needed/);
  });
});

describe('classify', () => {
  it('reports the class and the evidence for it', () => {
    expect(classify(METHODS.cambridgeMajor)).toEqual({
      className: 'Surprise',
      base: 'Surprise',
      little: false,
      differential: false,
      huntBells: [1],
      huntPath: 'treble bob',
    });
  });

  it('puts Little in front of the class name', () => {
    expect(classify(METHODS.littleBobMajor).className).toBe('Little Bob');
  });

  it('puts Differential in front of the class name', () => {
    expect(classify(differential()).className).toBe('Differential Alliance');
  });

  it('reports no hunt path for a principle', () => {
    const answer = classify(principle());
    expect(answer.className).toBe('Principle');
    expect(answer.huntPath).toBeNull();
    expect(answer.huntBells).toEqual([]);
  });

  it('lists both hunt bells of grandsire', () => {
    expect(classify(METHODS.grandsireDoubles).huntBells).toEqual([1, 2]);
  });
});
