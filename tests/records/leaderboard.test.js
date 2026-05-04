import { describe, it, expect } from 'vitest';
import {
  ORDERINGS,
  scoreOf,
  leaderboard,
  leaderboardLines,
  summary,
} from '../../src/records/leaderboard.js';
import { performance } from '../../src/records/performance.js';
import { ValidationError } from '../../src/errors.js';

const six = ['Alice', 'Bob', 'Carol', 'Dan', 'Erin', 'Frank'];

const rung = (overrides = {}) =>
  performance({
    tower: 'St Mary',
    date: new Date(Date.UTC(2025, 4, 18)),
    title: 'Plain Bob Minor',
    changes: 1260,
    band: six,
    conductor: 'Alice',
    ...overrides,
  });

const records = () => [
  rung(),
  rung({ date: new Date(Date.UTC(2025, 5, 1)), changes: 5040, conductor: 'Bob' }),
  rung({ date: new Date(Date.UTC(2025, 6, 1)), band: [...six.slice(0, 5), 'Gina'] }),
];

describe('scoreOf', () => {
  const line = { totals: { performances: 3, peals: 1, changes: 7560 }, conducted: 2 };

  it('scores each ordering from the right field', () => {
    expect(scoreOf(line, 'performances')).toBe(3);
    expect(scoreOf(line, 'peals')).toBe(1);
    expect(scoreOf(line, 'changes')).toBe(7560);
    expect(scoreOf(line, 'conducted')).toBe(2);
  });

  it('refuses an ordering nobody uses', () => {
    expect(() => scoreOf(line, 'height')).toThrow(/is not an ordering/);
  });

  it('lists the orderings it knows', () => {
    expect(ORDERINGS).toContain('peals');
    expect(Object.isFrozen(ORDERINGS)).toBe(true);
  });
});

describe('leaderboard', () => {
  it('puts the busiest ringer first', () => {
    const board = leaderboard(records());
    expect(board[0].place).toBe(1);
    expect(board[0].name).toBe('Alice');
    expect(board[0].score).toBe(3);
  });

  it('numbers the places', () => {
    expect(leaderboard(records()).map((line) => line.place)).toEqual([1, 2, 3, 4, 5, 6, 7]);
  });

  it('orders by peals when asked', () => {
    const board = leaderboard(records(), { ordering: 'peals' });
    expect(board.every((line) => line.score === 1)).toBe(true);
  });

  it('orders by conducting when asked', () => {
    const board = leaderboard(records(), { ordering: 'conducted' });
    expect(board[0].name).toBe('Alice');
    expect(board[0].score).toBe(2);
  });

  it('leaves out anybody who scored nothing', () => {
    const board = leaderboard(records(), { ordering: 'conducted' });
    expect(board.map((line) => line.name)).toEqual(['Alice', 'Bob']);
  });

  it('stops at the number of places asked for', () => {
    expect(leaderboard(records(), { top: 2 })).toHaveLength(2);
  });

  it('breaks a tie by name', () => {
    const board = leaderboard(records());
    const tied = board.filter((line) => line.score === 3).map((line) => line.name);
    expect(tied).toEqual([...tied].sort());
  });

  it('refuses a board with no places', () => {
    expect(() => leaderboard(records(), { top: 0 })).toThrow(ValidationError);
  });

  it('gives nothing for no records', () => {
    expect(leaderboard([])).toEqual([]);
  });
});

describe('leaderboardLines', () => {
  it('writes a heading and a table', () => {
    const lines = leaderboardLines(records());
    expect(lines[0]).toBe('Most performances');
    expect(lines[1]).toContain('Alice');
  });

  it('names the ordering in the heading', () => {
    expect(leaderboardLines(records(), { ordering: 'peals' })[0]).toBe('Most peals');
  });

  it('says so when nobody has rung anything', () => {
    expect(leaderboardLines([])).toEqual(['nobody has rung anything']);
  });
});

describe('summary', () => {
  it('sums the year up in a sentence', () => {
    const line = summary(records());
    expect(line).toContain('3 performances');
    expect(line).toContain('most of them by Alice with 3');
  });

  it('says so when nothing was rung', () => {
    expect(summary([])).toBe('nothing was rung');
  });
});
