import { Journal } from './journal.js';
import { loadState } from './snapshot.js';
import { assertNonEmptyString } from '../util/assert.js';
import { NotFoundError, StorageError, ValidationError } from '../errors.js';

/**
 * The service's state, kept as a fold over the journal.
 *
 * Everything is a document with an identifier, filed in a named collection.
 * That is a deliberately thin model: the towers, the band, the method library
 * and the performance records all have the same shape as far as storage is
 * concerned, and the domain classes are rebuilt from the documents when they
 * are wanted rather than being stored as themselves.
 *
 * There are only two operations in the journal, put and delete, and the state
 * is the result of applying them in order. That means the state can always be
 * rebuilt from the file, and it means a bug in this class cannot lose data that
 * was written before the bug existed.
 */

/** The operations a journal record may carry. */
export const PUT = 'put';
export const DELETE = 'delete';

export class Repository {
  /**
   * @param {{ journal: Journal, snapshot?: import('./snapshot.js').Snapshot }} options
   */
  constructor(options) {
    if (options === null || typeof options !== 'object') {
      throw new ValidationError('a repository needs a journal');
    }
    if (!(options.journal instanceof Journal)) {
      throw new ValidationError('a repository needs a Journal');
    }
    this.journal = options.journal;
    this.snapshot = options.snapshot ?? null;
    /** @type {Map<string, Map<string, unknown>>} */
    this.collections = new Map();
    this.applied = 0;
    this.load();
  }

  /**
   * Rebuild the state from the snapshot and the journal.
   *
   * @returns {{ fromSnapshot: boolean, replayed: number }}
   */
  load() {
    const step = (state, record, index) => {
      applyRecord(state, record, index);
      return state;
    };
    if (this.snapshot === null) {
      const state = new Map();
      const records = this.journal.read();
      records.forEach((record, index) => applyRecord(state, record, index));
      this.collections = state;
      this.applied = records.length;
      return { fromSnapshot: false, replayed: records.length };
    }
    const loaded = loadState({
      journal: this.journal,
      snapshot: this.snapshot,
      step: (state, record, index) => step(toMap(state), record, index),
      initial: () => new Map(),
    });
    this.collections = toMap(loaded.state);
    this.applied = this.journal.count();
    return { fromSnapshot: loaded.fromSnapshot, replayed: loaded.replayed };
  }

  /**
   * File a document.
   *
   * @param {string} collection
   * @param {string} id
   * @param {unknown} document
   * @returns {Repository} this
   */
  put(collection, id, document) {
    assertNonEmptyString(collection, 'a collection name');
    assertNonEmptyString(id, 'a document id');
    if (document === undefined) {
      throw new ValidationError('there is no document to file');
    }
    this.applyBatch([{ op: PUT, collection, id, document }]);
    return this;
  }

  /**
   * Take a document out.
   *
   * @param {string} collection
   * @param {string} id
   * @returns {boolean} true when there was one to remove
   */
  delete(collection, id) {
    assertNonEmptyString(collection, 'a collection name');
    assertNonEmptyString(id, 'a document id');
    if (!this.has(collection, id)) {
      return false;
    }
    this.applyBatch([{ op: DELETE, collection, id }]);
    return true;
  }

  /**
   * Write several changes as one append, so that they land together or not at
   * all. Every write goes through here, which is what makes a single put and a
   * whole unit of work follow the same path.
   *
   * @param {readonly unknown[]} changes
   * @returns {number} how many landed
   */
  applyBatch(changes) {
    if (!Array.isArray(changes)) {
      throw new ValidationError('a batch of changes must be an array');
    }
    if (changes.length === 0) {
      return 0;
    }
    changes.forEach((change, offset) => checkRecord(change, this.applied + offset));
    this.journal.appendAll(changes);
    changes.forEach((change, offset) =>
      applyRecord(this.collections, change, this.applied + offset),
    );
    this.applied += changes.length;
    return changes.length;
  }

  /**
   * @param {string} collection
   * @param {string} id
   * @returns {boolean}
   */
  has(collection, id) {
    return this.collections.get(collection)?.has(id) ?? false;
  }

  /**
   * @param {string} collection
   * @param {string} id
   * @returns {unknown}
   */
  get(collection, id) {
    const found = this.collections.get(collection)?.get(id);
    if (found === undefined) {
      throw new NotFoundError(collection, id);
    }
    return found;
  }

  /**
   * The document, or undefined where a miss is ordinary.
   *
   * @param {string} collection
   * @param {string} id
   * @returns {unknown}
   */
  find(collection, id) {
    return this.collections.get(collection)?.get(id);
  }

  /**
   * Everything in a collection, in id order.
   *
   * @param {string} collection
   * @returns {{ id: string, document: unknown }[]}
   */
  all(collection) {
    const held = this.collections.get(collection);
    if (held === undefined) {
      return [];
    }
    return [...held.entries()]
      .sort((left, right) => left[0].localeCompare(right[0]))
      .map(([id, document]) => ({ id, document }));
  }

  /**
   * How many documents a collection holds.
   *
   * @param {string} collection
   * @returns {number}
   */
  count(collection) {
    return this.collections.get(collection)?.size ?? 0;
  }

  /**
   * Every collection with anything in it.
   *
   * @returns {string[]}
   */
  collectionNames() {
    return [...this.collections.keys()].filter((name) => this.count(name) > 0).sort();
  }

  /**
   * Write a snapshot of where the journal has got to.
   *
   * @returns {boolean} true when a snapshot was written
   */
  save() {
    if (this.snapshot === null) {
      return false;
    }
    this.snapshot.write(fromMap(this.collections), this.applied);
    return true;
  }
}

/**
 * Check that a record is a change this can apply, without applying it. Doing
 * this before anything is written is what lets a bad batch be refused rather
 * than half written.
 *
 * @param {unknown} record
 * @param {number} index
 */
function checkRecord(record, index) {
  if (record === null || typeof record !== 'object') {
    throw new StorageError(`record ${index} is not a change`, { details: { index } });
  }
  const { op, collection, id } = record;
  if (typeof collection !== 'string' || typeof id !== 'string') {
    throw new StorageError(`record ${index} does not say what it changes`, { details: { index } });
  }
  if (op !== PUT && op !== DELETE) {
    throw new StorageError(`record ${index} has an operation this cannot apply: ${op}`, {
      details: { index, op },
    });
  }
}

/**
 * @param {Map<string, Map<string, unknown>>} state
 * @param {unknown} record
 * @param {number} index
 */
function applyRecord(state, record, index) {
  checkRecord(record, index);
  const { op, collection, id } = record;
  if (op === PUT) {
    let held = state.get(collection);
    if (held === undefined) {
      held = new Map();
      state.set(collection, held);
    }
    held.set(id, record.document);
    return;
  }
  state.get(collection)?.delete(id);
}

/**
 * @param {unknown} state
 * @returns {Map<string, Map<string, unknown>>}
 */
function toMap(state) {
  if (state instanceof Map) {
    return state;
  }
  const rebuilt = new Map();
  for (const [collection, documents] of Object.entries(state ?? {})) {
    rebuilt.set(collection, new Map(Object.entries(documents)));
  }
  return rebuilt;
}

/**
 * @param {Map<string, Map<string, unknown>>} state
 * @returns {Record<string, Record<string, unknown>>}
 */
function fromMap(state) {
  const plain = {};
  for (const [collection, documents] of state) {
    plain[collection] = Object.fromEntries(documents);
  }
  return plain;
}
