import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { Snapshot, loadState, SNAPSHOT_VERSION } from '../../src/store/snapshot.js';
import { Journal } from '../../src/store/journal.js';
import { StorageError, ValidationError } from '../../src/errors.js';

/** @type {string} */
let directory;
/** @type {Snapshot} */
let snapshot;
/** @type {Journal} */
let journal;

beforeEach(() => {
  directory = mkdtempSync(join(tmpdir(), 'garterhole-'));
  snapshot = new Snapshot(join(directory, 'state.json'));
  journal = new Journal(join(directory, 'records.jsonl'));
});

afterEach(() => {
  rmSync(directory, { recursive: true, force: true });
});

const add = (state, record) => state + record.n;

describe('Snapshot', () => {
  it('is not there until something is written', () => {
    expect(snapshot.exists()).toBe(false);
    expect(snapshot.read()).toBeNull();
  });

  it('writes and reads a state back', () => {
    snapshot.write({ total: 7 }, 3);
    expect(snapshot.read()).toEqual({ state: { total: 7 }, through: 3 });
  });

  it('leaves no half written file behind', () => {
    snapshot.write({ total: 7 }, 3);
    expect(snapshot.exists()).toBe(true);
    expect(() => snapshot.read()).not.toThrow();
  });

  it('overwrites an earlier snapshot', () => {
    snapshot.write({ total: 1 }, 1);
    snapshot.write({ total: 2 }, 2);
    expect(snapshot.read().state.total).toBe(2);
  });

  it('refuses a path that is not one', () => {
    expect(() => new Snapshot('')).toThrow(/needs a path/);
  });

  it('refuses a record count that is not one', () => {
    expect(() => snapshot.write({}, -1)).toThrow(/is not a number of records/);
    expect(() => snapshot.write({}, 1.5)).toThrow(ValidationError);
  });

  it('refuses a state JSON cannot hold', () => {
    expect(() => snapshot.write(() => 1, 0)).toThrow(/cannot be written as JSON/);
  });

  it('removes itself', () => {
    snapshot.write({ total: 1 }, 1);
    expect(snapshot.remove()).toBe(true);
    expect(snapshot.exists()).toBe(false);
    expect(snapshot.remove()).toBe(false);
  });
});

describe('damaged snapshots', () => {
  it('reports a file that is not a snapshot', () => {
    writeFileSync(snapshot.path, 'not json', 'utf8');
    expect(() => snapshot.read()).toThrow(/cannot be read/);
  });

  it('reports a version it does not know', () => {
    writeFileSync(
      snapshot.path,
      JSON.stringify({ version: 99, through: 0, checksum: 'x', state: {} }),
      'utf8',
    );
    expect(() => snapshot.read()).toThrow(/is version 99/);
  });

  it('reports a snapshot with no record count', () => {
    writeFileSync(
      snapshot.path,
      JSON.stringify({ version: SNAPSHOT_VERSION, checksum: 'x', state: {} }),
      'utf8',
    );
    expect(() => snapshot.read()).toThrow(/does not say how far it goes/);
  });

  it('reports a state that does not match its checksum', () => {
    writeFileSync(
      snapshot.path,
      JSON.stringify({
        version: SNAPSHOT_VERSION,
        through: 0,
        checksum: '00000000',
        state: { a: 1 },
      }),
      'utf8',
    );
    expect(() => snapshot.read()).toThrow(StorageError);
    expect(() => snapshot.read()).toThrow(/is damaged/);
  });

  it('reports a file holding something that is not an object', () => {
    writeFileSync(snapshot.path, '42', 'utf8');
    expect(() => snapshot.read()).toThrow(/is not a snapshot/);
  });
});

describe('loadState', () => {
  beforeEach(() => {
    journal.appendAll([{ n: 1 }, { n: 2 }, { n: 3 }]);
  });

  it('replays the whole journal when there is no snapshot', () => {
    const loaded = loadState({ journal, snapshot, step: add, initial: () => 0 });
    expect(loaded.state).toBe(6);
    expect(loaded.fromSnapshot).toBe(false);
    expect(loaded.replayed).toBe(3);
  });

  it('replays only what came after a snapshot', () => {
    snapshot.write(3, 2);
    const loaded = loadState({ journal, snapshot, step: add, initial: () => 0 });
    expect(loaded.state).toBe(6);
    expect(loaded.fromSnapshot).toBe(true);
    expect(loaded.replayed).toBe(1);
  });

  it('replays nothing when the snapshot is up to date', () => {
    snapshot.write(6, 3);
    expect(loadState({ journal, snapshot, step: add, initial: () => 0 }).replayed).toBe(0);
  });

  it('refuses a snapshot from further ahead than the journal', () => {
    snapshot.write(6, 9);
    expect(() => loadState({ journal, snapshot, step: add, initial: () => 0 })).toThrow(
      'the snapshot claims 9 records but the journal holds 3',
    );
  });

  it('refuses options it cannot use', () => {
    expect(() => loadState(null)).toThrow(/needs a journal and a snapshot/);
    expect(() => loadState({ journal, snapshot, step: null, initial: () => 0 })).toThrow(
      /needs a fold and a starting point/,
    );
  });
});
