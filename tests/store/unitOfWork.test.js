import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { UnitOfWork, inUnitOfWork } from '../../src/store/unitOfWork.js';
import { Repository } from '../../src/store/repository.js';
import { Journal } from '../../src/store/journal.js';
import { ValidationError } from '../../src/errors.js';

/** @type {string} */
let directory;
/** @type {Journal} */
let journal;
/** @type {Repository} */
let repository;

beforeEach(() => {
  directory = mkdtempSync(join(tmpdir(), 'garterhole-'));
  journal = new Journal(join(directory, 'records.jsonl'));
  repository = new Repository({ journal });
});

afterEach(() => {
  rmSync(directory, { recursive: true, force: true });
});

describe('collecting changes', () => {
  it('writes nothing until it is committed', () => {
    const unit = new UnitOfWork(repository);
    unit.put('towers', 'a', { n: 1 });
    expect(journal.count()).toBe(0);
    expect(repository.has('towers', 'a')).toBe(false);
  });

  it('counts what is waiting', () => {
    const unit = new UnitOfWork(repository);
    unit.put('towers', 'a', {}).delete('towers', 'b');
    expect(unit.size).toBe(2);
  });

  it('lands everything in one append', () => {
    const unit = new UnitOfWork(repository);
    unit.put('towers', 'a', { n: 1 }).put('towers', 'b', { n: 2 });
    expect(unit.commit()).toBe(2);
    expect(journal.count()).toBe(2);
    expect(repository.count('towers')).toBe(2);
  });

  it('commits nothing when there is nothing to commit', () => {
    expect(new UnitOfWork(repository).commit()).toBe(0);
    expect(journal.count()).toBe(0);
  });

  it('refuses a change it cannot use', () => {
    const unit = new UnitOfWork(repository);
    expect(() => unit.put('', 'a', {})).toThrow(/a collection name/);
    expect(() => unit.put('towers', 'a', undefined)).toThrow(/no document to file/);
    expect(() => unit.delete('towers', '')).toThrow(/a document id/);
  });

  it('refuses to be built on something that is not a repository', () => {
    expect(() => new UnitOfWork({})).toThrow(ValidationError);
  });
});

describe('reading inside a unit', () => {
  it('sees a document it has filed', () => {
    const unit = new UnitOfWork(repository);
    unit.put('towers', 'a', { n: 1 });
    expect(unit.find('towers', 'a')).toEqual({ n: 1 });
    expect(unit.has('towers', 'a')).toBe(true);
  });

  it('sees the latest of several changes to one document', () => {
    const unit = new UnitOfWork(repository);
    unit.put('towers', 'a', { n: 1 }).put('towers', 'a', { n: 2 });
    expect(unit.find('towers', 'a')).toEqual({ n: 2 });
  });

  it('sees a document it has deleted as gone', () => {
    repository.put('towers', 'a', { n: 1 });
    const unit = new UnitOfWork(repository);
    unit.delete('towers', 'a');
    expect(unit.has('towers', 'a')).toBe(false);
  });

  it('falls through to what is already filed', () => {
    repository.put('towers', 'a', { n: 1 });
    expect(new UnitOfWork(repository).find('towers', 'a')).toEqual({ n: 1 });
  });
});

describe('finishing a unit', () => {
  it('cannot be used twice', () => {
    const unit = new UnitOfWork(repository);
    unit.commit();
    expect(() => unit.put('towers', 'a', {})).toThrow(/already been finished/);
    expect(() => unit.commit()).toThrow(ValidationError);
  });

  it('throws the changes away on a rollback', () => {
    const unit = new UnitOfWork(repository);
    unit.put('towers', 'a', {}).put('towers', 'b', {});
    expect(unit.rollback()).toBe(2);
    expect(journal.count()).toBe(0);
  });
});

describe('inUnitOfWork', () => {
  it('commits when the work returns', () => {
    const answer = inUnitOfWork(repository, (unit) => {
      unit.put('towers', 'a', { n: 1 });
      return 'done';
    });
    expect(answer).toBe('done');
    expect(repository.has('towers', 'a')).toBe(true);
  });

  it('rolls back when the work throws', () => {
    expect(() =>
      inUnitOfWork(repository, (unit) => {
        unit.put('towers', 'a', { n: 1 });
        throw new Error('no');
      }),
    ).toThrow('no');
    expect(journal.count()).toBe(0);
    expect(repository.has('towers', 'a')).toBe(false);
  });

  it('refuses to be given something that is not work', () => {
    expect(() => inUnitOfWork(repository, null)).toThrow(/something to do/);
  });
});

describe('the batch the repository writes', () => {
  it('refuses a batch that is not an array', () => {
    expect(() => repository.applyBatch(null)).toThrow(/must be an array/);
  });

  it('writes nothing for an empty batch', () => {
    expect(repository.applyBatch([])).toBe(0);
    expect(journal.count()).toBe(0);
  });

  it('refuses the whole batch when one change is bad', () => {
    expect(() =>
      repository.applyBatch([
        { op: 'put', collection: 'towers', id: 'a', document: {} },
        { op: 'merge', collection: 'towers', id: 'b' },
      ]),
    ).toThrow(/cannot apply: merge/);
    expect(journal.count()).toBe(0);
  });
});
