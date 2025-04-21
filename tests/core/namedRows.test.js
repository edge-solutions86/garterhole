import { describe, it, expect } from 'vitest';
import {
  queens,
  kings,
  tittums,
  backrounds,
  whittingtons,
  namedRows,
  nameOfRow,
} from '../../src/core/namedRows.js';
import { rounds } from '../../src/core/row.js';
import { parseRow } from '../../src/core/rowParse.js';

describe('queens', () => {
  it('puts the odd bells first on major', () => {
    expect(queens(8).toString()).toBe('13572468');
  });

  it('works on maximus', () => {
    expect(queens(12).toString()).toBe('13579E24680T');
  });

  it('uses every bell of an odd stage, since the cover is not one of them', () => {
    expect(queens(5).toString()).toBe('13524');
  });
});

describe('kings', () => {
  it('runs the odd bells down and the even bells up', () => {
    expect(kings(8).toString()).toBe('75312468');
  });

  it('is rounds turned around only in part', () => {
    expect(kings(6).toString()).toBe('531246');
  });
});

describe('tittums', () => {
  it('interleaves the two halves', () => {
    expect(tittums(8).toString()).toBe('15263748');
    expect(tittums(6).toString()).toBe('142536');
  });

  it('interleaves an odd stage as evenly as it can', () => {
    expect(tittums(7).toString()).toBe('1526374');
    expect(tittums(5).toString()).toBe('14253');
  });
});

describe('backrounds', () => {
  it('reverses the changing bells', () => {
    expect(backrounds(8).toString()).toBe('87654321');
  });

  it('reverses an odd stage as well', () => {
    expect(backrounds(5).toString()).toBe('54321');
  });
});

describe('whittingtons', () => {
  it('is the row on eight', () => {
    expect(whittingtons(8).toString()).toBe('12753468');
  });

  it('does not exist at any other stage', () => {
    expect(() => whittingtons(6)).toThrow('Whittingtons is only rung on eight bells');
    expect(() => whittingtons(12)).toThrow(RangeError);
  });
});

describe('namedRows', () => {
  it('lists the rows that have names', () => {
    const named = namedRows(8);
    expect([...named.keys()]).toEqual([
      'Rounds',
      'Backrounds',
      'Queens',
      'Kings',
      'Tittums',
      'Whittingtons',
    ]);
  });

  it('leaves Whittingtons out at other stages', () => {
    expect(namedRows(6).has('Whittingtons')).toBe(false);
  });

  it('gives rows at the stage that was asked for', () => {
    for (const row of namedRows(10).values()) {
      expect(row.stage).toBe(10);
    }
  });

  it('refuses an unsupported stage', () => {
    expect(() => namedRows(2)).toThrow(/stage 2 is not supported/);
  });
});

describe('nameOfRow', () => {
  it('names a row that has one', () => {
    expect(nameOfRow(rounds(8))).toBe('Rounds');
    expect(nameOfRow(parseRow('13572468'))).toBe('Queens');
    expect(nameOfRow(parseRow('87654321'))).toBe('Backrounds');
  });

  it('gives null for an ordinary row', () => {
    expect(nameOfRow(parseRow('13527486'))).toBeNull();
  });

  it('refuses something that is not a row', () => {
    expect(() => nameOfRow('13572468')).toThrow(/a row is needed/);
  });
});
