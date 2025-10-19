import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { Lock } from '../../src/store/lock.js';
import { ConflictError, ValidationError } from '../../src/errors.js';

/** @type {string} */
let directory;
/** @type {string} */
let path;
let clock = 1000;

beforeEach(() => {
  directory = mkdtempSync(join(tmpdir(), 'garterhole-'));
  path = join(directory, 'lock');
  clock = 1000;
});

afterEach(() => {
  rmSync(directory, { recursive: true, force: true });
});

const lock = (pid = 1234) => new Lock(path, { now: () => clock, pid });

describe('taking a lock', () => {
  it('is not held until it is taken', () => {
    expect(lock().exists()).toBe(false);
    expect(lock().holder()).toBeNull();
    expect(lock().age()).toBeNull();
  });

  it('creates the file and records who took it', () => {
    lock().acquire();
    expect(lock().holder()).toEqual({ pid: 1234, taken: 1000 });
  });

  it('makes the directory it needs', () => {
    const nested = new Lock(join(directory, 'deep', 'lock'), { now: () => clock });
    expect(() => nested.acquire()).not.toThrow();
  });

  it('refuses to take a lock somebody else holds', () => {
    lock(1234).acquire();
    expect(() => lock(5678).acquire()).toThrow(ConflictError);
    expect(() => lock(5678).acquire()).toThrow(/held by process 1234/);
  });

  it('refuses a path that is not one', () => {
    expect(() => new Lock('')).toThrow(/a lock needs a path/);
  });
});

describe('giving a lock up', () => {
  it('removes the file', () => {
    const holder = lock();
    holder.acquire();
    expect(holder.release()).toBe(true);
    expect(holder.exists()).toBe(false);
  });

  it('does nothing when it never held the lock', () => {
    lock(1234).acquire();
    expect(lock(5678).release()).toBe(false);
    expect(lock().exists()).toBe(true);
  });

  it('lets the lock be taken again afterwards', () => {
    const first = lock();
    first.acquire();
    first.release();
    expect(() => lock(5678).acquire()).not.toThrow();
  });
});

describe('reading a lock', () => {
  it('measures how long it has been held', () => {
    lock().acquire();
    clock = 5000;
    expect(lock().age()).toBe(4000);
  });

  it('gives null for a lock file it cannot read', () => {
    writeFileSync(path, 'not json', 'utf8');
    expect(lock().holder()).toBeNull();
    expect(lock().age()).toBeNull();
  });

  it('gives null for a lock file with the wrong shape', () => {
    writeFileSync(path, JSON.stringify({ pid: 'me' }), 'utf8');
    expect(lock().holder()).toBeNull();
  });
});

describe('breaking a stale lock', () => {
  it('breaks one older than the age given', () => {
    lock().acquire();
    clock = 60000;
    expect(lock().breakStale(30000)).toBe(true);
    expect(lock().exists()).toBe(false);
  });

  it('leaves a fresh lock alone', () => {
    lock().acquire();
    clock = 1500;
    expect(lock().breakStale(30000)).toBe(false);
    expect(lock().exists()).toBe(true);
  });

  it('does nothing when there is no lock', () => {
    expect(lock().breakStale(0)).toBe(false);
  });

  it('refuses an age that is not one', () => {
    expect(() => lock().breakStale(-1)).toThrow(ValidationError);
  });
});

describe('around', () => {
  it('holds the lock while the work runs and gives it up after', () => {
    const holder = lock();
    const seen = holder.around(() => holder.exists());
    expect(seen).toBe(true);
    expect(holder.exists()).toBe(false);
  });

  it('gives the lock up even when the work throws', () => {
    const holder = lock();
    expect(() =>
      holder.around(() => {
        throw new Error('no');
      }),
    ).toThrow('no');
    expect(holder.exists()).toBe(false);
  });

  it('refuses to be given something that is not work', () => {
    expect(() => lock().around(null)).toThrow(/something to do while it is held/);
  });
});
