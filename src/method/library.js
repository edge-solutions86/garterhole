import { Method } from './method.js';
import { titleOf } from './title.js';
import { ConflictError, NotFoundError, ValidationError } from '../errors.js';
import { slugify } from '../util/text.js';

/**
 * A collection of methods, held in memory and keyed by title.
 *
 * The library refuses two methods with the same title, and it refuses two
 * methods that ring the same rows under different titles. The second check is
 * the one that matters: a tower library that quietly holds Cambridge twice
 * under two spellings will produce a splicing table that cannot be rung.
 */
export class MethodLibrary {
  constructor() {
    /** @type {Map<string, Method>} */
    this.byTitle = new Map();
    /** @type {Map<string, string>} */
    this.titleByNotation = new Map();
  }

  /**
   * How many methods the library holds.
   *
   * @returns {number}
   */
  get size() {
    return this.byTitle.size;
  }

  /**
   * Add a method. The title is worked out from the method rather than supplied,
   * so a caller cannot file Cambridge under the wrong class.
   *
   * @param {Method} method
   * @returns {string} the title it was filed under
   */
  add(method) {
    if (!(method instanceof Method)) {
      throw new ValidationError('only a Method can go into the library');
    }
    const title = titleOf(method);
    if (this.byTitle.has(title)) {
      throw new ConflictError(`the library already holds ${JSON.stringify(title)}`, {
        details: { title },
      });
    }
    const key = notationKey(method);
    const existing = this.titleByNotation.get(key);
    if (existing !== undefined) {
      throw new ConflictError(
        `${JSON.stringify(title)} rings the same rows as ${JSON.stringify(existing)}`,
        { details: { title, existing } },
      );
    }
    this.byTitle.set(title, method);
    this.titleByNotation.set(key, title);
    return title;
  }

  /**
   * Add several methods, stopping at the first one that clashes.
   *
   * @param {Iterable<Method>} methods
   * @returns {string[]} the titles added
   */
  addAll(methods) {
    const titles = [];
    for (const method of methods) {
      titles.push(this.add(method));
    }
    return titles;
  }

  /**
   * @param {string} title
   * @returns {boolean}
   */
  has(title) {
    return this.byTitle.has(title);
  }

  /**
   * @param {string} title
   * @returns {Method}
   */
  get(title) {
    const method = this.byTitle.get(title);
    if (method === undefined) {
      throw new NotFoundError('method', title);
    }
    return method;
  }

  /**
   * The method with a title, or undefined. Used where a miss is ordinary rather
   * than exceptional, such as resolving the methods named in a composition.
   *
   * @param {string} title
   * @returns {Method | undefined}
   */
  find(title) {
    return this.byTitle.get(title);
  }

  /**
   * The method that rings these rows, whatever it is called here.
   *
   * @param {Method} method
   * @returns {Method | undefined}
   */
  findByNotation(method) {
    const title = this.titleByNotation.get(notationKey(method));
    return title === undefined ? undefined : this.byTitle.get(title);
  }

  /**
   * Take a method out.
   *
   * @param {string} title
   * @returns {Method} the method that was removed
   */
  remove(title) {
    const method = this.get(title);
    this.byTitle.delete(title);
    this.titleByNotation.delete(notationKey(method));
    return method;
  }

  /**
   * Every title, sorted.
   *
   * @returns {string[]}
   */
  titles() {
    return [...this.byTitle.keys()].sort();
  }

  /**
   * Every method, in title order.
   *
   * @returns {Method[]}
   */
  methods() {
    return this.titles().map((title) => this.byTitle.get(title));
  }

  /**
   * A short identifier for a title, safe in a URL.
   *
   * @param {string} title
   * @returns {string}
   */
  static slugFor(title) {
    return slugify(title);
  }

  /**
   * @returns {IterableIterator<Method>}
   */
  [Symbol.iterator]() {
    return this.methods()[Symbol.iterator]();
  }
}

/**
 * Two methods ring the same rows when they are on the same stage and have the
 * same changes written out in full.
 *
 * @param {Method} method
 * @returns {string}
 */
function notationKey(method) {
  return `${method.stage}:${method.fullNotation}`;
}
