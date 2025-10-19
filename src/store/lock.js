import {
  existsSync,
  mkdirSync,
  openSync,
  closeSync,
  readFileSync,
  unlinkSync,
  writeSync,
} from 'node:fs';
import { dirname } from 'node:path';
import process from 'node:process';
import { ConflictError, StorageError, ValidationError } from '../errors.js';

/**
 * A lock file, so that two copies of the service cannot write to one journal.
 *
 * The lock is a file created with the exclusive flag, which the operating
 * system will only do when the file is not already there. That makes taking the
 * lock a single atomic operation rather than a check followed by a write, which
 * is the version that lets two processes both think they won.
 *
 * A lock left behind by a process that died is a real problem, so the file says
 * which process holds it and when it was taken. A stale lock can then be broken
 * deliberately, which is a different thing from breaking one by accident.
 */
export class Lock {
  /**
   * @param {string} path
   * @param {{ now?: () => number, pid?: number }} [options]
   */
  constructor(path, options = {}) {
    if (typeof path !== 'string' || path.length === 0) {
      throw new ValidationError('a lock needs a path');
    }
    this.path = path;
    this.now = options.now ?? (() => Date.now());
    this.pid = options.pid ?? process.pid;
    this.held = false;
  }

  /**
   * @returns {boolean}
   */
  exists() {
    return existsSync(this.path);
  }

  /**
   * Take the lock.
   *
   * @returns {Lock} this
   */
  acquire() {
    const directory = dirname(this.path);
    if (!existsSync(directory)) {
      mkdirSync(directory, { recursive: true });
    }
    let handle;
    try {
      handle = openSync(this.path, 'wx');
    } catch (error) {
      if (error.code === 'EEXIST') {
        const holder = this.holder();
        throw new ConflictError(
          `the lock at ${this.path} is held by process ${holder === null ? 'unknown' : holder.pid}`,
          { details: { path: this.path, holder } },
        );
      }
      throw new StorageError(`cannot take the lock at ${this.path}: ${error.message}`, {
        cause: error,
      });
    }
    try {
      const body = JSON.stringify({ pid: this.pid, taken: this.now() });
      writeThrough(handle, body);
    } finally {
      closeSync(handle);
    }
    this.held = true;
    return this;
  }

  /**
   * Who holds the lock, or null when nobody does or the file is unreadable.
   *
   * @returns {{ pid: number, taken: number } | null}
   */
  holder() {
    if (!this.exists()) {
      return null;
    }
    try {
      const parsed = JSON.parse(readFileSync(this.path, 'utf8'));
      if (!Number.isInteger(parsed.pid) || !Number.isInteger(parsed.taken)) {
        return null;
      }
      return { pid: parsed.pid, taken: parsed.taken };
    } catch {
      return null;
    }
  }

  /**
   * How long the lock has been held, or null when nobody holds it.
   *
   * @returns {number | null}
   */
  age() {
    const holder = this.holder();
    return holder === null ? null : this.now() - holder.taken;
  }

  /**
   * Give the lock up. Only the holder may do this, which stops one process
   * releasing another's lock by accident.
   *
   * @returns {boolean} true when there was a lock to give up
   */
  release() {
    if (!this.held) {
      return false;
    }
    this.held = false;
    if (!this.exists()) {
      return false;
    }
    try {
      unlinkSync(this.path);
    } catch (error) {
      throw new StorageError(`cannot release the lock at ${this.path}: ${error.message}`, {
        cause: error,
      });
    }
    return true;
  }

  /**
   * Break a lock left behind by a process that is gone. The caller has to say
   * how old a lock has to be before it counts as abandoned, because only the
   * caller knows how long its own work takes.
   *
   * @param {number} olderThan in milliseconds
   * @returns {boolean} true when a lock was broken
   */
  breakStale(olderThan) {
    if (typeof olderThan !== 'number' || olderThan < 0) {
      throw new ValidationError(`${olderThan} is not an age in milliseconds`);
    }
    const age = this.age();
    if (age === null || age < olderThan) {
      return false;
    }
    unlinkSync(this.path);
    return true;
  }

  /**
   * Run something with the lock held, giving it up afterwards whatever happens.
   *
   * @template T
   * @param {() => T} work
   * @returns {T}
   */
  around(work) {
    if (typeof work !== 'function') {
      throw new ValidationError('the lock needs something to do while it is held');
    }
    this.acquire();
    try {
      return work();
    } finally {
      this.release();
    }
  }
}

/**
 * Write the whole buffer, which a single write is not guaranteed to do.
 *
 * @param {number} handle
 * @param {string} body
 */
function writeThrough(handle, body) {
  const buffer = Buffer.from(body, 'utf8');
  let written = 0;
  while (written < buffer.length) {
    written += writeSync(handle, buffer, written, buffer.length - written);
  }
}
