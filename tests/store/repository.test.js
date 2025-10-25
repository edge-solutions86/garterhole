import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { Repository } from '../../src/store/repository.js';
import { Journal } from '../../src/store/journal.js';
import { Snapshot } from '../../src/store/snapshot.js';
import { NotFoundError, StorageError } from '../../src/errors.js';

/** @type {string} */
let directory;
/** @type {Journal} */
let journal;
/** @type {Snapshot} */
let snapshot;

beforeEach(() => {
  directory = mkdtempSync(join(tmpdir(), 'garterhole-'));
  journal = new Journal(join(directory, 'records.jsonl'));
  snapshot = new Snapshot(join(directory, 'state.json'));
});

afterEach(() => {
  rmSync(directory, { recursive: true, force: true });
});

const open = (withSnapshot = false) =>
  new Repository(withSnapshot ? { journal, snapshot } : { journal });

describe('a new repository', () => {
  it('starts empty', () => {
    const repository = open();
    expect(repository.collectionNames()).toEqual([]);
    expect(repository.count('towers')).toBe(0);
    expect(repository.all('towers')).toEqual([]);
  });

  it('refuses options it cannot use', () => {
    expect(() => new Repository(null)).toThrow(/needs a journal/);
    expect(() => new Repository({ journal: 'records.jsonl' })).toThrow(/needs a Journal/);
  });
});

describe('filing documents', () => {
  it('files and finds one', () => {
    const repository = open();
    repository.put('towers', 'st-mary', { name: 'St Mary', bells: 8 });
    expect(repository.get('towers', 'st-mary')).toEqual({ name: 'St Mary', bells: 8 });
    expect(repository.has('towers', 'st-mary')).toBe(true);
  });

  it('replaces a document with the same id', () => {
    const repository = open();
    repository.put('towers', 'st-mary', { bells: 6 });
    repository.put('towers', 'st-mary', { bells: 8 });
    expect(repository.get('towers', 'st-mary')).toEqual({ bells: 8 });
    expect(repository.count('towers')).toBe(1);
  });

  it('keeps collections apart', () => {
    const repository = open();
    repository.put('towers', 'a', { n: 1 });
    repository.put('methods', 'a', { n: 2 });
    expect(repository.get('towers', 'a')).toEqual({ n: 1 });
    expect(repository.collectionNames()).toEqual(['methods', 'towers']);
  });

  it('throws for a document it does not hold', () => {
    expect(() => open().get('towers', 'nothing')).toThrow(NotFoundError);
    expect(open().find('towers', 'nothing')).toBeUndefined();
  });

  it('lists a collection in id order', () => {
    const repository = open();
    repository.put('towers', 'c', {});
    repository.put('towers', 'a', {});
    repository.put('towers', 'b', {});
    expect(repository.all('towers').map((entry) => entry.id)).toEqual(['a', 'b', 'c']);
  });

  it('refuses a document it cannot file', () => {
    const repository = open();
    expect(() => repository.put('', 'a', {})).toThrow(/a collection name/);
    expect(() => repository.put('towers', '', {})).toThrow(/a document id/);
    expect(() => repository.put('towers', 'a', undefined)).toThrow(/no document to file/);
  });
});

describe('deleting', () => {
  it('takes a document out', () => {
    const repository = open();
    repository.put('towers', 'a', {});
    expect(repository.delete('towers', 'a')).toBe(true);
    expect(repository.has('towers', 'a')).toBe(false);
  });

  it('says so when there was nothing to delete', () => {
    expect(open().delete('towers', 'a')).toBe(false);
  });

  it('writes nothing to the journal for a delete that does nothing', () => {
    const repository = open();
    repository.delete('towers', 'a');
    expect(journal.count()).toBe(0);
  });
});

describe('rebuilding from the journal', () => {
  it('gives the same state when reopened', () => {
    const first = open();
    first.put('towers', 'a', { n: 1 });
    first.put('towers', 'b', { n: 2 });
    first.delete('towers', 'a');
    const second = open();
    expect(second.count('towers')).toBe(1);
    expect(second.get('towers', 'b')).toEqual({ n: 2 });
  });

  it('reports how much it replayed', () => {
    const first = open();
    first.put('towers', 'a', {});
    first.put('towers', 'b', {});
    expect(open().load()).toEqual({ fromSnapshot: false, replayed: 2 });
  });

  it('refuses a journal holding something that is not a change', () => {
    journal.append({ nonsense: true });
    expect(() => open()).toThrow(StorageError);
    expect(() => open()).toThrow(/does not say what it changes/);
  });

  it('refuses an operation it cannot apply', () => {
    journal.append({ op: 'merge', collection: 'towers', id: 'a' });
    expect(() => open()).toThrow(/has an operation this cannot apply: merge/);
  });

  it('refuses a record that is not an object', () => {
    journal.append(42);
    expect(() => open()).toThrow(/is not a change/);
  });
});

describe('snapshots', () => {
  it('writes one and loads from it', () => {
    const first = open(true);
    first.put('towers', 'a', { n: 1 });
    expect(first.save()).toBe(true);
    const second = open(true);
    expect(second.get('towers', 'a')).toEqual({ n: 1 });
  });

  it('replays only what came after the snapshot', () => {
    const first = open(true);
    first.put('towers', 'a', { n: 1 });
    first.save();
    first.put('towers', 'b', { n: 2 });
    const second = open(true);
    expect(second.load().fromSnapshot).toBe(true);
    expect(second.count('towers')).toBe(2);
  });

  it('does nothing when there is no snapshot to write', () => {
    expect(open().save()).toBe(false);
  });

  it('refuses a document id that is not a string', () => {
    expect(() => open().put('towers', 7, {})).toThrow(TypeError);
    expect(() => open().put('towers', 7, {})).toThrow(/a document id must be a non empty string/);
  });
});
