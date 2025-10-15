import {
  existsSync,
  mkdirSync,
  readFileSync,
  renameSync,
  unlinkSync,
  writeFileSync,
} from 'node:fs';
import { dirname } from 'node:path';
import { checksum } from './codec.js';
import { StorageError, ValidationError } from '../errors.js';

/**
 * A snapshot of the state, so that start up does not have to replay the whole
 * journal every time.
 *
 * A snapshot records how far through the journal it was taken, and the loader
 * replays only what came after. Getting that number wrong in either direction
 * is the whole risk: too low and records are applied twice, too high and they
 * are lost. So it is written inside the snapshot rather than inferred from the
 * file name or the modification time.
 *
 * The file is written to a temporary name and then renamed, which is atomic on
 * every filesystem this runs on. A snapshot half written is a snapshot that
 * never existed.
 */

/** The version stamped into every snapshot. */
export const SNAPSHOT_VERSION = 1;

export class Snapshot {
  /**
   * @param {string} path
   */
  constructor(path) {
    if (typeof path !== 'string' || path.length === 0) {
      throw new ValidationError('a snapshot needs a path');
    }
    this.path = path;
  }

  /**
   * @returns {boolean}
   */
  exists() {
    return existsSync(this.path);
  }

  /**
   * Write the state out.
   *
   * @param {unknown} state
   * @param {number} through how many journal records the state includes
   * @returns {Snapshot} this
   */
  write(state, through) {
    if (!Number.isInteger(through) || through < 0) {
      throw new ValidationError(`${through} is not a number of records`);
    }
    const body = JSON.stringify(state);
    if (body === undefined) {
      throw new ValidationError('this state cannot be written as JSON');
    }
    const document = {
      version: SNAPSHOT_VERSION,
      through,
      checksum: checksum(body),
      state,
    };
    const directory = dirname(this.path);
    if (!existsSync(directory)) {
      mkdirSync(directory, { recursive: true });
    }
    const temporary = `${this.path}.writing`;
    try {
      writeFileSync(temporary, JSON.stringify(document), 'utf8');
      renameSync(temporary, this.path);
    } catch (error) {
      throw new StorageError(`cannot write the snapshot at ${this.path}: ${error.message}`, {
        cause: error,
      });
    }
    return this;
  }

  /**
   * Read the state back, or null when there is no snapshot.
   *
   * @returns {{ state: unknown, through: number } | null}
   */
  read() {
    if (!this.exists()) {
      return null;
    }
    let document;
    try {
      document = JSON.parse(readFileSync(this.path, 'utf8'));
    } catch (error) {
      throw new StorageError(`the snapshot at ${this.path} cannot be read: ${error.message}`, {
        cause: error,
      });
    }
    if (document === null || typeof document !== 'object') {
      throw new StorageError(`the snapshot at ${this.path} is not a snapshot`);
    }
    if (document.version !== SNAPSHOT_VERSION) {
      throw new StorageError(
        `the snapshot at ${this.path} is version ${document.version} and this reads version ${SNAPSHOT_VERSION}`,
        { details: { found: document.version, expected: SNAPSHOT_VERSION } },
      );
    }
    if (!Number.isInteger(document.through) || document.through < 0) {
      throw new StorageError(`the snapshot at ${this.path} does not say how far it goes`);
    }
    const found = checksum(JSON.stringify(document.state));
    if (found !== document.checksum) {
      throw new StorageError(`the snapshot at ${this.path} is damaged`, {
        details: { found, wanted: document.checksum },
      });
    }
    return { state: document.state, through: document.through };
  }

  /**
   * Throw the snapshot away, so that the next start up replays everything.
   *
   * @returns {boolean} true when there was one to remove
   */
  remove() {
    if (!this.exists()) {
      return false;
    }
    try {
      unlinkSync(this.path);
    } catch (error) {
      throw new StorageError(`cannot remove the snapshot at ${this.path}: ${error.message}`, {
        cause: error,
      });
    }
    return true;
  }
}

/**
 * Load a state from a snapshot and whatever the journal has added since.
 *
 * @template T
 * @param {{
 *   journal: import('./journal.js').Journal,
 *   snapshot: Snapshot,
 *   step: (state: T, record: unknown, index: number) => T,
 *   initial: () => T
 * }} options
 * @returns {{ state: T, fromSnapshot: boolean, replayed: number }}
 */
export function loadState(options) {
  if (options === null || typeof options !== 'object') {
    throw new ValidationError('loading a state needs a journal and a snapshot');
  }
  const { journal, snapshot, step, initial } = options;
  if (typeof step !== 'function' || typeof initial !== 'function') {
    throw new ValidationError('loading a state needs a fold and a starting point');
  }
  const saved = snapshot.read();
  const records = journal.read();
  let state = saved === null ? initial() : saved.state;
  const from = saved === null ? 0 : saved.through;
  if (from > records.length) {
    throw new StorageError(
      `the snapshot claims ${from} records but the journal holds ${records.length}`,
      { details: { snapshot: from, journal: records.length } },
    );
  }
  for (let index = from; index < records.length; index += 1) {
    state = step(state, records[index], index);
  }
  return { state, fromSnapshot: saved !== null, replayed: records.length - from };
}
