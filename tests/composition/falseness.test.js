import { describe, it, expect } from 'vitest';
import {
  courseHeadBetween,
  falseCourseHeads,
  tenorsTogetherFalseCourseHeads,
  isFalseAgainstPlainCourse,
  sharedRows,
  trueCourseHeads,
  falseCourseHeadGroups,
} from '../../src/composition/falseness.js';
import { method } from '../../src/method/method.js';
import { plainCourse, courseLeadHeads } from '../../src/method/plainCourse.js';
import { transpose } from '../../src/core/transposition.js';
import { parseRow, parseRows } from '../../src/core/rowParse.js';
import { rounds } from '../../src/core/row.js';
import { ValidationError } from '../../src/errors.js';

const plainBobMinor = () => method('Plain', 6, 'x1x1x1,2');
const cambridgeMinor = () => method('Cambridge', 6, 'x36x14x12x36x14x56,12');
const bristolMajor = () => method('Bristol', 8, 'x58x14.58x58.36.14x14.58x14x18,18');

describe('courseHeadBetween', () => {
  it('gives the head that takes one row to another', () => {
    const from = parseRow('13527486');
    const to = parseRow('15738264');
    const head = courseHeadBetween(from, to);
    expect(transpose(head, from).equals(to)).toBe(true);
  });

  it('gives rounds for a row and itself', () => {
    const row = parseRow('13527486');
    expect(courseHeadBetween(row, row).isRounds()).toBe(true);
  });

  it('is the row itself when the course starts at rounds', () => {
    const row = parseRow('13527486');
    expect(courseHeadBetween(rounds(8), row).equals(row)).toBe(true);
  });
});

describe('falseCourseHeads', () => {
  it('finds the false course heads of plain bob minor', () => {
    expect(falseCourseHeads(plainBobMinor()).map(String)).toEqual([
      '124365',
      '132546',
      '135264',
      '142635',
      '146253',
      '153624',
      '156342',
      '164523',
      '165432',
    ]);
  });

  it('leaves rounds out', () => {
    expect(falseCourseHeads(cambridgeMinor()).some((head) => head.isRounds())).toBe(false);
  });

  it('keeps only heads that leave the treble at the front', () => {
    for (const head of falseCourseHeads(cambridgeMinor())) {
      expect(head.bellAt(1)).toBe(1);
    }
  });

  it('finds fewer for a method that is designed to be clean', () => {
    expect(tenorsTogetherFalseCourseHeads(bristolMajor())).toHaveLength(4);
  });

  it('refuses something that is not a method', () => {
    expect(() => falseCourseHeads('Plain Bob')).toThrow(ValidationError);
  });
});

describe('tenorsTogetherFalseCourseHeads', () => {
  it('keeps only the heads that leave the tenors alone', () => {
    for (const head of tenorsTogetherFalseCourseHeads(bristolMajor())) {
      expect(head.bellAt(7)).toBe(7);
      expect(head.bellAt(8)).toBe(8);
    }
  });

  it('finds none in plain bob minor', () => {
    expect(tenorsTogetherFalseCourseHeads(plainBobMinor())).toEqual([]);
  });

  it('takes how many tenors to hold', () => {
    const three = tenorsTogetherFalseCourseHeads(bristolMajor(), 3);
    expect(three.length).toBeLessThanOrEqual(4);
  });
});

describe('isFalseAgainstPlainCourse', () => {
  it('recognises a head from the list', () => {
    const head = falseCourseHeads(plainBobMinor())[0];
    expect(isFalseAgainstPlainCourse(plainBobMinor(), head)).toBe(true);
  });

  it('does not condemn rounds', () => {
    expect(isFalseAgainstPlainCourse(plainBobMinor(), rounds(6))).toBe(false);
  });
});

describe('sharedRows', () => {
  it('finds the rows two blocks have in common', () => {
    expect(sharedRows(parseRows('1234 2143'), parseRows('2143 2413'))).toEqual(['2143']);
  });

  it('finds nothing when the blocks are disjoint', () => {
    expect(sharedRows(parseRows('1234'), parseRows('2143'))).toEqual([]);
  });

  it('reports a row once however many times it appears', () => {
    expect(sharedRows(parseRows('1234 1234'), parseRows('1234 1234'))).toEqual(['1234']);
  });

  it('finds the plain course shares everything with itself', () => {
    const course = plainCourse(plainBobMinor()).rows;
    expect(sharedRows(course, course)).toHaveLength(60);
  });
});

describe('trueCourseHeads', () => {
  it('drops the heads that clash', () => {
    const kept = trueCourseHeads(plainBobMinor(), courseLeadHeads(plainBobMinor()));
    expect(kept.every((head) => head.isRounds())).toBe(true);
  });

  it('keeps a head that is not on the list', () => {
    const kept = trueCourseHeads(bristolMajor(), [parseRow('12345678')]);
    expect(kept).toHaveLength(1);
  });
});

describe('falseCourseHeadGroups', () => {
  it('groups the heads by the shape of the transposition', () => {
    const groups = falseCourseHeadGroups(plainBobMinor());
    expect(groups.size).toBeGreaterThan(0);
    expect(groups.size).toBeLessThan(falseCourseHeads(plainBobMinor()).length);
  });

  it('keys the groups on the cycle lengths', () => {
    for (const key of falseCourseHeadGroups(plainBobMinor()).keys()) {
      expect(key).toMatch(/^[0-9]+(\.[0-9]+)*$/);
    }
  });

  it('holds every head somewhere', () => {
    const groups = falseCourseHeadGroups(cambridgeMinor());
    const total = [...groups.values()].reduce((sum, bucket) => sum + bucket.length, 0);
    expect(total).toBe(falseCourseHeads(cambridgeMinor()).length);
  });
});
