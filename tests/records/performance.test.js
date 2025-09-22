import { describe, it, expect } from 'vitest';
import { Performance, performance } from '../../src/records/performance.js';
import { ValidationError } from '../../src/errors.js';

const band = ['Alice Vane', 'Bob Reed', 'Carol Sims', 'Dan Ellis', 'Erin Fox', 'Frank Gale'];

const quarter = () =>
  performance({
    tower: 'St Mary, Bridgend',
    date: new Date(Date.UTC(2025, 4, 18)),
    title: 'Plain Bob Minor',
    changes: 1260,
    band,
    conductor: 'Alice Vane',
  });

describe('Performance', () => {
  it('keeps what it was given', () => {
    const rung = quarter();
    expect(rung.tower).toBe('St Mary, Bridgend');
    expect(rung.title).toBe('Plain Bob Minor');
    expect(rung.changes).toBe(1260);
    expect(rung.band).toEqual(band);
  });

  it('is frozen', () => {
    expect(Object.isFrozen(quarter())).toBe(true);
    expect(Object.isFrozen(quarter().band)).toBe(true);
  });

  it('copies the date it was given', () => {
    const date = new Date(Date.UTC(2025, 4, 18));
    const rung = performance({
      tower: 'Test',
      date,
      title: 'Plain Bob Minor',
      changes: 1260,
      band,
    });
    date.setFullYear(1999);
    expect(rung.dateText).toBe('2025-05-18');
  });

  it('reads the stage out of the title', () => {
    expect(quarter().stage).toBe(6);
  });

  it('says what the length counts as', () => {
    expect(quarter().kind).toBe('quarter peal');
    expect(performance({ ...fields(), changes: 720 }).kind).toBe('touch');
    expect(performance({ ...fields(), changes: 5040 }).kind).toBe('peal');
  });

  it('counts the band', () => {
    expect(quarter().bandSize).toBe(6);
  });

  it('makes an identifier that catches the same performance twice', () => {
    expect(quarter().id).toBe(performance(fields()).id);
    expect(quarter().id).not.toBe(performance({ ...fields(), changes: 1280 }).id);
  });

  it('prints itself', () => {
    expect(quarter().toString()).toBe('St Mary, Bridgend, 2025-05-18: 1260 Plain Bob Minor');
  });
});

/** The fields of the quarter above. */
function fields() {
  return {
    tower: 'St Mary, Bridgend',
    date: new Date(Date.UTC(2025, 4, 18)),
    title: 'Plain Bob Minor',
    changes: 1260,
    band,
    conductor: 'Alice Vane',
  };
}

describe('bad records', () => {
  it('refuses a definition that is not an object', () => {
    expect(() => new Performance('a peal')).toThrow(/needs a definition object/);
  });

  it('refuses a missing tower or title', () => {
    expect(() => performance({ ...fields(), tower: '' })).toThrow(/a tower name/);
    expect(() => performance({ ...fields(), title: '' })).toThrow(/a method title/);
  });

  it('refuses a date that is not a date', () => {
    expect(() => performance({ ...fields(), date: '2025-05-18' })).toThrow(/needs the date/);
    expect(() => performance({ ...fields(), date: new Date('nonsense') })).toThrow(ValidationError);
  });

  it('refuses a length that is not a length', () => {
    expect(() => performance({ ...fields(), changes: 0 })).toThrow(/is not a number of changes/);
  });

  it('refuses an empty band or a nameless ringer', () => {
    expect(() => performance({ ...fields(), band: [] })).toThrow(/needs a band/);
    expect(() => performance({ ...fields(), band: ['Alice', ''] })).toThrow(/the ringer of bell 2/);
  });

  it('refuses a conductor who did not ring', () => {
    expect(() => performance({ ...fields(), conductor: 'Nobody' })).toThrow(
      'the conductor "Nobody" is not in the band',
    );
  });

  it('refuses a duration that is not minutes', () => {
    expect(() => performance({ ...fields(), duration: 0 })).toThrow(/not a length of time/);
  });
});

describe('looking up the band', () => {
  it('names the ringer of a bell', () => {
    expect(quarter().ringerOf(1)).toBe('Alice Vane');
    expect(quarter().ringerOf(6)).toBe('Frank Gale');
  });

  it('refuses a bell nobody rang', () => {
    expect(() => quarter().ringerOf(7)).toThrow(/was not rung in this performance/);
    expect(() => quarter().ringerOf(0)).toThrow(ValidationError);
  });

  it('finds the bell somebody rang', () => {
    expect(quarter().bellOf('Carol Sims')).toBe(3);
  });

  it('gives null for somebody who was not there', () => {
    expect(quarter().bellOf('Nobody')).toBeNull();
  });

  it('records the optional details as null when they are missing', () => {
    const rung = performance({ ...fields(), conductor: undefined, composer: undefined });
    expect(rung.conductor).toBeNull();
    expect(rung.composer).toBeNull();
    expect(rung.duration).toBeNull();
    expect(rung.footnotes).toEqual([]);
  });
});
