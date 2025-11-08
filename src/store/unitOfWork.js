import { Repository, PUT, DELETE } from './repository.js';
import { assertNonEmptyString } from '../util/assert.js';
import { ValidationError } from '../errors.js';

/**
 * Several changes that have to land together.
 *
 * Filing a performance touches more than one document: the record itself, and
 * the first in a method for whoever it was a first for. Writing those one at a
 * time leaves a window where the journal holds half the change, and a process
 * that dies in that window comes back up with a state nobody intended.
 *
 * A unit of work collects the changes and writes them in a single append, which
 * either lands whole or does not land at all.
 *
 * Reads inside the unit see the pending changes, so code that writes a document
 * and then reads it back gets what it wrote rather than what is on disk.
 */
export class UnitOfWork {
  /**
   * @param {Repository} repository
   */
  constructor(repository) {
    if (!(repository instanceof Repository)) {
      throw new ValidationError('a unit of work needs a Repository');
    }
    this.repository = repository;
    /** @type {unknown[]} */
    this.changes = [];
    this.committed = false;
  }

  /**
   * @returns {number} how many changes are waiting
   */
  get size() {
    return this.changes.length;
  }

  /**
   * File a document as part of this unit.
   *
   * @param {string} collection
   * @param {string} id
   * @param {unknown} document
   * @returns {UnitOfWork} this
   */
  put(collection, id, document) {
    this.assertOpen();
    assertNonEmptyString(collection, 'a collection name');
    assertNonEmptyString(id, 'a document id');
    if (document === undefined) {
      throw new ValidationError('there is no document to file');
    }
    this.changes.push({ op: PUT, collection, id, document });
    return this;
  }

  /**
   * Take a document out as part of this unit.
   *
   * @param {string} collection
   * @param {string} id
   * @returns {UnitOfWork} this
   */
  delete(collection, id) {
    this.assertOpen();
    assertNonEmptyString(collection, 'a collection name');
    assertNonEmptyString(id, 'a document id');
    this.changes.push({ op: DELETE, collection, id });
    return this;
  }

  /**
   * The document as it will be once this unit lands.
   *
   * @param {string} collection
   * @param {string} id
   * @returns {unknown}
   */
  find(collection, id) {
    for (let index = this.changes.length - 1; index >= 0; index -= 1) {
      const change = this.changes[index];
      if (change.collection !== collection || change.id !== id) {
        continue;
      }
      return change.op === PUT ? change.document : undefined;
    }
    return this.repository.find(collection, id);
  }

  /**
   * @param {string} collection
   * @param {string} id
   * @returns {boolean}
   */
  has(collection, id) {
    return this.find(collection, id) !== undefined;
  }

  /**
   * Write everything at once.
   *
   * @returns {number} how many changes landed
   */
  commit() {
    this.assertOpen();
    this.committed = true;
    if (this.changes.length === 0) {
      return 0;
    }
    const landed = this.repository.applyBatch(this.changes);
    this.changes = [];
    return landed;
  }

  /**
   * Throw the changes away.
   *
   * @returns {number} how many were thrown away
   */
  rollback() {
    const thrown = this.changes.length;
    this.changes = [];
    this.committed = true;
    return thrown;
  }

  /**
   */
  assertOpen() {
    if (this.committed) {
      throw new ValidationError('this unit of work has already been finished');
    }
  }
}

/**
 * Run some work in a unit, committing when it returns and rolling back when it
 * throws.
 *
 * @template T
 * @param {Repository} repository
 * @param {(unit: UnitOfWork) => T} work
 * @returns {T}
 */
export function inUnitOfWork(repository, work) {
  if (typeof work !== 'function') {
    throw new ValidationError('a unit of work needs something to do');
  }
  const unit = new UnitOfWork(repository);
  let answer;
  try {
    answer = work(unit);
  } catch (error) {
    unit.rollback();
    throw error;
  }
  unit.commit();
  return answer;
}
