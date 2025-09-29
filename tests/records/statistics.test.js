import { describe, it, expect } from 'vitest';
import {
  totals,
  byRinger,
  byTower,
  byMethod,
  byStage,
  between,
} from '../../src/records/statistics.js';
import { performance } from '../../src/records/performance.js';
import { ValidationError } from '../../src/errors.js';

const six = ['Alice', 'Bob', 'Carol', 'Dan', 'Erin', 'Frank'];
const eight = [...six, 'Gina', 'Hal'];

const records = () => [
  performance({
    tower: 'St Mary',
    date: new Date(Date.UTC(2025, 0, 5)),
    title: 'Plain Bob Minor',
    changes: 1260,
    band: six,
    conductor: 'Alice',
  }),
  performance({
    tower: 'St Mary',
    date: new Date(Date.UTC(2025, 2, 9)),
    title: 'Plain Bob Minor',
    changes: 5040,
    band: six,
    conductor: 'Bob',
  }),
  performance({
    tower: 'All Saints',
    date: new Date(Date.UTC(2025, 5, 14)),
    title: 'Cambridge Surprise Major',
    changes: 1280,
    band: eight,
    conductor: 'Alice',
  }),
  performance({
    tower: 'All Saints',
    date: new Date(Date.UTC(2025, 7, 2)),
    title: 'Plain Bob Major',
    changes: 720,
    band: eight,
  }),
];

describe('totals', () => {
  it('counts performances by kind', () => {
    expect(totals(records())).toEqual({
      performances: 4,
      peals: 1,
      quarters: 2,
      touches: 1,
      changes: 1260 + 5040 + 1280 + 720,
    });
  });

  it('counts nothing for nothing', () => {
    expect(totals([])).toEqual({
      performances: 0,
      peals: 0,
      quarters: 0,
      touches: 0,
      changes: 0,
    });
  });

  it('refuses anything that is not a performance', () => {
    expect(() => totals(['a peal'])).toThrow(ValidationError);
  });
});

describe('byRinger', () => {
  it('counts a performance once for everybody in the band', () => {
    const lines = byRinger(records());
    const alice = lines.find((line) => line.name === 'Alice');
    expect(alice.totals.performances).toBe(4);
    expect(alice.conducted).toBe(2);
  });

  it('counts somebody who rang once', () => {
    const gina = byRinger(records()).find((line) => line.name === 'Gina');
    expect(gina.totals.performances).toBe(2);
    expect(gina.conducted).toBe(0);
  });

  it('sorts by how much each rang', () => {
    const lines = byRinger(records());
    expect(lines[0].totals.performances).toBeGreaterThanOrEqual(
      lines[lines.length - 1].totals.performances,
    );
  });

  it('breaks a tie by name', () => {
    const names = byRinger(records())
      .filter((line) => line.totals.performances === 2)
      .map((line) => line.name);
    expect(names).toEqual(['Gina', 'Hal']);
  });
});

describe('byTower', () => {
  it('counts what each tower had rung in it', () => {
    const lines = byTower(records());
    expect(lines).toHaveLength(2);
    expect(lines[0].totals.performances).toBe(2);
  });

  it('names both towers', () => {
    expect(
      byTower(records())
        .map((line) => line.tower)
        .sort(),
    ).toEqual(['All Saints', 'St Mary']);
  });
});

describe('byMethod', () => {
  it('counts each method, most first', () => {
    expect(byMethod(records())[0]).toEqual({ title: 'Plain Bob Minor', count: 2 });
  });

  it('lists every method once', () => {
    expect(byMethod(records())).toHaveLength(3);
  });
});

describe('byStage', () => {
  it('counts each stage, largest first', () => {
    expect(byStage(records())).toEqual([
      { stage: 8, count: 2 },
      { stage: 6, count: 2 },
    ]);
  });

  it('skips a title it cannot read', () => {
    const odd = performance({
      tower: 'Test',
      date: new Date(Date.UTC(2025, 0, 1)),
      title: 'Nonsense',
      changes: 720,
      band: six,
    });
    expect(byStage([odd])).toEqual([]);
  });
});

describe('between', () => {
  it('keeps the performances inside the dates', () => {
    const found = between(
      records(),
      new Date(Date.UTC(2025, 1, 1)),
      new Date(Date.UTC(2025, 6, 1)),
    );
    expect(found).toHaveLength(2);
  });

  it('includes the start and excludes the end', () => {
    const found = between(
      records(),
      new Date(Date.UTC(2025, 0, 5)),
      new Date(Date.UTC(2025, 2, 9)),
    );
    expect(found).toHaveLength(1);
  });

  it('refuses something that is not a pair of dates', () => {
    expect(() => between(records(), '2025-01-01', new Date())).toThrow(/two dates are needed/);
  });
});
