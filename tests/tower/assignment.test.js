import { describe, it, expect } from 'vitest';
import { assignBells, ringingBells, bellsOpenTo } from '../../src/tower/assignment.js';
import { Tower } from '../../src/tower/tower.js';
import { Bell } from '../../src/tower/bell.js';
import { ringer } from '../../src/tower/ringer.js';
import { CompetencyRegister } from '../../src/tower/competency.js';
import { ValidationError } from '../../src/errors.js';

const TITLE = 'Plain Bob Minor';

/**
 * A six bell tower where the bells get steadily heavier.
 */
const tower = () =>
  new Tower({
    name: 'Test',
    bells: [1, 2, 3, 4, 5, 6].map((number) => new Bell({ number, weightPounds: 200 * number })),
  });

const band = () => [
  ringer('alice', { id: 'alice', roles: ['conductor'] }),
  ringer('bob', { id: 'bob' }),
  ringer('carol', { id: 'carol' }),
  ringer('dan', { id: 'dan' }),
  ringer('erin', { id: 'erin' }),
  ringer('frank', { id: 'frank' }),
];

describe('ringingBells', () => {
  it('lists the bells an even stage needs', () => {
    expect(ringingBells({ tower: tower(), band: band(), stage: 6 })).toEqual([1, 2, 3, 4, 5, 6]);
  });

  it('adds a cover for an odd stage when there is a bell spare', () => {
    expect(ringingBells({ tower: tower(), band: band(), stage: 5 })).toEqual([1, 2, 3, 4, 5, 6]);
  });

  it('rings an odd stage without a cover when there is no bell spare', () => {
    const five = new Tower({
      name: 'Test',
      bells: [1, 2, 3, 4, 5].map((number) => new Bell({ number, weightPounds: 200 * number })),
    });
    expect(ringingBells({ tower: five, band: band(), stage: 5 })).toEqual([1, 2, 3, 4, 5]);
  });

  it('refuses a stage the tower cannot ring', () => {
    expect(() => ringingBells({ tower: tower(), band: band(), stage: 8 })).toThrow(
      /cannot ring 8 bells/,
    );
  });

  it('refuses a request it cannot use', () => {
    expect(() => ringingBells(null)).toThrow(/needs a request object/);
    expect(() => ringingBells({ tower: 'Test', band: band(), stage: 6 })).toThrow(/needs a Tower/);
    expect(() => ringingBells({ tower: tower(), band: [], stage: 6 })).toThrow(/needs a band/);
    expect(() => ringingBells({ tower: tower(), band: ['alice'], stage: 6 })).toThrow(
      /must be a Ringer/,
    );
  });
});

describe('bellsOpenTo', () => {
  it('opens every bell to a ringer with no limits', () => {
    const request = { tower: tower(), band: band(), stage: 6 };
    expect(bellsOpenTo(request, ringer('alice', { id: 'alice' }))).toEqual([1, 2, 3, 4, 5, 6]);
  });

  it('closes the heavy bells to a ringer who cannot take them', () => {
    const request = { tower: tower(), band: band(), stage: 6 };
    const member = ringer('bob', { id: 'bob', heaviestBellPounds: 600 });
    expect(bellsOpenTo(request, member)).toEqual([1, 2, 3]);
  });

  it('closes every bell to a ringer who cannot ring the method', () => {
    const competency = new CompetencyRegister().record('alice', TITLE, 'confident');
    const request = { tower: tower(), band: band(), stage: 6, title: TITLE, competency };
    expect(bellsOpenTo(request, ringer('bob', { id: 'bob' }))).toEqual([]);
    expect(bellsOpenTo(request, ringer('alice', { id: 'alice' }))).toHaveLength(6);
  });
});

describe('assignBells', () => {
  it('puts a band with no limits on the bells', () => {
    const { assignment } = assignBells({ tower: tower(), band: band(), stage: 6 });
    expect(assignment.size).toBe(6);
    expect(new Set(assignment.values()).size).toBe(6);
  });

  it('keeps the bells in order', () => {
    const { assignment } = assignBells({ tower: tower(), band: band(), stage: 6 });
    expect([...assignment.keys()]).toEqual([1, 2, 3, 4, 5, 6]);
  });

  it('respects the weight a ringer can take', () => {
    const limited = band().map((member) =>
      member.id === 'frank' ? ringer('frank', { id: 'frank', heaviestBellPounds: 250 }) : member,
    );
    const { assignment } = assignBells({ tower: tower(), band: limited, stage: 6 });
    expect(assignment.get(1)).toBe('frank');
  });

  it('backtracks when a greedy choice would fail', () => {
    const tight = [
      ringer('a', { id: 'a', heaviestBellPounds: 1200 }),
      ringer('b', { id: 'b', heaviestBellPounds: 1200 }),
      ringer('c', { id: 'c', heaviestBellPounds: 1200 }),
      ringer('d', { id: 'd', heaviestBellPounds: 1200 }),
      ringer('e', { id: 'e', heaviestBellPounds: 1200 }),
      ringer('f', { id: 'f' }),
    ];
    const { assignment } = assignBells({ tower: tower(), band: tight, stage: 6 });
    expect(assignment.get(6)).toBe('f');
  });

  it('names a conductor who is on the bells', () => {
    const { conductor } = assignBells({ tower: tower(), band: band(), stage: 6 });
    expect(conductor).toBe('alice');
  });

  it('reports no conductor when nobody conducts', () => {
    const quiet = band().map((member) => ringer(member.name, { id: member.id }));
    expect(assignBells({ tower: tower(), band: quiet, stage: 6 }).conductor).toBeNull();
  });

  it('refuses when a conductor is required and there is none', () => {
    const quiet = band().map((member) => ringer(member.name, { id: member.id }));
    expect(() => assignBells({ tower: tower(), band: quiet, stage: 6, conductor: true })).toThrow(
      /nobody on the bells can conduct/,
    );
  });

  it('refuses when there are not enough ringers', () => {
    expect(() => assignBells({ tower: tower(), band: band().slice(0, 3), stage: 6 })).toThrow(
      '6 bells need ringing and only 3 are here',
    );
  });

  it('refuses when nobody can take a bell', () => {
    const light = band().map((member) =>
      ringer(member.name, { id: member.id, heaviestBellPounds: 300 }),
    );
    expect(() => assignBells({ tower: tower(), band: light, stage: 6 })).toThrow(
      'nobody here can take bell 2',
    );
  });

  it('refuses when the band cannot be matched to the bells', () => {
    const members = band();
    const heavyOnly = [
      ringer('a', { id: 'a', heaviestBellPounds: 200 }),
      ringer('b', { id: 'b', heaviestBellPounds: 200 }),
      ...members.slice(2),
    ];
    expect(() => assignBells({ tower: tower(), band: heavyOnly, stage: 6 })).toThrow(
      ValidationError,
    );
  });
});
