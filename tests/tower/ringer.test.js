import { describe, it, expect } from 'vitest';
import { Ringer, ringer, withRole, whoCanHandle, ROLES } from '../../src/tower/ringer.js';
import { bellWeighing } from '../../src/tower/bell.js';
import { ValidationError } from '../../src/errors.js';

const light = () => bellWeighing(1, '4-0-0');
const heavy = () => bellWeighing(8, '23-2-11');

describe('Ringer', () => {
  it('keeps the name and makes an id from it', () => {
    const member = ringer('Alice Vane');
    expect(member.name).toBe('Alice Vane');
    expect(member.id).toBe('alice-vane');
  });

  it('takes an id of its own', () => {
    expect(ringer('Alice Vane', { id: 'av1' }).id).toBe('av1');
  });

  it('is frozen', () => {
    expect(Object.isFrozen(ringer('Alice Vane'))).toBe(true);
    expect(Object.isFrozen(ringer('Alice Vane').roles)).toBe(true);
  });

  it('squashes whitespace in the name', () => {
    expect(ringer('  Alice   Vane ').name).toBe('Alice Vane');
  });

  it('sorts and de-duplicates the roles', () => {
    const member = ringer('Alice Vane', { roles: ['teacher', 'conductor', 'teacher'] });
    expect(member.roles).toEqual(['conductor', 'teacher']);
  });

  it('has no roles and no weight limit by default', () => {
    const member = ringer('Alice Vane');
    expect(member.roles).toEqual([]);
    expect(member.heaviestBellPounds).toBeNull();
  });

  it('refuses a definition it cannot use', () => {
    expect(() => new Ringer('Alice')).toThrow(/needs a definition object/);
    expect(() => ringer('')).toThrow(/a ringer name/);
  });

  it('refuses a weight that is not a weight', () => {
    expect(() => ringer('Alice', { heaviestBellPounds: 0 })).toThrow(
      /is not a bell weight a ringer can handle/,
    );
  });

  it('refuses a role nobody holds in a tower', () => {
    expect(() => ringer('Alice', { roles: ['organist'] })).toThrow(/is not a role in a tower/);
    expect(() => ringer('Alice', { roles: 'conductor' })).toThrow(ValidationError);
  });
});

describe('roles', () => {
  it('says whether a ringer holds one', () => {
    const member = ringer('Alice', { roles: ['conductor'] });
    expect(member.hasRole('conductor')).toBe(true);
    expect(member.hasRole('teacher')).toBe(false);
  });

  it('has a shortcut for conducting', () => {
    expect(ringer('Alice', { roles: ['conductor'] }).conducts).toBe(true);
    expect(ringer('Bob').conducts).toBe(false);
  });

  it('lists the roles a tower has', () => {
    expect(ROLES).toContain('tower captain');
    expect(Object.isFrozen(ROLES)).toBe(true);
  });
});

describe('handling a bell', () => {
  it('lets a ringer with no recorded limit take anything', () => {
    expect(ringer('Alice').canHandle(heavy())).toBe(true);
  });

  it('keeps a ringer off a bell that is too heavy', () => {
    const member = ringer('Bob', { heaviestBellPounds: 900 });
    expect(member.canHandle(light())).toBe(true);
    expect(member.canHandle(heavy())).toBe(false);
  });

  it('allows a bell exactly at the limit', () => {
    const member = ringer('Bob', { heaviestBellPounds: 448 });
    expect(member.canHandle(light())).toBe(true);
  });
});

describe('searching a band', () => {
  const band = () => [
    ringer('Alice', { roles: ['conductor'] }),
    ringer('Bob', { heaviestBellPounds: 500 }),
    ringer('Carol', { roles: ['conductor', 'teacher'] }),
  ];

  it('finds the ringers who hold a role', () => {
    expect(withRole(band(), 'conductor').map((member) => member.name)).toEqual(['Alice', 'Carol']);
  });

  it('finds nobody when nobody holds it', () => {
    expect(withRole(band(), 'steeple keeper')).toEqual([]);
  });

  it('refuses a role that is not one', () => {
    expect(() => withRole(band(), 'organist')).toThrow(/is not a role in a tower/);
  });

  it('finds the ringers who could take a bell', () => {
    expect(whoCanHandle(band(), heavy()).map((member) => member.name)).toEqual(['Alice', 'Carol']);
    expect(whoCanHandle(band(), light())).toHaveLength(3);
  });
});

describe('printing a ringer', () => {
  it('gives the name alone when there are no roles', () => {
    expect(ringer('Alice Vane').toString()).toBe('Alice Vane');
  });

  it('lists the roles after the name', () => {
    expect(ringer('Alice Vane', { roles: ['conductor', 'teacher'] }).toString()).toBe(
      'Alice Vane (conductor, teacher)',
    );
  });
});
