import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdtempSync, rmSync, writeFileSync, readFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import {
  backup,
  verify,
  listBackups,
  prune,
  restore,
  newestBackup,
  backupName,
} from '../../src/store/backup.js';
import { Journal } from '../../src/store/journal.js';
import { StorageError, ValidationError } from '../../src/errors.js';

/** @type {string} */
let directory;
/** @type {Journal} */
let journal;
/** @type {string} */
let backups;
let moment = 0;

const clock = () => {
  moment += 1000;
  return new Date(moment);
};

beforeEach(() => {
  directory = mkdtempSync(join(tmpdir(), 'garterhole-'));
  journal = new Journal(join(directory, 'journal.jsonl'));
  backups = join(directory, 'backups');
  moment = 0;
  journal.appendAll([{ n: 1 }, { n: 2 }, { n: 3 }]);
});

afterEach(() => {
  rmSync(directory, { recursive: true, force: true });
});

describe('taking a backup', () => {
  it('copies the journal and counts what is in it', () => {
    const taken = backup({ journal, directory: backups, now: clock });
    expect(existsSync(taken.path)).toBe(true);
    expect(taken.records).toBe(3);
  });

  it('makes the directory it needs', () => {
    expect(existsSync(backups)).toBe(false);
    backup({ journal, directory: backups, now: clock });
    expect(existsSync(backups)).toBe(true);
  });

  it('names the file after the time it was taken', () => {
    const taken = backup({ journal, directory: backups, now: clock });
    expect(backupName(taken.path).startsWith('journal-')).toBe(true);
    expect(backupName(taken.path).endsWith('.jsonl')).toBe(true);
  });

  it('refuses when there is no journal to copy', () => {
    const missing = new Journal(join(directory, 'nothing.jsonl'));
    expect(() => backup({ journal: missing, directory: backups })).toThrow(
      'there is no journal to back up',
    );
  });

  it('refuses options it cannot use', () => {
    expect(() => backup(null)).toThrow(/needs a journal/);
    expect(() => backup({ journal: 'journal', directory: backups })).toThrow(/needs a Journal/);
    expect(() => backup({ journal, directory: '' })).toThrow(/needs a directory/);
  });
});

describe('verifying', () => {
  it('reads a backup back and counts it', () => {
    const taken = backup({ journal, directory: backups, now: clock });
    expect(verify(taken.path)).toBe(3);
  });

  it('refuses a backup that is not there', () => {
    expect(() => verify(join(backups, 'nothing.jsonl'))).toThrow(/there is no backup at/);
  });

  it('throws a damaged copy away rather than keeping it', () => {
    const taken = backup({ journal, directory: backups, now: clock });
    writeFileSync(taken.path, 'not a journal\n', 'utf8');
    expect(() => verify(taken.path)).toThrow(StorageError);
    expect(existsSync(taken.path)).toBe(false);
  });
});

describe('listing and pruning', () => {
  beforeEach(() => {
    backup({ journal, directory: backups, now: clock });
    backup({ journal, directory: backups, now: clock });
    backup({ journal, directory: backups, now: clock });
  });

  it('lists them newest first', () => {
    const listed = listBackups(backups);
    expect(listed).toHaveLength(3);
    expect(listed[0].name > listed[1].name).toBe(true);
  });

  it('gives nothing for a directory that is not there', () => {
    expect(listBackups(join(directory, 'nowhere'))).toEqual([]);
  });

  it('keeps only the newest few', () => {
    const removed = prune(backups, 2);
    expect(removed).toHaveLength(1);
    expect(listBackups(backups)).toHaveLength(2);
  });

  it('removes nothing when there are few enough', () => {
    expect(prune(backups, 5)).toEqual([]);
  });

  it('prunes as part of taking a backup', () => {
    const taken = backup({ journal, directory: backups, now: clock, keep: 2 });
    expect(taken.removed.length).toBeGreaterThan(0);
    expect(listBackups(backups)).toHaveLength(2);
  });

  it('refuses a policy that keeps nothing', () => {
    expect(() => prune(backups, 0)).toThrow(ValidationError);
  });

  it('names the newest', () => {
    expect(newestBackup(backups)).toBe(listBackups(backups)[0].path);
    expect(newestBackup(join(directory, 'nowhere'))).toBeNull();
  });
});

describe('restoring', () => {
  it('puts a backup back over the journal', () => {
    const taken = backup({ journal, directory: backups, now: clock });
    journal.rewrite([{ n: 9 }]);
    expect(restore({ journal, path: taken.path })).toBe(3);
    expect(journal.read()).toEqual([{ n: 1 }, { n: 2 }, { n: 3 }]);
  });

  it('restores into a journal that is not there yet', () => {
    const taken = backup({ journal, directory: backups, now: clock });
    const fresh = new Journal(join(directory, 'restored', 'journal.jsonl'));
    expect(restore({ journal: fresh, path: taken.path })).toBe(3);
    expect(fresh.count()).toBe(3);
  });

  it('refuses a restore it cannot do', () => {
    expect(() => restore({ journal: 'journal', path: 'x' })).toThrow(ValidationError);
    expect(() => restore({ journal, path: join(backups, 'nothing.jsonl') })).toThrow(StorageError);
  });

  it('leaves the journal alone when the backup is unreadable', () => {
    const taken = backup({ journal, directory: backups, now: clock });
    writeFileSync(taken.path, 'rubbish\n', 'utf8');
    const before = readFileSync(journal.path, 'utf8');
    expect(() => restore({ journal, path: taken.path })).toThrow(StorageError);
    expect(readFileSync(journal.path, 'utf8')).toBe(before);
  });
});
