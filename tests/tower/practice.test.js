import { describe, it, expect } from 'vitest';
import { readyFor, candidates, planPractice, describeItem } from '../../src/tower/practice.js';
import { Tower } from '../../src/tower/tower.js';
import { Bell } from '../../src/tower/bell.js';
import { ringer } from '../../src/tower/ringer.js';
import { CompetencyRegister } from '../../src/tower/competency.js';
import { ValidationError } from '../../src/errors.js';

const ROUNDS = 'Plain Bob Minor';
const HARD = 'Cambridge Surprise Minor';

const tower = () =>
  new Tower({
    name: 'Test',
    bells: [1, 2, 3, 4, 5, 6].map((number) => new Bell({ number, weightPounds: 200 * number })),
  });

const names = ['alice', 'bob', 'carol', 'dan', 'erin', 'frank'];
const present = () =>
  names.map((name) => ringer(name, { id: name, roles: name === 'alice' ? ['conductor'] : [] }));

function register() {
  const competency = new CompetencyRegister();
  for (const name of names) {
    competency.record(name, ROUNDS, 'confident');
  }
  competency.record('alice', HARD, 'conducts');
  competency.record('bob', HARD, 'learning');
  return competency;
}

describe('readyFor', () => {
  it('counts the band who are up to a method', () => {
    expect(readyFor(register(), present(), ROUNDS, 'inside')).toBe(6);
    expect(readyFor(register(), present(), HARD, 'inside')).toBe(1);
  });

  it('counts more at a lower level', () => {
    expect(readyFor(register(), present(), HARD, 'learning')).toBe(2);
  });
});

describe('candidates', () => {
  it('lists what the band could ring, best first', () => {
    const found = candidates({ tower: tower(), present: present(), competency: register() });
    expect(found[0].title).toBe(ROUNDS);
    expect(found[0].ready).toBe(6);
  });

  it('includes a method only one of them can ring', () => {
    const found = candidates({ tower: tower(), present: present(), competency: register() });
    expect(found.map((entry) => entry.title)).toContain(HARD);
  });

  it('leaves out a method the tower cannot ring', () => {
    const competency = register().record('alice', 'Cambridge Surprise Major', 'conducts');
    const found = candidates({ tower: tower(), present: present(), competency });
    expect(found.map((entry) => entry.title)).not.toContain('Cambridge Surprise Major');
  });

  it('leaves out a title it cannot read', () => {
    const competency = register().record('alice', 'not a title', 'conducts');
    const found = candidates({ tower: tower(), present: present(), competency });
    expect(found.map((entry) => entry.title)).not.toContain('not a title');
  });

  it('refuses a setting it cannot use', () => {
    expect(() => candidates(null)).toThrow(/needs a setting/);
    expect(() => candidates({ tower: 'Test', present: present(), competency: register() })).toThrow(
      /needs a Tower/,
    );
    expect(() => candidates({ tower: tower(), present: [], competency: register() })).toThrow(
      /somebody to turn up/,
    );
    expect(() => candidates({ tower: tower(), present: present(), competency: {} })).toThrow(
      /what the band can ring/,
    );
  });
});

describe('planPractice', () => {
  it('fills the evening with what the band can ring', () => {
    const plan = planPractice({ tower: tower(), present: present(), competency: register() });
    expect(plan.length).toBeGreaterThan(0);
    expect(plan[0].title).toBe(ROUNDS);
    expect(plan[0].purpose).toBe('repertoire');
  });

  it('puts everybody on a bell', () => {
    const plan = planPractice({ tower: tower(), present: present(), competency: register() });
    expect(plan[0].assignment.size).toBe(6);
    expect(new Set(plan[0].band).size).toBe(6);
  });

  it('names the conductor', () => {
    const plan = planPractice({ tower: tower(), present: present(), competency: register() });
    expect(plan[0].conductor).toBe('alice');
  });

  it('leaves out a method the band cannot fill the bells for', () => {
    const plan = planPractice({ tower: tower(), present: present(), competency: register() });
    expect(plan.map((item) => item.title)).not.toContain(HARD);
  });

  it('includes a stretch when enough of the band are learning it', () => {
    const competency = register();
    for (const name of names) {
      competency.record(name, HARD, 'learning');
    }
    const plan = planPractice({ tower: tower(), present: present(), competency });
    const stretch = plan.find((item) => item.purpose === 'stretch');
    expect(stretch.title).toBe(HARD);
  });

  it('rings only as many stretches as it was told to', () => {
    const competency = register();
    for (const name of names) {
      competency.record(name, HARD, 'learning');
      competency.record(name, 'London Surprise Minor', 'learning');
    }
    const plan = planPractice({ tower: tower(), present: present(), competency, stretches: 1 });
    expect(plan.filter((item) => item.purpose === 'stretch')).toHaveLength(1);
  });

  it('stops at the number of slots', () => {
    const competency = register();
    for (const name of names) {
      competency.record(name, HARD, 'confident');
    }
    const plan = planPractice({ tower: tower(), present: present(), competency, slots: 1 });
    expect(plan).toHaveLength(1);
  });

  it('refuses a practice with no slots', () => {
    expect(() =>
      planPractice({ tower: tower(), present: present(), competency: register(), slots: 0 }),
    ).toThrow(ValidationError);
  });
});

describe('describeItem', () => {
  it('reads the plan out', () => {
    const plan = planPractice({ tower: tower(), present: present(), competency: register() });
    const line = describeItem(plan[0]);
    expect(line).toContain('Plain Bob Minor (repertoire)');
    expect(line).toContain('conducted by alice');
  });

  it('says when nobody is conducting', () => {
    const plan = planPractice({
      tower: tower(),
      present: names.map((name) => ringer(name, { id: name })),
      competency: register(),
    });
    expect(describeItem(plan[0])).toContain('nobody conducting');
  });
});
