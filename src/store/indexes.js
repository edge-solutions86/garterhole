import { Repository } from './repository.js';
import { assertNonEmptyString, assertFunction } from '../util/assert.js';
import { ValidationError } from '../errors.js';

/**
 * Secondary indexes over a collection.
 *
 * The repository can find a document by its identifier and nothing else, which
 * is all the journal supports. Everything else the service asks for, such as
 * every performance at a tower or every method at a stage, is a scan unless
 * somebody keeps an index.
 *
 * An index is a function from a document to the keys it should be filed under,
 * plus a map from key to identifiers. It is rebuilt from the repository rather
 * than maintained incrementally, because rebuilding is fast at the sizes this
 * runs at and an index that can drift is worse than no index.
 */
export class Index {
  /**
   * @param {{
   *   name: string,
   *   collection: string,
   *   keysOf: (document: unknown, id: string) => (string | number)[]
   * }} definition
   */
  constructor(definition) {
    if (definition === null || typeof definition !== 'object') {
      throw new ValidationError('an index needs a definition object');
    }
    assertNonEmptyString(definition.name, 'an index name');
    assertNonEmptyString(definition.collection, 'a collection name');
    assertFunction(definition.keysOf, 'the key function');
    this.name = definition.name;
    this.collection = definition.collection;
    this.keysOf = definition.keysOf;
    /** @type {Map<string, Set<string>>} */
    this.byKey = new Map();
  }

  /**
   * Build the index from what the repository holds now.
   *
   * @param {Repository} repository
   * @returns {Index} this
   */
  build(repository) {
    if (!(repository instanceof Repository)) {
      throw new ValidationError('an index is built from a Repository');
    }
    this.byKey = new Map();
    for (const { id, document } of repository.all(this.collection)) {
      for (const key of this.keysOf(document, id)) {
        const text = String(key);
        let bucket = this.byKey.get(text);
        if (bucket === undefined) {
          bucket = new Set();
          this.byKey.set(text, bucket);
        }
        bucket.add(id);
      }
    }
    return this;
  }

  /**
   * The identifiers filed under a key, in order.
   *
   * @param {string | number} key
   * @returns {string[]}
   */
  lookup(key) {
    const bucket = this.byKey.get(String(key));
    return bucket === undefined ? [] : [...bucket].sort();
  }

  /**
   * Every key the index holds, in order.
   *
   * @returns {string[]}
   */
  keys() {
    return [...this.byKey.keys()].sort();
  }

  /**
   * How many documents each key holds, largest first.
   *
   * @returns {{ key: string, count: number }[]}
   */
  counts() {
    return [...this.byKey.entries()]
      .map(([key, bucket]) => ({ key, count: bucket.size }))
      .sort((left, right) => right.count - left.count || left.key.localeCompare(right.key));
  }

  /**
   * @returns {number} how many keys are filed
   */
  get size() {
    return this.byKey.size;
  }
}

/**
 * A set of indexes over one repository, rebuilt together.
 */
export class IndexSet {
  /**
   * @param {Repository} repository
   */
  constructor(repository) {
    if (!(repository instanceof Repository)) {
      throw new ValidationError('an index set needs a Repository');
    }
    this.repository = repository;
    /** @type {Map<string, Index>} */
    this.indexes = new Map();
  }

  /**
   * Add an index and build it at once.
   *
   * @param {Index} index
   * @returns {IndexSet} this
   */
  add(index) {
    if (!(index instanceof Index)) {
      throw new ValidationError('only an Index can be added to an index set');
    }
    index.build(this.repository);
    this.indexes.set(index.name, index);
    return this;
  }

  /**
   * @param {string} name
   * @returns {Index}
   */
  get(name) {
    const found = this.indexes.get(name);
    if (found === undefined) {
      throw new ValidationError(`there is no index called ${JSON.stringify(name)}`);
    }
    return found;
  }

  /**
   * Look up through a named index.
   *
   * @param {string} name
   * @param {string | number} key
   * @returns {{ id: string, document: unknown }[]}
   */
  find(name, key) {
    const index = this.get(name);
    return index
      .lookup(key)
      .map((id) => ({ id, document: this.repository.get(index.collection, id) }));
  }

  /**
   * Rebuild every index, which is what a write does before anything reads.
   *
   * @returns {IndexSet} this
   */
  rebuild() {
    for (const index of this.indexes.values()) {
      index.build(this.repository);
    }
    return this;
  }

  /**
   * @returns {string[]}
   */
  names() {
    return [...this.indexes.keys()].sort();
  }
}
