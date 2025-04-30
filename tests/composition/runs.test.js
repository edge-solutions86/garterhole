import { describe, it, expect } from 'vitest';
import {
  runLengthAtBack,
  runLengthAtFront,
  hasRunAtBack,
  hasRunAtFront,
  countRuns,
  runTable,
} from '../../src/composition/music/runs.js';
import { parseRow, parseRows } from '../../src/core/rowParse.js';
import { rounds } from '../../src/core/row.js';
import { ValidationError } from '../../src/errors.js';

describe('runLengthAtBack', () => {
  it('finds an ascending run', () => {
    expect(runLengthAtBack(parseRow('42135678'))).toBe(4);
  });

  it('finds a descending run', () => {
    expect(runLengthAtBack(parseRow('24138765'))).toBe(4);
  });

  it('finds the whole row in rounds', () => {
    expect(runLengthAtBack(rounds(8))).toBe(8);
  });

  it('gives one when the last two bells are not next to each other', () => {
    expect(runLengthAtBack(parseRow('13527468'))).toBe(1);
  });

  it('refuses something that is not a row', () => {
    expect(() => runLengthAtBack('12345678')).toThrow(ValidationError);
  });
});

describe('runLengthAtFront', () => {
  it('finds an ascending run', () => {
    expect(runLengthAtFront(parseRow('12346857'))).toBe(4);
  });

  it('finds a descending run', () => {
    expect(runLengthAtFront(parseRow('43216857'))).toBe(4);
  });

  it('gives one when the first two bells are not next to each other', () => {
    expect(runLengthAtFront(parseRow('13527468'))).toBe(1);
  });
});

describe('hasRunAtBack and hasRunAtFront', () => {
  it('accepts a run at least as long as asked for', () => {
    expect(hasRunAtBack(parseRow('42135678'), 4)).toBe(true);
    expect(hasRunAtBack(parseRow('42135678'), 3)).toBe(true);
    expect(hasRunAtBack(parseRow('42135678'), 5)).toBe(false);
  });

  it('looks at the right end of the row', () => {
    expect(hasRunAtFront(parseRow('42135678'), 4)).toBe(false);
    expect(hasRunAtFront(parseRow('12346857'), 4)).toBe(true);
  });

  it('refuses a length that is not worth counting', () => {
    expect(() => hasRunAtBack(rounds(8), 1)).toThrow(/is not something to count/);
    expect(() => hasRunAtBack(rounds(8), 0)).toThrow(ValidationError);
  });
});

describe('countRuns', () => {
  const rows = parseRows('42135678 12346857 13527468 87654321');

  it('counts the two ends separately', () => {
    expect(countRuns(rows, 4)).toEqual({ front: 2, back: 2, total: 3 });
  });

  it('counts a row with a run at both ends once in the total', () => {
    expect(countRuns(parseRows('12345678'), 4)).toEqual({ front: 1, back: 1, total: 1 });
  });

  it('counts nothing when nothing qualifies', () => {
    expect(countRuns(parseRows('13527468'), 4)).toEqual({ front: 0, back: 0, total: 0 });
  });

  it('counts nothing in an empty block', () => {
    expect(countRuns([], 4)).toEqual({ front: 0, back: 0, total: 0 });
  });
});

describe('runTable', () => {
  const rows = parseRows('42135678 12346857 13527468 87654321');

  it('has a line for every length from four up', () => {
    const table = runTable(rows, 8);
    expect([...table.keys()]).toEqual([4, 5, 6, 7, 8]);
  });

  it('counts fewer long runs than short ones', () => {
    const table = runTable(rows, 8);
    expect(table.get(4).total).toBeGreaterThanOrEqual(table.get(5).total);
    expect(table.get(8).total).toBe(1);
  });

  it('starts wherever it is told to', () => {
    expect([...runTable(rows, 8, 6).keys()]).toEqual([6, 7, 8]);
  });
});
