import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { Index, IndexSet } from '../../src/store/indexes.js';
import { Repository } from '../../src/store/repository.js';
import { Journal } from '../../src/store/journal.js';
import { ValidationError } from '../../src/errors.js';

/** @type {string} */
let directory;
/** @type {Repository} */
let repository;

beforeEach(() => {
  directory = mkdtempSync(join(tmpdir(), 'garterhole-'));
  repository = new Repository({ journal: new Journal(join(directory, 'records.jsonl')) });
  repository.put('performances', 'a', { tower: 'St Mary', stage: 6, band: ['Alice', 'Bob'] });
  repository.put('performances', 'b', { tower: 'St Mary', stage: 8, band: ['Alice'] });
  repository.put('performances', 'c', { tower: 'All Saints', stage: 6, band: ['Bob'] });
});

afterEach(() => {
  rmSync(directory, { recursive: true, force: true });
});

const byTower = () =>
  new Index({
    name: 'by tower',
    collection: 'performances',
    keysOf: (document) => [document.tower],
  });

const byRinger = () =>
  new Index({
    name: 'by ringer',
    collection: 'performances',
    keysOf: (document) => document.band,
  });

describe('Index', () => {
  it('files each document under its key', () => {
    const index = byTower().build(repository);
    expect(index.lookup('St Mary')).toEqual(['a', 'b']);
    expect(index.lookup('All Saints')).toEqual(['c']);
  });

  it('files a document under several keys', () => {
    const index = byRinger().build(repository);
    expect(index.lookup('Alice')).toEqual(['a', 'b']);
    expect(index.lookup('Bob')).toEqual(['a', 'c']);
  });

  it('gives nothing for a key it does not hold', () => {
    expect(byTower().build(repository).lookup('Nowhere')).toEqual([]);
  });

  it('turns a key into a string so a number works too', () => {
    const index = new Index({
      name: 'by stage',
      collection: 'performances',
      keysOf: (document) => [document.stage],
    }).build(repository);
    expect(index.lookup(6)).toEqual(['a', 'c']);
    expect(index.lookup('6')).toEqual(['a', 'c']);
  });

  it('lists its keys and counts them', () => {
    const index = byTower().build(repository);
    expect(index.keys()).toEqual(['All Saints', 'St Mary']);
    expect(index.size).toBe(2);
    expect(index.counts()).toEqual([
      { key: 'St Mary', count: 2 },
      { key: 'All Saints', count: 1 },
    ]);
  });

  it('rebuilds from what the repository holds now', () => {
    const index = byTower().build(repository);
    repository.delete('performances', 'b');
    index.build(repository);
    expect(index.lookup('St Mary')).toEqual(['a']);
  });

  it('refuses a definition it cannot use', () => {
    expect(() => new Index(null)).toThrow(/needs a definition object/);
    expect(() => new Index({ name: '', collection: 'a', keysOf: () => [] })).toThrow(
      /an index name/,
    );
    expect(() => new Index({ name: 'a', collection: 'b', keysOf: 'tower' })).toThrow(
      /the key function/,
    );
  });

  it('refuses to be built from something that is not a repository', () => {
    expect(() => byTower().build({})).toThrow(ValidationError);
  });
});

describe('IndexSet', () => {
  it('builds each index as it is added', () => {
    const set = new IndexSet(repository).add(byTower()).add(byRinger());
    expect(set.names()).toEqual(['by ringer', 'by tower']);
    expect(set.get('by tower').lookup('St Mary')).toEqual(['a', 'b']);
  });

  it('looks documents up through an index', () => {
    const set = new IndexSet(repository).add(byTower());
    const found = set.find('by tower', 'St Mary');
    expect(found).toHaveLength(2);
    expect(found[0].document.tower).toBe('St Mary');
  });

  it('rebuilds every index at once', () => {
    const set = new IndexSet(repository).add(byTower()).add(byRinger());
    repository.put('performances', 'd', { tower: 'St Mary', stage: 6, band: ['Carol'] });
    set.rebuild();
    expect(set.get('by tower').lookup('St Mary')).toHaveLength(3);
    expect(set.get('by ringer').lookup('Carol')).toEqual(['d']);
  });

  it('refuses an index nobody added', () => {
    expect(() => new IndexSet(repository).get('by nothing')).toThrow(
      /there is no index called "by nothing"/,
    );
  });

  it('refuses anything that is not an index', () => {
    expect(() => new IndexSet(repository).add({ name: 'by tower' })).toThrow(ValidationError);
  });

  it('refuses to be built on something that is not a repository', () => {
    expect(() => new IndexSet({})).toThrow(/needs a Repository/);
  });
});
