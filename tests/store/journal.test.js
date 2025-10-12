import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdtempSync, rmSync, writeFileSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { Journal } from '../../src/store/journal.js';
import { StorageError, ValidationError } from '../../src/errors.js';

/** @type {string} */
let directory;
/** @type {Journal} */
let journal;

beforeEach(() => {
  directory = mkdtempSync(join(tmpdir(), 'garterhole-'));
  journal = new Journal(join(directory, 'records.jsonl'));
});

afterEach(() => {
  rmSync(directory, { recursive: true, force: true });
});

describe('a new journal', () => {
  it('is not there until something is written', () => {
    expect(journal.exists()).toBe(false);
    expect(journal.bytes).toBe(0);
    expect(journal.read()).toEqual([]);
    expect(journal.count()).toBe(0);
  });

  it('refuses a path that is not one', () => {
    expect(() => new Journal('')).toThrow(/needs a path/);
    expect(() => new Journal(null)).toThrow(ValidationError);
  });

  it('makes the directory it needs', () => {
    const nested = new Journal(join(directory, 'deep', 'down', 'records.jsonl'));
    nested.append({ a: 1 });
    expect(nested.read()).toEqual([{ a: 1 }]);
  });
});

describe('appending', () => {
  it('writes a record and reads it back', () => {
    journal.append({ kind: 'tower', name: 'St Mary' });
    expect(journal.read()).toEqual([{ kind: 'tower', name: 'St Mary' }]);
  });

  it('keeps the order records were written in', () => {
    journal.append({ n: 1 }).append({ n: 2 }).append({ n: 3 });
    expect(journal.read().map((record) => record.n)).toEqual([1, 2, 3]);
  });

  it('writes several in one go', () => {
    expect(journal.appendAll([{ n: 1 }, { n: 2 }])).toBe(2);
    expect(journal.count()).toBe(2);
  });

  it('writes nothing for an empty batch', () => {
    expect(journal.appendAll([])).toBe(0);
    expect(journal.exists()).toBe(false);
  });

  it('grows the file', () => {
    journal.append({ n: 1 });
    const first = journal.bytes;
    journal.append({ n: 2 });
    expect(journal.bytes).toBeGreaterThan(first);
  });
});

describe('replaying', () => {
  beforeEach(() => {
    journal.appendAll([
      { kind: 'add', n: 3 },
      { kind: 'add', n: 4 },
      { kind: 'reset' },
      { kind: 'add', n: 10 },
    ]);
  });

  it('folds the records into a state', () => {
    const total = journal.replay(
      (state, record) => (record.kind === 'reset' ? 0 : state + record.n),
      0,
    );
    expect(total).toBe(10);
  });

  it('passes the index of each record', () => {
    const indexes = journal.replay((state, record, index) => [...state, index], []);
    expect(indexes).toEqual([0, 1, 2, 3]);
  });

  it('gives the initial state back for an empty journal', () => {
    const empty = new Journal(join(directory, 'empty.jsonl'));
    expect(empty.replay((state) => state + 1, 7)).toBe(7);
  });

  it('refuses to fold without a function', () => {
    expect(() => journal.replay(null, 0)).toThrow(/a function to fold with/);
  });
});

describe('damaged journals', () => {
  it('reports a line that has been changed', () => {
    journal.append({ n: 1 });
    const text = readFileSync(journal.path, 'utf8');
    writeFileSync(journal.path, `${text}00000000 {"n":2}\n`, 'utf8');
    expect(() => journal.read()).toThrow(StorageError);
    expect(() => journal.read()).toThrow(/line 2 is damaged/);
  });

  it('reports a line with no checksum', () => {
    writeFileSync(journal.path, '{"n":1}\n', 'utf8');
    expect(() => journal.read()).toThrow(/does not start with a checksum/);
  });
});

describe('rewriting', () => {
  it('replaces everything', () => {
    journal.appendAll([{ n: 1 }, { n: 2 }]);
    expect(journal.rewrite([{ n: 9 }])).toBe(1);
    expect(journal.read()).toEqual([{ n: 9 }]);
  });

  it('can empty the journal', () => {
    journal.append({ n: 1 });
    expect(journal.rewrite([])).toBe(0);
    expect(journal.read()).toEqual([]);
    expect(journal.bytes).toBe(0);
  });
});
