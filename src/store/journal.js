import {
  appendFileSync,
  existsSync,
  mkdirSync,
  readFileSync,
  statSync,
  writeFileSync,
} from 'node:fs';
import { dirname } from 'node:path';
import { encode, encodeAll, decodeAll } from './codec.js';
import { StorageError, ValidationError } from '../errors.js';

/**
 * An append only journal on disk.
 *
 * Everything the service knows is a fold over this file. Nothing is ever
 * changed in place, which makes the failure modes small: a write either lands
 * whole or leaves a line the checksum rejects, and either way the records
 * before it are untouched.
 *
 * Writes are synchronous on purpose. A tower's journal is a few thousand lines
 * and the simplicity of knowing the record is on disk when the call returns is
 * worth more than the throughput of doing it any other way.
 */
export class Journal {
  /**
   * @param {string} path
   */
  constructor(path) {
    if (typeof path !== 'string' || path.length === 0) {
      throw new ValidationError('a journal needs a path');
    }
    this.path = path;
  }

  /**
   * True when the file is there.
   *
   * @returns {boolean}
   */
  exists() {
    return existsSync(this.path);
  }

  /**
   * How many bytes the journal takes up, or zero when it is not there yet.
   *
   * @returns {number}
   */
  get bytes() {
    return this.exists() ? statSync(this.path).size : 0;
  }

  /**
   * Add one record.
   *
   * @param {unknown} record
   * @returns {Journal} this
   */
  append(record) {
    this.ensureDirectory();
    try {
      appendFileSync(this.path, `${encode(record)}\n`, 'utf8');
    } catch (error) {
      throw new StorageError(`cannot write to ${this.path}: ${error.message}`, { cause: error });
    }
    return this;
  }

  /**
   * Add several records in one write, so that a batch either lands or does not.
   *
   * @param {Iterable<unknown>} records
   * @returns {number} how many were written
   */
  appendAll(records) {
    const text = encodeAll(records);
    if (text.length === 0) {
      return 0;
    }
    this.ensureDirectory();
    try {
      appendFileSync(this.path, text, 'utf8');
    } catch (error) {
      throw new StorageError(`cannot write to ${this.path}: ${error.message}`, { cause: error });
    }
    return text.split('\n').length - 1;
  }

  /**
   * Every record, in the order they were written.
   *
   * @returns {unknown[]}
   */
  read() {
    if (!this.exists()) {
      return [];
    }
    let text;
    try {
      text = readFileSync(this.path, 'utf8');
    } catch (error) {
      throw new StorageError(`cannot read ${this.path}: ${error.message}`, { cause: error });
    }
    return decodeAll(text);
  }

  /**
   * Fold the journal into whatever the records mean.
   *
   * @template T
   * @param {(state: T, record: unknown, index: number) => T} step
   * @param {T} initial
   * @returns {T}
   */
  replay(step, initial) {
    if (typeof step !== 'function') {
      throw new ValidationError('replaying a journal needs a function to fold with');
    }
    let state = initial;
    this.read().forEach((record, index) => {
      state = step(state, record, index);
    });
    return state;
  }

  /**
   * How many records the journal holds.
   *
   * @returns {number}
   */
  count() {
    return this.read().length;
  }

  /**
   * Replace the whole journal with a new set of records. This is how
   * compaction lands, and it is the only operation here that is not an append.
   *
   * @param {Iterable<unknown>} records
   * @returns {number} how many were written
   */
  rewrite(records) {
    const text = encodeAll(records);
    this.ensureDirectory();
    try {
      writeFileSync(this.path, text, 'utf8');
    } catch (error) {
      throw new StorageError(`cannot rewrite ${this.path}: ${error.message}`, { cause: error });
    }
    return text.length === 0 ? 0 : text.split('\n').length - 1;
  }

  /**
   * Make sure the directory the journal lives in is there.
   */
  ensureDirectory() {
    const directory = dirname(this.path);
    if (!existsSync(directory)) {
      mkdirSync(directory, { recursive: true });
    }
  }
}
