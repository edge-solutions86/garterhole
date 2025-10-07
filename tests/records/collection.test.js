import { describe, it, expect, beforeEach } from 'vitest';
import { PerformanceCollection } from '../../src/records/collection.js';
import { performance } from '../../src/records/performance.js';
import { ConflictError, NotFoundError, ValidationError } from '../../src/errors.js';

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

/** @type {PerformanceCollection} */
let records;

beforeEach(() => {
  records = new PerformanceCollection();
  records.addAll([
    rung(),
    rung({ date: new Date(Date.UTC(2025, 6, 2)), changes: 5040, conductor: 'Bob' }),
    rung({
      date: new Date(Date.UTC(2025, 2, 9)),
      tower: 'All Saints',
      title: 'Cambridge Surprise Minor',
      changes: 720,
    }),
  ]);
});

describe('filing performances', () => {
  it('files a performance under its identifier', () => {
    const collection = new PerformanceCollection();
    const id = collection.add(rung());
    expect(collection.size).toBe(1);
    expect(collection.get(id).tower).toBe('St Mary');
  });

  it('refuses the same performance twice', () => {
    const collection = new PerformanceCollection();
    collection.add(rung());
    expect(() => collection.add(rung())).toThrow(ConflictError);
    expect(() => collection.add(rung())).toThrow(/already filed/);
  });

  it('accepts two different performances on the same day', () => {
    const collection = new PerformanceCollection();
    collection.add(rung());
    expect(() => collection.add(rung({ changes: 720 }))).not.toThrow();
  });

  it('refuses anything that is not a performance', () => {
    expect(() => new PerformanceCollection().add({ tower: 'St Mary' })).toThrow(ValidationError);
  });

  it('skips the repeats when several are filed at once', () => {
    const result = records.addAll([rung(), rung({ changes: 1280 })]);
    expect(result.added).toHaveLength(1);
    expect(result.alreadyFiled).toHaveLength(1);
  });
});

describe('reading the records', () => {
  it('says whether it holds one', () => {
    expect(records.has(rung().id)).toBe(true);
    expect(records.has('nothing')).toBe(false);
  });

  it('throws for one it does not hold', () => {
    expect(() => records.get('nothing')).toThrow(NotFoundError);
  });

  it('lists everything with the most recent first', () => {
    expect(records.all().map((entry) => entry.dateText)).toEqual([
      '2025-07-02',
      '2025-05-18',
      '2025-03-09',
    ]);
  });

  it('iterates in the same order', () => {
    expect([...records]).toHaveLength(3);
  });

  it('takes a performance out', () => {
    const removed = records.remove(rung().id);
    expect(removed.tower).toBe('St Mary');
    expect(records.size).toBe(2);
  });

  it('throws when taking out one it does not hold', () => {
    expect(() => records.remove('nothing')).toThrow(NotFoundError);
  });
});

describe('searching', () => {
  it('gives everything when nothing is asked for', () => {
    expect(records.find()).toHaveLength(3);
  });

  it('filters by tower, however it is typed', () => {
    expect(records.find({ tower: 'st mary' })).toHaveLength(2);
    expect(records.find({ tower: 'all' })).toHaveLength(1);
  });

  it('filters by method', () => {
    expect(records.find({ title: 'cambridge' })).toHaveLength(1);
  });

  it('filters by who rang', () => {
    expect(records.find({ ringer: 'Alice' })).toHaveLength(3);
    expect(records.find({ ringer: 'Nobody' })).toHaveLength(0);
  });

  it('filters by who conducted', () => {
    expect(records.find({ conductor: 'Bob' })).toHaveLength(1);
  });

  it('filters by what the length counts as', () => {
    expect(records.find({ kind: 'peal' })).toHaveLength(1);
    expect(records.find({ kind: 'touch' })).toHaveLength(1);
  });

  it('filters by date, including the start and excluding the end', () => {
    const found = records.find({
      from: new Date(Date.UTC(2025, 2, 9)),
      to: new Date(Date.UTC(2025, 6, 2)),
    });
    expect(found).toHaveLength(2);
  });

  it('combines filters', () => {
    expect(records.find({ tower: 'St Mary', kind: 'peal' })).toHaveLength(1);
  });
});

describe('firstIn', () => {
  it('finds the earliest performance of a method by a ringer', () => {
    expect(records.firstIn('Alice', 'Plain Bob Minor').dateText).toBe('2025-05-18');
  });

  it('gives null when they have never rung it', () => {
    expect(records.firstIn('Alice', 'Bristol Surprise Major')).toBeNull();
    expect(records.firstIn('Nobody', 'Plain Bob Minor')).toBeNull();
  });
});
