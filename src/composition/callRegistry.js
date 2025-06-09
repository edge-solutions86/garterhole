import { Call } from './call.js';
import { fourthsPlaceCalls, thirdsPlaceCalls } from './callSet.js';
import { huntBells } from '../method/huntBells.js';
import { Method } from '../method/method.js';
import { ValidationError, NotFoundError } from '../errors.js';

/**
 * Which calls go with which method.
 *
 * This started as a pair of branches inside the call set, which was fine while
 * there were two families. It is a registry now because a tower that rings
 * anything unusual needs to add its own entry without editing the library, and
 * because the order the entries are tried in has to be visible rather than
 * buried in an if.
 *
 * Entries are tried in the order they were registered, and the first one whose
 * test passes wins, so a tower's own entry beats the built in ones.
 */
export class CallRegistry {
  constructor() {
    /** @type {{ name: string, test: (method: Method) => boolean, build: (method: Method) => Record<string, Call> }[]} */
    this.entries = [];
  }

  /**
   * Add an entry at the front, so that it is tried before everything already
   * registered.
   *
   * @param {string} name
   * @param {(method: Method) => boolean} test
   * @param {(method: Method) => Record<string, Call>} build
   * @returns {CallRegistry} this, so that registrations can be chained
   */
  register(name, test, build) {
    if (typeof name !== 'string' || name.length === 0) {
      throw new ValidationError('a registry entry needs a name');
    }
    if (typeof test !== 'function' || typeof build !== 'function') {
      throw new ValidationError(`the entry ${JSON.stringify(name)} needs a test and a builder`);
    }
    this.entries.unshift({ name, test, build });
    return this;
  }

  /**
   * The name of the entry that would be used for a method.
   *
   * @param {Method} method
   * @returns {string}
   */
  entryFor(method) {
    if (!(method instanceof Method)) {
      throw new ValidationError('a Method is needed to choose its calls');
    }
    for (const entry of this.entries) {
      if (entry.test(method)) {
        return entry.name;
      }
    }
    throw new NotFoundError('call set', method.name);
  }

  /**
   * The calls for a method, including the plain lead.
   *
   * @param {Method} method
   * @returns {Record<string, Call>}
   */
  callsFor(method) {
    const name = this.entryFor(method);
    const entry = this.entries.find((candidate) => candidate.name === name);
    return { plain: Call.plain(method.stage), ...entry.build(method) };
  }

  /**
   * Every entry name, in the order they are tried.
   *
   * @returns {string[]}
   */
  names() {
    return this.entries.map((entry) => entry.name);
  }
}

/**
 * The registry the library uses when nobody supplies one.
 *
 * @returns {CallRegistry}
 */
export function defaultRegistry() {
  return new CallRegistry()
    .register(
      'fourths place',
      () => true,
      (method) => fourthsPlaceCalls(method.stage),
    )
    .register(
      'thirds place',
      (method) => huntBells(method).length >= 2,
      (method) => thirdsPlaceCalls(method.stage),
    );
}
