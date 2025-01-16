import { describe, it, expect } from 'vitest';
import {
  groupBy,
  countBy,
  chunk,
  unique,
  sortBy,
  partition,
  zip,
  sum,
  mean,
  maxBy,
} from '../../src/util/collections.js';

describe('groupBy', () => {
  it('collects items under their key', () => {
    const groups = groupBy(['aa', 'ab', 'ba'], (word) => word[0]);
    expect(groups.get('a')).toEqual(['aa', 'ab']);
    expect(groups.get('b')).toEqual(['ba']);
  });

  it('keeps the order items arrived in', () => {
    const groups = groupBy([3, 1, 4, 1, 5], (n) => n % 2);
    expect(groups.get(1)).toEqual([3, 1, 1, 5]);
  });

  it('gives an empty map for nothing', () => {
    expect(groupBy([], (x) => x).size).toBe(0);
  });
});

describe('countBy', () => {
  it('counts items under their key', () => {
    const counts = countBy(['bob', 'single', 'bob', 'plain'], (call) => call);
    expect(counts.get('bob')).toBe(2);
    expect(counts.get('plain')).toBe(1);
    expect(counts.get('extreme')).toBeUndefined();
  });
});

describe('chunk', () => {
  it('breaks a list into runs', () => {
    expect(chunk([1, 2, 3, 4, 5, 6], 2)).toEqual([
      [1, 2],
      [3, 4],
      [5, 6],
    ]);
  });

  it('leaves a short run at the end', () => {
    expect(chunk([1, 2, 3, 4, 5], 2)).toEqual([[1, 2], [3, 4], [5]]);
  });

  it('gives nothing for an empty list', () => {
    expect(chunk([], 3)).toEqual([]);
  });

  it('refuses a size that is not a positive whole number', () => {
    expect(() => chunk([1], 0)).toThrow(/must be a positive whole number/);
    expect(() => chunk([1], 1.5)).toThrow(RangeError);
  });
});

describe('unique', () => {
  it('keeps the first of each repeat', () => {
    expect(unique([1, 2, 1, 3, 2])).toEqual([1, 2, 3]);
  });

  it('uses a key function when one is given', () => {
    const rows = [{ id: 'a' }, { id: 'b' }, { id: 'a' }];
    expect(unique(rows, (row) => row.id)).toHaveLength(2);
  });

  it('does not disturb the input', () => {
    const input = [2, 1, 2];
    unique(input);
    expect(input).toEqual([2, 1, 2]);
  });
});

describe('sortBy', () => {
  it('sorts by one key', () => {
    expect(sortBy([3, 1, 2], (n) => n)).toEqual([1, 2, 3]);
  });

  it('falls back to the next key on a tie', () => {
    const items = [
      { stage: 8, name: 'Yorkshire' },
      { stage: 6, name: 'Cambridge' },
      { stage: 8, name: 'Bristol' },
    ];
    expect(
      sortBy(
        items,
        (m) => m.stage,
        (m) => m.name,
      ).map((m) => m.name),
    ).toEqual(['Cambridge', 'Bristol', 'Yorkshire']);
  });

  it('leaves the input alone', () => {
    const input = [3, 1, 2];
    sortBy(input, (n) => n);
    expect(input).toEqual([3, 1, 2]);
  });
});

describe('partition', () => {
  it('splits by the test', () => {
    expect(partition([1, 2, 3, 4], (n) => n % 2 === 0)).toEqual([
      [2, 4],
      [1, 3],
    ]);
  });

  it('gives two empty lists for nothing', () => {
    expect(partition([], () => true)).toEqual([[], []]);
  });
});

describe('zip', () => {
  it('pairs items up', () => {
    expect(zip([1, 2], ['a', 'b'])).toEqual([
      [1, 'a'],
      [2, 'b'],
    ]);
  });

  it('stops at the shorter list', () => {
    expect(zip([1, 2, 3], ['a'])).toEqual([[1, 'a']]);
    expect(zip([], ['a'])).toEqual([]);
  });
});

describe('sum and mean', () => {
  it('adds numbers up', () => {
    expect(sum([1, 2, 3])).toBe(6);
    expect(sum([])).toBe(0);
  });

  it('takes the mean', () => {
    expect(mean([2, 4, 6])).toBe(4);
  });

  it('gives null rather than NaN for an empty mean', () => {
    expect(mean([])).toBeNull();
  });
});

describe('maxBy', () => {
  it('finds the highest score', () => {
    expect(maxBy([{ n: 1 }, { n: 5 }, { n: 3 }], (item) => item.n)).toEqual({ n: 5 });
  });

  it('breaks ties in favour of the first', () => {
    const first = { id: 'a', n: 5 };
    expect(maxBy([first, { id: 'b', n: 5 }], (item) => item.n)).toBe(first);
  });

  it('gives null for an empty list', () => {
    expect(maxBy([], () => 1)).toBeNull();
  });
});
