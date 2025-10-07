import { Performance } from './performance.js';
import { squash } from '../util/text.js';
import { sortBy } from '../util/collections.js';
import { ConflictError, NotFoundError, ValidationError } from '../errors.js';

/**
 * A tower's performance records, held together.
 *
 * Duplicates are the thing this exists to stop. The same quarter peal gets
 * filed twice more often than anything else goes wrong with these records:
 * once by the conductor on the night and once by the secretary a week later.
 * They are caught on the identifier, which is built from the date, the tower,
 * the method and the length, so two genuinely different performances on the
 * same night are still both accepted.
 */
export class PerformanceCollection {
  constructor() {
    /** @type {Map<string, Performance>} */
    this.byId = new Map();
  }

  /**
   * @returns {number}
   */
  get size() {
    return this.byId.size;
  }

  /**
   * File a performance.
   *
   * @param {Performance} rung
   * @returns {string} the identifier it was filed under
   */
  add(rung) {
    if (!(rung instanceof Performance)) {
      throw new ValidationError('only a Performance can be filed');
    }
    if (this.byId.has(rung.id)) {
      throw new ConflictError(`this performance is already filed as ${JSON.stringify(rung.id)}`, {
        details: { id: rung.id },
      });
    }
    this.byId.set(rung.id, rung);
    return rung.id;
  }

  /**
   * File several, skipping the ones already there rather than stopping. A
   * secretary importing a year of records wants the ones that are new, not an
   * error at the first repeat.
   *
   * @param {Iterable<Performance>} records
   * @returns {{ added: string[], alreadyFiled: string[] }}
   */
  addAll(records) {
    const added = [];
    const alreadyFiled = [];
    for (const rung of records) {
      if (this.byId.has(rung.id)) {
        alreadyFiled.push(rung.id);
        continue;
      }
      added.push(this.add(rung));
    }
    return { added, alreadyFiled };
  }

  /**
   * @param {string} id
   * @returns {Performance}
   */
  get(id) {
    const found = this.byId.get(id);
    if (found === undefined) {
      throw new NotFoundError('performance', id);
    }
    return found;
  }

  /**
   * @param {string} id
   * @returns {boolean}
   */
  has(id) {
    return this.byId.has(id);
  }

  /**
   * @param {string} id
   * @returns {Performance}
   */
  remove(id) {
    const found = this.get(id);
    this.byId.delete(id);
    return found;
  }

  /**
   * Everything filed, most recent first.
   *
   * @returns {Performance[]}
   */
  all() {
    return sortBy(
      [...this.byId.values()],
      (rung) => -rung.date.getTime(),
      (rung) => rung.tower,
    );
  }

  /**
   * Search the records.
   *
   * @param {{
   *   tower?: string,
   *   title?: string,
   *   ringer?: string,
   *   conductor?: string,
   *   kind?: string,
   *   from?: Date,
   *   to?: Date
   * }} query
   * @returns {Performance[]}
   */
  find(query = {}) {
    const wantedTower = query.tower === undefined ? null : squash(query.tower).toLowerCase();
    const wantedTitle = query.title === undefined ? null : squash(query.title).toLowerCase();
    const wantedRinger = query.ringer === undefined ? null : squash(query.ringer);
    const wantedConductor = query.conductor === undefined ? null : squash(query.conductor);
    return this.all().filter((rung) => {
      if (wantedTower !== null && !rung.tower.toLowerCase().includes(wantedTower)) {
        return false;
      }
      if (wantedTitle !== null && !rung.title.toLowerCase().includes(wantedTitle)) {
        return false;
      }
      if (wantedRinger !== null && rung.bellOf(wantedRinger) === null) {
        return false;
      }
      if (wantedConductor !== null && rung.conductor !== wantedConductor) {
        return false;
      }
      if (query.kind !== undefined && rung.kind !== query.kind) {
        return false;
      }
      if (query.from !== undefined && rung.date.getTime() < query.from.getTime()) {
        return false;
      }
      if (query.to !== undefined && rung.date.getTime() >= query.to.getTime()) {
        return false;
      }
      return true;
    });
  }

  /**
   * The performance a ringer would call their first in a method, or null when
   * they have never rung it.
   *
   * @param {string} name
   * @param {string} title
   * @returns {Performance | null}
   */
  firstIn(name, title) {
    const found = this.find({ ringer: name, title });
    return found.length === 0 ? null : found[found.length - 1];
  }

  /**
   * @returns {IterableIterator<Performance>}
   */
  [Symbol.iterator]() {
    return this.all()[Symbol.iterator]();
  }
}
