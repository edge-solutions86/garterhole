import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import {
  compactionGain,
  worthCompacting,
  liveRecords,
  compact,
  compactIfWorthwhile,
} from '../../src/store/compaction.js';
import { Repository } from '../../src/store/repository.js';
import { Journal } from '../../src/store/journal.js';
import { Snapshot } from '../../src/store/snapshot.js';
import { ValidationError } from '../../src/errors.js';

/** @type {string} */
let directory;
/** @type {Journal} */
let journal;
/** @type {Snapshot} */
let snapshot;
/** @type {Repository} */
let repository;

beforeEach(() => {
  directory = mkdtempSync(join(tmpdir(), 'garterhole-'));
  journal = new Journal(join(directory, 'records.jsonl'));
  snapshot = new Snapshot(join(directory, 'state.json'));
  repository = new Repository({ journal, snapshot });
});

afterEach(() => {
  rmSync(directory, { recursive: true, force: true });
});

/** Write a document several times and delete another, so there is waste. */
function churn() {
  repository.put('towers', 'a', { n: 1 });
  repository.put('towers', 'a', { n: 2 });
  repository.put('towers', 'a', { n: 3 });
  repository.put('towers', 'b', { n: 1 });
  repository.delete('towers', 'b');
}

describe('compactionGain', () => {
  it('counts nothing wasted in a fresh journal', () => {
    repository.put('towers', 'a', { n: 1 });
    expect(compactionGain(repository)).toEqual({ records: 1, live: 1, wasted: 0, ratio: 0 });
  });

  it('counts the records no longer doing any work', () => {
    churn();
    const gain = compactionGain(repository);
    expect(gain.records).toBe(5);
    expect(gain.live).toBe(1);
    expect(gain.wasted).toBe(4);
    expect(gain.ratio).toBeCloseTo(0.8, 6);
  });

  it('handles an empty journal', () => {
    expect(compactionGain(repository).ratio).toBe(0);
  });

  it('refuses something that is not a repository', () => {
    expect(() => compactionGain({})).toThrow(ValidationError);
  });
});

describe('worthCompacting', () => {
  it('says no for a small journal however wasteful', () => {
    churn();
    expect(worthCompacting(repository)).toBe(false);
  });

  it('says yes once the journal is big and wasteful', () => {
    for (let time = 0; time < 60; time += 1) {
      repository.put('towers', 'a', { n: time });
    }
    expect(worthCompacting(repository, { minimumRecords: 10 })).toBe(true);
  });

  it('says no when the journal is big and tidy', () => {
    for (let time = 0; time < 60; time += 1) {
      repository.put('towers', `t${time}`, { n: time });
    }
    expect(worthCompacting(repository, { minimumRecords: 10 })).toBe(false);
  });
});

describe('liveRecords', () => {
  it('gives one put per living document', () => {
    churn();
    const records = liveRecords(repository);
    expect(records).toHaveLength(1);
    expect(records[0]).toEqual({ op: 'put', collection: 'towers', id: 'a', document: { n: 3 } });
  });

  it('walks the collections in order', () => {
    repository.put('methods', 'm', {});
    repository.put('towers', 't', {});
    expect(liveRecords(repository).map((record) => record.collection)).toEqual([
      'methods',
      'towers',
    ]);
  });
});

describe('compact', () => {
  it('shortens the journal', () => {
    churn();
    expect(compact(repository)).toEqual({ before: 5, after: 1 });
    expect(journal.count()).toBe(1);
  });

  it('leaves the state exactly as it was', () => {
    churn();
    repository.put('methods', 'm', { title: 'Plain Bob Minor' });
    compact(repository);
    const reopened = new Repository({ journal });
    expect(reopened.get('towers', 'a')).toEqual({ n: 3 });
    expect(reopened.get('methods', 'm')).toEqual({ title: 'Plain Bob Minor' });
    expect(reopened.has('towers', 'b')).toBe(false);
  });

  it('throws the snapshot away, since it counts the old records', () => {
    churn();
    repository.save();
    expect(snapshot.exists()).toBe(true);
    compact(repository);
    expect(snapshot.exists()).toBe(false);
  });

  it('leaves the repository able to write again', () => {
    churn();
    compact(repository);
    repository.put('towers', 'c', { n: 9 });
    expect(new Repository({ journal }).get('towers', 'c')).toEqual({ n: 9 });
  });
});

describe('compactIfWorthwhile', () => {
  it('does nothing to a tidy journal', () => {
    repository.put('towers', 'a', { n: 1 });
    expect(compactIfWorthwhile(repository)).toEqual({ compacted: false, before: 1, after: 1 });
  });

  it('compacts a wasteful one', () => {
    for (let time = 0; time < 60; time += 1) {
      repository.put('towers', 'a', { n: time });
    }
    const result = compactIfWorthwhile(repository, { minimumRecords: 10 });
    expect(result.compacted).toBe(true);
    expect(result.after).toBe(1);
  });
});
