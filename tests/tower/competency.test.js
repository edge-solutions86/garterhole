import { describe, it, expect, beforeEach } from 'vitest';
import { CompetencyRegister, LEVELS, rankOf, meets } from '../../src/tower/competency.js';
import { ValidationError } from '../../src/errors.js';

const CAMBRIDGE = 'Cambridge Surprise Major';
const PLAIN_BOB = 'Plain Bob Major';

/** @type {CompetencyRegister} */
let register;

beforeEach(() => {
  register = new CompetencyRegister();
  register
    .record('alice', CAMBRIDGE, 'conducts')
    .record('alice', PLAIN_BOB, 'conducts')
    .record('bob', PLAIN_BOB, 'confident')
    .record('carol', PLAIN_BOB, 'learning')
    .record('carol', CAMBRIDGE, 'learning');
});

describe('levels', () => {
  it('ranks them from least to most able', () => {
    expect(LEVELS).toEqual(['learning', 'inside', 'confident', 'conducts']);
    expect(rankOf('learning')).toBeLessThan(rankOf('conducts'));
  });

  it('refuses a level nobody uses', () => {
    expect(() => rankOf('expert')).toThrow(/is not a level of competency/);
  });

  it('compares two levels', () => {
    expect(meets('confident', 'inside')).toBe(true);
    expect(meets('inside', 'inside')).toBe(true);
    expect(meets('learning', 'inside')).toBe(false);
  });
});

describe('recording competency', () => {
  it('writes a level down', () => {
    expect(register.levelOf('bob', PLAIN_BOB)).toBe('confident');
  });

  it('gives null for a method a ringer has never rung', () => {
    expect(register.levelOf('bob', CAMBRIDGE)).toBeNull();
    expect(register.levelOf('nobody', PLAIN_BOB)).toBeNull();
  });

  it('replaces an earlier record', () => {
    register.record('bob', PLAIN_BOB, 'conducts');
    expect(register.levelOf('bob', PLAIN_BOB)).toBe('conducts');
  });

  it('refuses a record it cannot use', () => {
    expect(() => register.record('', PLAIN_BOB, 'inside')).toThrow(/a ringer id/);
    expect(() => register.record('bob', '', 'inside')).toThrow(/a method title/);
    expect(() => register.record('bob', PLAIN_BOB, 'expert')).toThrow(ValidationError);
  });
});

describe('forgetting', () => {
  it('takes a method out', () => {
    expect(register.forget('bob', PLAIN_BOB)).toBe(true);
    expect(register.levelOf('bob', PLAIN_BOB)).toBeNull();
  });

  it('says so when there was nothing to forget', () => {
    expect(register.forget('bob', CAMBRIDGE)).toBe(false);
    expect(register.forget('nobody', PLAIN_BOB)).toBe(false);
  });
});

describe('canRing', () => {
  it('accepts a ringer at or above the level asked for', () => {
    expect(register.canRing('bob', PLAIN_BOB)).toBe(true);
    expect(register.canRing('bob', PLAIN_BOB, 'confident')).toBe(true);
  });

  it('rejects a ringer below it', () => {
    expect(register.canRing('carol', PLAIN_BOB)).toBe(false);
    expect(register.canRing('bob', PLAIN_BOB, 'conducts')).toBe(false);
  });

  it('rejects a ringer who has never rung it', () => {
    expect(register.canRing('bob', CAMBRIDGE)).toBe(false);
  });
});

describe('repertoireOf', () => {
  it('lists what a ringer can ring in title order', () => {
    expect(register.repertoireOf('alice')).toEqual([
      { title: CAMBRIDGE, level: 'conducts' },
      { title: PLAIN_BOB, level: 'conducts' },
    ]);
  });

  it('gives nothing for a ringer with no record', () => {
    expect(register.repertoireOf('nobody')).toEqual([]);
  });
});

describe('whoCanRing', () => {
  it('lists the band members who are up to it', () => {
    expect(register.whoCanRing(PLAIN_BOB)).toEqual(['alice', 'bob']);
  });

  it('takes the level into account', () => {
    expect(register.whoCanRing(PLAIN_BOB, 'learning')).toEqual(['alice', 'bob', 'carol']);
    expect(register.whoCanRing(PLAIN_BOB, 'conducts')).toEqual(['alice']);
  });

  it('gives nobody for a method nobody rings', () => {
    expect(register.whoCanRing('Bristol Surprise Major')).toEqual([]);
  });
});

describe('coverage', () => {
  it('counts how many can ring each method, most first', () => {
    expect([...register.coverage().entries()]).toEqual([
      [PLAIN_BOB, 2],
      [CAMBRIDGE, 1],
    ]);
  });

  it('counts more at a lower level', () => {
    expect(register.coverage('learning').get(CAMBRIDGE)).toBe(2);
  });

  it('counts nothing for an empty register', () => {
    expect(new CompetencyRegister().coverage().size).toBe(0);
  });
});

describe('titles', () => {
  it('lists every method anybody has rung', () => {
    expect(register.titles()).toEqual([CAMBRIDGE, PLAIN_BOB]);
  });

  it('lists nothing for an empty register', () => {
    expect(new CompetencyRegister().titles()).toEqual([]);
  });
});
