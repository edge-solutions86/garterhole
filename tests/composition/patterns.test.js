import { describe, it, expect } from 'vitest';
import {
  parsePattern,
  matchesPattern,
  countPattern,
  findPattern,
  commonPatterns,
} from '../../src/composition/music/patterns.js';
import { parseRow, parseRows } from '../../src/core/rowParse.js';
import { NotationError, ValidationError } from '../../src/errors.js';

describe('parsePattern', () => {
  it('reads bells, wildcards and runs', () => {
    expect(parsePattern('1?*8')).toEqual([
      { kind: 'bell', bell: 1 },
      { kind: 'any' },
      { kind: 'run' },
      { kind: 'bell', bell: 8 },
    ]);
  });

  it('refuses an empty pattern', () => {
    expect(() => parsePattern('')).toThrow(ValidationError);
    expect(() => parsePattern(null)).toThrow(/must be a non empty string/);
  });

  it('refuses a character it does not know', () => {
    expect(() => parsePattern('*56/8')).toThrow(NotationError);
    expect(() => parsePattern('*56/8')).toThrow('"/" at position 4 is not part of a pattern');
  });
});

describe('matchesPattern', () => {
  it('matches a run of bells at the back', () => {
    expect(matchesPattern(parseRow('42135678'), '*5678')).toBe(true);
    expect(matchesPattern(parseRow('42136578'), '*5678')).toBe(false);
  });

  it('matches a run of bells at the front', () => {
    expect(matchesPattern(parseRow('12346857'), '1234*')).toBe(true);
    expect(matchesPattern(parseRow('21346857'), '1234*')).toBe(false);
  });

  it('matches an exact row with no wildcards', () => {
    expect(matchesPattern(parseRow('12345678'), '12345678')).toBe(true);
    expect(matchesPattern(parseRow('12345678'), '1234567')).toBe(false);
  });

  it('matches a single bell wildcard', () => {
    expect(matchesPattern(parseRow('12345678'), '1?345678')).toBe(true);
    expect(matchesPattern(parseRow('12345678'), '?2345678')).toBe(true);
    expect(matchesPattern(parseRow('12345678'), '1?45678')).toBe(false);
  });

  it('lets a run swallow nothing at all', () => {
    expect(matchesPattern(parseRow('12345678'), '*12345678')).toBe(true);
    expect(matchesPattern(parseRow('12345678'), '12345678*')).toBe(true);
  });

  it('matches a run in the middle', () => {
    expect(matchesPattern(parseRow('12345678'), '12*78')).toBe(true);
    expect(matchesPattern(parseRow('12345678'), '12*76')).toBe(false);
  });

  it('takes a pattern that has already been read', () => {
    const parts = parsePattern('*5678');
    expect(matchesPattern(parseRow('42135678'), parts)).toBe(true);
  });

  it('refuses something that is not a row', () => {
    expect(() => matchesPattern('12345678', '*5678')).toThrow(ValidationError);
  });
});

describe('countPattern', () => {
  const rows = parseRows('42135678 12346857 13527468 87654321');

  it('counts the rows that match', () => {
    expect(countPattern(rows, '*5678')).toBe(1);
    expect(countPattern(rows, '1234*')).toBe(1);
  });

  it('counts nothing when nothing matches', () => {
    expect(countPattern(rows, '*1234')).toBe(0);
  });

  it('counts everything for a pattern that matches anything', () => {
    expect(countPattern(rows, '*')).toBe(4);
  });
});

describe('findPattern', () => {
  const rows = parseRows('42135678 12346857 13527468 87654321');

  it('says where each match was', () => {
    const found = findPattern(rows, '*5678');
    expect(found).toHaveLength(1);
    expect(found[0].index).toBe(0);
    expect(found[0].row.toString()).toBe('42135678');
  });

  it('gives nothing when nothing matches', () => {
    expect(findPattern(rows, '*1234')).toEqual([]);
  });
});

describe('commonPatterns', () => {
  it('names the four bell runs at each end', () => {
    const patterns = commonPatterns(8);
    expect(patterns.get('5678 at the back')).toBe('*5678');
    expect(patterns.get('8765 at the back')).toBe('*8765');
    expect(patterns.get('1234 at the front')).toBe('1234*');
  });

  it('names the tenors together', () => {
    expect(commonPatterns(8).get('tenors together')).toBe('*78');
    expect(commonPatterns(12).get('tenors together')).toBe('*ET');
  });

  it('works at royal', () => {
    expect(commonPatterns(10).get('7890 at the back')).toBe('*7890');
  });

  it('refuses a stage too small to have them', () => {
    expect(() => commonPatterns(3)).toThrow(/no common patterns on 3/);
  });
});
