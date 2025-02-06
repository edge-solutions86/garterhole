import { describe, it, expect } from 'vitest';
import {
  leadsInCourse,
  courseLength,
  courseLeadHeads,
  plainCourse,
  isTrueInThePlain,
  leadHeadMovement,
} from '../../src/method/plainCourse.js';
import { method } from '../../src/method/method.js';
import { parseRow } from '../../src/core/rowParse.js';

const plainBobMinor = () => method('Plain Bob', 6, 'x1x1x1,2');
const plainBobMajor = () => method('Plain Bob', 8, 'x18x18x18x18,12');
const cambridgeMinor = () => method('Cambridge Surprise', 6, 'x36x14x12x36x14x56,12');
const bristolMajor = () => method('Bristol Surprise', 8, 'x58x14.58x58.36.14x14.58x14x18,18');

describe('leadsInCourse', () => {
  it('counts five leads for plain bob minor', () => {
    expect(leadsInCourse(plainBobMinor())).toBe(5);
  });

  it('counts seven leads for plain bob major', () => {
    expect(leadsInCourse(plainBobMajor())).toBe(7);
    expect(leadsInCourse(bristolMajor())).toBe(7);
  });

  it('counts five leads for cambridge surprise minor', () => {
    expect(leadsInCourse(cambridgeMinor())).toBe(5);
  });

  it('refuses something that is not a method', () => {
    expect(() => leadsInCourse('Plain Bob')).toThrow(/a Method is needed/);
  });
});

describe('courseLength', () => {
  it('multiplies the lead length by the number of leads', () => {
    expect(courseLength(plainBobMinor())).toBe(60);
    expect(courseLength(cambridgeMinor())).toBe(120);
    expect(courseLength(plainBobMajor())).toBe(112);
    expect(courseLength(bristolMajor())).toBe(224);
  });
});

describe('courseLeadHeads', () => {
  it('starts and finishes on rounds', () => {
    const heads = courseLeadHeads(plainBobMinor());
    expect(heads).toHaveLength(6);
    expect(heads[0].isRounds()).toBe(true);
    expect(heads[heads.length - 1].isRounds()).toBe(true);
  });

  it('gives the plain bob minor lead heads in order', () => {
    expect(courseLeadHeads(plainBobMinor()).map(String)).toEqual([
      '123456',
      '135264',
      '156342',
      '164523',
      '142635',
      '123456',
    ]);
  });

  it('starts from any row it is given', () => {
    const heads = courseLeadHeads(plainBobMinor(), parseRow('135264'));
    expect(heads[0].toString()).toBe('135264');
    expect(heads[heads.length - 1].toString()).toBe('135264');
  });
});

describe('plainCourse', () => {
  it('rings the whole course and comes round', () => {
    const course = plainCourse(plainBobMinor());
    expect(course.changeCount).toBe(60);
    expect(course.comesRound()).toBe(true);
  });

  it('rings a surprise course', () => {
    const course = plainCourse(cambridgeMinor());
    expect(course.changeCount).toBe(120);
    expect(course.comesRound()).toBe(true);
  });

  it('holds every row once apart from the repeat of rounds', () => {
    const course = plainCourse(plainBobMinor());
    const keys = new Set(course.rows.slice(0, -1).map((row) => row.key()));
    expect(keys.size).toBe(60);
  });
});

describe('isTrueInThePlain', () => {
  it('finds the standard methods true', () => {
    expect(isTrueInThePlain(plainBobMinor())).toBe(true);
    expect(isTrueInThePlain(cambridgeMinor())).toBe(true);
    expect(isTrueInThePlain(bristolMajor())).toBe(true);
  });

  it('finds a method that repeats a row false', () => {
    expect(isTrueInThePlain(method('Repeats', 6, 'x16x16x16x16x16x16x16'))).toBe(false);
  });
});

describe('leadHeadMovement', () => {
  it('reports where each bell goes over a lead', () => {
    const movement = leadHeadMovement(plainBobMinor());
    expect(movement).toHaveLength(6);
    expect(movement[0]).toEqual({ bell: 1, from: 1, to: 1 });
    expect(movement[1]).toEqual({ bell: 2, from: 2, to: 4 });
  });

  it('leaves the hunt bell where it was', () => {
    for (const entry of leadHeadMovement(cambridgeMinor())) {
      if (entry.bell === 1) {
        expect(entry.to).toBe(1);
      }
    }
  });
});
