import { assertNonEmptyString } from '../util/assert.js';
import { slugify, squash } from '../util/text.js';
import { ValidationError } from '../errors.js';

/**
 * A ringer.
 *
 * The record holds only what a practice planner needs to decide who can ring
 * what: the name, how heavy a bell they can handle, and whether they conduct.
 * What they can actually ring is a separate thing, because it changes week by
 * week while the rest of this does not.
 */

/** The things a ringer can be asked to do besides ring. */
export const ROLES = Object.freeze(['conductor', 'teacher', 'steeple keeper', 'tower captain']);

export class Ringer {
  /**
   * @param {{
   *   name: string,
   *   id?: string,
   *   heaviestBellPounds?: number,
   *   roles?: readonly string[],
   *   notes?: string
   * }} definition
   */
  constructor(definition) {
    if (definition === null || typeof definition !== 'object') {
      throw new ValidationError('a ringer needs a definition object');
    }
    assertNonEmptyString(definition.name, 'a ringer name');
    const heaviest = definition.heaviestBellPounds;
    if (heaviest !== undefined && (typeof heaviest !== 'number' || !(heaviest > 0))) {
      throw new ValidationError(`${heaviest} is not a bell weight a ringer can handle`);
    }
    const roles = definition.roles ?? [];
    if (!Array.isArray(roles)) {
      throw new ValidationError('a ringer needs a list of roles');
    }
    for (const role of roles) {
      if (!ROLES.includes(role)) {
        throw new ValidationError(`${JSON.stringify(role)} is not a role in a tower`);
      }
    }
    this.name = squash(definition.name);
    this.id = definition.id ?? slugify(this.name);
    this.heaviestBellPounds = heaviest ?? null;
    /** @type {readonly string[]} */
    this.roles = Object.freeze([...new Set(roles)].sort());
    this.notes = definition.notes === undefined ? '' : squash(definition.notes);
    Object.freeze(this);
  }

  /**
   * True when the ringer holds a role.
   *
   * @param {string} role
   * @returns {boolean}
   */
  hasRole(role) {
    return this.roles.includes(role);
  }

  /**
   * @returns {boolean}
   */
  get conducts() {
    return this.hasRole('conductor');
  }

  /**
   * True when the ringer can handle a bell of a given weight. A ringer with no
   * weight recorded is assumed to manage anything, because most bands never
   * write it down and refusing everybody would be worse than useless.
   *
   * @param {import('./bell.js').Bell} bell
   * @returns {boolean}
   */
  canHandle(bell) {
    if (this.heaviestBellPounds === null) {
      return true;
    }
    return bell.weightPounds <= this.heaviestBellPounds;
  }

  /**
   * @returns {string}
   */
  toString() {
    const roles = this.roles.length === 0 ? '' : ` (${this.roles.join(', ')})`;
    return `${this.name}${roles}`;
  }
}

/**
 * Build a ringer from a name and nothing else, which is how most band lists
 * start out.
 *
 * @param {string} name
 * @param {{ id?: string, heaviestBellPounds?: number, roles?: readonly string[] }} [details]
 * @returns {Ringer}
 */
export function ringer(name, details = {}) {
  return new Ringer({ name, ...details });
}

/**
 * The ringers in a band who hold a role.
 *
 * @param {Iterable<Ringer>} band
 * @param {string} role
 * @returns {Ringer[]}
 */
export function withRole(band, role) {
  if (!ROLES.includes(role)) {
    throw new ValidationError(`${JSON.stringify(role)} is not a role in a tower`);
  }
  return [...band].filter((member) => member.hasRole(role));
}

/**
 * The ringers who could take a given bell.
 *
 * @param {Iterable<Ringer>} band
 * @param {import('./bell.js').Bell} bell
 * @returns {Ringer[]}
 */
export function whoCanHandle(band, bell) {
  return [...band].filter((member) => member.canHandle(bell));
}
