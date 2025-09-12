import { describe, it, expect, beforeEach } from 'vitest';
import { Diary } from '../../src/tower/availability.js';
import { interval } from '../../src/tower/interval.js';
import { ValidationError } from '../../src/errors.js';

/** @type {Diary} */
let diary;

beforeEach(() => {
  diary = new Diary();
  diary
    .free('alice', interval(0, 100))
    .free('bob', interval(0, 50))
    .free('bob', interval(60, 100))
    .free('carol', interval(40, 100));
});

describe('writing the diary', () => {
  it('records a stretch of free time', () => {
    expect(diary.availabilityOf('alice').total).toBe(100);
  });

  it('merges two stretches that touch', () => {
    diary.free('alice', interval(100, 150));
    expect(diary.availabilityOf('alice').size).toBe(1);
    expect(diary.availabilityOf('alice').total).toBe(150);
  });

  it('keeps two stretches that do not touch apart', () => {
    expect(diary.availabilityOf('bob').size).toBe(2);
  });

  it('gives an empty set for a ringer who has said nothing', () => {
    expect(diary.availabilityOf('nobody').size).toBe(0);
  });

  it('refuses an entry it cannot use', () => {
    expect(() => diary.free('', interval(0, 10))).toThrow(/a ringer id/);
    expect(() => diary.free('alice', { start: 0, end: 10 })).toThrow(/needs an Interval/);
  });

  it('lists the ringers it knows about', () => {
    expect(diary.ringers()).toEqual(['alice', 'bob', 'carol']);
  });
});

describe('isFree', () => {
  it('needs the whole slot to be covered', () => {
    expect(diary.isFree('alice', interval(10, 20))).toBe(true);
    expect(diary.isFree('bob', interval(45, 65))).toBe(false);
  });

  it('says a ringer who has written nothing down is not free', () => {
    expect(diary.isFree('nobody', interval(0, 1))).toBe(false);
  });
});

describe('whoIsFree', () => {
  it('lists the band who could come, in order', () => {
    expect(diary.whoIsFree(interval(70, 80))).toEqual(['alice', 'bob', 'carol']);
  });

  it('leaves out anybody with a clash', () => {
    expect(diary.whoIsFree(interval(10, 20))).toEqual(['alice', 'bob']);
    expect(diary.whoIsFree(interval(50, 60))).toEqual(['alice', 'carol']);
  });

  it('counts them', () => {
    expect(diary.countFree(interval(70, 80))).toBe(3);
    expect(diary.countFree(interval(200, 210))).toBe(0);
  });
});

describe('commonTime', () => {
  it('finds when everybody named is free at once', () => {
    expect(diary.commonTime(['alice', 'carol']).toString()).toBe('[40, 100)');
  });

  it('breaks the answer up when somebody has a gap', () => {
    expect(diary.commonTime(['alice', 'bob']).toString()).toBe('[0, 50) [60, 100)');
  });

  it('gives one ringer their own diary back', () => {
    expect(diary.commonTime(['bob']).total).toBe(90);
  });

  it('gives nothing when somebody named has said nothing', () => {
    expect(diary.commonTime(['alice', 'nobody']).size).toBe(0);
  });

  it('refuses to answer for nobody', () => {
    expect(() => diary.commonTime([])).toThrow(/naming nobody/);
    expect(() => diary.commonTime(null)).toThrow(ValidationError);
  });
});

describe('bestSlot', () => {
  const candidates = () => [interval(10, 20), interval(50, 60), interval(70, 80)];

  it('picks the slot the most of the band can make', () => {
    const best = diary.bestSlot(candidates());
    expect(best.slot.start).toBe(70);
    expect(best.band).toEqual(['alice', 'bob', 'carol']);
  });

  it('breaks a tie in favour of the earlier slot', () => {
    const best = diary.bestSlot([interval(10, 20), interval(50, 60)]);
    expect(best.slot.start).toBe(10);
  });

  it('ignores a slot with too small a band', () => {
    expect(diary.bestSlot(candidates(), 3).slot.start).toBe(70);
  });

  it('gives null when nothing is good enough', () => {
    expect(diary.bestSlot(candidates(), 4)).toBeNull();
    expect(diary.bestSlot([])).toBeNull();
  });

  it('refuses something that is not a list of slots', () => {
    expect(() => diary.bestSlot(null)).toThrow(/list of candidate slots/);
  });
});
