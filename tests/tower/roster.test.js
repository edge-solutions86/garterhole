import { describe, it, expect, beforeEach } from 'vitest';
import { Roster } from '../../src/tower/roster.js';
import { ValidationError } from '../../src/errors.js';

const day = (month, date) => new Date(Date.UTC(2025, month - 1, date));

/** @type {Roster} */
let roster;

beforeEach(() => {
  roster = new Roster();
  roster
    .attended('alice', day(1, 7))
    .attended('alice', day(1, 14))
    .attended('alice', day(1, 21))
    .attended('bob', day(1, 7))
    .attended('bob', day(1, 14))
    .attended('carol', day(1, 7));
});

describe('recording attendance', () => {
  it('counts the days somebody came', () => {
    expect(roster.countFor('alice')).toBe(3);
    expect(roster.countFor('carol')).toBe(1);
  });

  it('counts nobody as never having been', () => {
    expect(roster.countFor('nobody')).toBe(0);
    expect(roster.lastSeen('nobody')).toBeNull();
  });

  it('counts one day once however many times it is recorded', () => {
    roster.attended('carol', day(1, 7));
    roster.attended('carol', new Date(Date.UTC(2025, 0, 7, 19, 30)));
    expect(roster.countFor('carol')).toBe(1);
  });

  it('refuses a record it cannot use', () => {
    expect(() => roster.attended('', day(1, 7))).toThrow(/a ringer id/);
    expect(() => roster.attended('alice', '2025-01-07')).toThrow(/needs the date/);
    expect(() => roster.attended('alice', new Date('nonsense'))).toThrow(ValidationError);
  });
});

describe('reading the roster', () => {
  it('lists the days somebody came, earliest first', () => {
    expect(roster.daysFor('bob')).toEqual(['2025-01-07', '2025-01-14']);
  });

  it('gives the last day somebody came', () => {
    expect(roster.lastSeen('alice')).toBe('2025-01-21');
  });

  it('says who came on a day', () => {
    expect(roster.whoCame(day(1, 7))).toEqual(['alice', 'bob', 'carol']);
    expect(roster.whoCame(day(1, 21))).toEqual(['alice']);
  });

  it('says nobody came on a day nobody came', () => {
    expect(roster.whoCame(day(2, 1))).toEqual([]);
  });

  it('lists every day anybody came', () => {
    expect(roster.days()).toEqual(['2025-01-07', '2025-01-14', '2025-01-21']);
  });
});

describe('attendance', () => {
  it('lists everybody, most regular first', () => {
    expect(roster.attendance().map((line) => line.ringerId)).toEqual(['alice', 'bob', 'carol']);
  });

  it('gives each of them their count and their last day', () => {
    expect(roster.attendance()[0]).toEqual({
      ringerId: 'alice',
      attended: 3,
      lastSeen: '2025-01-21',
    });
  });

  it('breaks a tie by name', () => {
    const other = new Roster().attended('zoe', day(1, 7)).attended('adam', day(1, 7));
    expect(other.attendance().map((line) => line.ringerId)).toEqual(['adam', 'zoe']);
  });

  it('lists nobody for an empty roster', () => {
    expect(new Roster().attendance()).toEqual([]);
  });
});

describe('missingSince', () => {
  it('lists who has not been since a date', () => {
    expect(roster.missingSince(day(1, 20)).map((line) => line.ringerId)).toEqual(['bob', 'carol']);
  });

  it('lists nobody when everybody has been', () => {
    expect(roster.missingSince(day(1, 1))).toEqual([]);
  });

  it('gives their last day so somebody knows how long it has been', () => {
    expect(roster.missingSince(day(1, 20))[0].lastSeen).toBe('2025-01-14');
  });

  it('refuses something that is not a date', () => {
    expect(() => roster.missingSince('2025-01-20')).toThrow(ValidationError);
  });
});
