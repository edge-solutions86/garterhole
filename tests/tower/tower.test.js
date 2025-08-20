import { describe, it, expect } from 'vitest';
import { Tower } from '../../src/tower/tower.js';
import { Bell, bellWeighing } from '../../src/tower/bell.js';
import { NotFoundError } from '../../src/errors.js';

/**
 * @param {number} count
 */
function ring(count) {
  const bells = [];
  for (let number = 1; number <= count; number += 1) {
    bells.push(new Bell({ number, weightPounds: 300 + number * 200 }));
  }
  return bells;
}

const stMary = () =>
  new Tower({
    name: 'St Mary the Virgin',
    place: 'Bridgend',
    bells: ring(8),
  });

describe('Tower', () => {
  it('keeps the name and the place', () => {
    expect(stMary().name).toBe('St Mary the Virgin');
    expect(stMary().place).toBe('Bridgend');
  });

  it('is frozen', () => {
    expect(Object.isFrozen(stMary())).toBe(true);
    expect(Object.isFrozen(stMary().bells)).toBe(true);
  });

  it('sorts the bells by number', () => {
    const tower = new Tower({ name: 'Test', bells: [...ring(4)].reverse() });
    expect(tower.bells.map((bell) => bell.number)).toEqual([1, 2, 3, 4]);
  });

  it('counts the bells', () => {
    expect(stMary().size).toBe(8);
  });

  it('names the treble and the tenor', () => {
    expect(stMary().treble.number).toBe(1);
    expect(stMary().tenor.number).toBe(8);
  });

  it('adds the ring up', () => {
    expect(new Tower({ name: 'Test', bells: ring(2) }).totalWeightPounds).toBe(500 + 700);
  });

  it('has no place when none was given', () => {
    expect(new Tower({ name: 'Test', bells: ring(6) }).place).toBeNull();
  });

  it('refuses a definition it cannot use', () => {
    expect(() => new Tower('St Mary')).toThrow(/needs a definition object/);
    expect(() => new Tower({ name: '', bells: ring(6) })).toThrow(/a tower name/);
    expect(() => new Tower({ name: 'Test', bells: [] })).toThrow(/at least one bell/);
  });

  it('refuses an entry that is not a bell', () => {
    expect(() => new Tower({ name: 'Test', bells: [{ number: 1 }] })).toThrow(
      /entry 0 of the ring is not a bell/,
    );
  });

  it('refuses a ring with a gap in the numbering', () => {
    const bells = [
      new Bell({ number: 1, weightPounds: 400 }),
      new Bell({ number: 3, weightPounds: 900 }),
    ];
    expect(() => new Tower({ name: 'Test', bells })).toThrow(
      'the ring jumps from bell 1 to bell 3, so it has a gap',
    );
  });
});

describe('looking bells up', () => {
  it('finds a bell by number', () => {
    expect(stMary().bell(3).number).toBe(3);
  });

  it('throws for a bell that is not there', () => {
    expect(() => stMary().bell(9)).toThrow(NotFoundError);
    expect(() => stMary().bell(0)).toThrow(/no bell with id "0"/);
  });
});

describe('what a tower can ring', () => {
  it('rings any even stage up to its size', () => {
    const tower = stMary();
    expect(tower.canRing(8)).toBe(true);
    expect(tower.canRing(6)).toBe(true);
    expect(tower.canRing(10)).toBe(false);
  });

  it('rings an odd stage with or without a cover', () => {
    const six = new Tower({ name: 'Test', bells: ring(6) });
    expect(six.canRing(5)).toBe(true);
    expect(six.canRing(7)).toBe(false);
  });

  it('says when there is a spare bell to cover with', () => {
    const six = new Tower({ name: 'Test', bells: ring(6) });
    expect(six.canCover(5)).toBe(true);
    expect(six.canCover(6)).toBe(false);
    const five = new Tower({ name: 'Test', bells: ring(5) });
    expect(five.canRing(5)).toBe(true);
    expect(five.canCover(5)).toBe(false);
  });

  it('refuses a stage that is not a stage', () => {
    expect(stMary().canRing(2)).toBe(false);
    expect(stMary().canRing(6.5)).toBe(false);
  });

  it('lists the stages largest first', () => {
    expect(new Tower({ name: 'Test', bells: ring(6) }).stages()).toEqual([6, 5, 4, 3]);
  });

  it('names the largest stage it can ring', () => {
    expect(stMary().largestStage).toBe(8);
    expect(new Tower({ name: 'Test', bells: ring(3) }).largestStage).toBe(3);
  });

  it('gives no stages to a tower with too few bells', () => {
    const two = new Tower({ name: 'Test', bells: ring(2) });
    expect(two.stages()).toEqual([]);
    expect(two.largestStage).toBe(0);
  });
});

describe('identifying a tower', () => {
  it('makes an id from the place and the name', () => {
    expect(stMary().id).toBe('bridgend-st-mary-the-virgin');
  });

  it('uses the name alone when there is no place', () => {
    expect(new Tower({ name: 'Test Tower', bells: ring(6) }).id).toBe('test-tower');
  });

  it('prints itself', () => {
    const tower = new Tower({
      name: 'St Mary',
      place: 'Bridgend',
      bells: [bellWeighing(1, '4-0-0'), bellWeighing(2, '23-2-11')],
    });
    expect(tower.toString()).toBe('St Mary, Bridgend: 2 bells, tenor 23-2-11');
  });

  it('describes what it rings', () => {
    expect(stMary().describe()).toContain('ringing up to Major');
  });
});
