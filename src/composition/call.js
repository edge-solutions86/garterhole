import { parseChanges } from '../notation/parser.js';
import { writeChanges } from '../notation/format.js';
import { Method } from '../method/method.js';
import { ValidationError } from '../errors.js';
import { assertNonEmptyString } from '../util/assert.js';

/**
 * A call is an instruction from the conductor that swaps one or more changes at
 * the end of a lead for different ones. A bob in Plain Bob Major replaces the
 * `12` at the lead end with `14`; a single replaces it with `1234`.
 *
 * The call knows only the changes it substitutes and how many it replaces. What
 * that does to the bells depends on the method, which is why the call itself
 * carries no lead head or calling position.
 */
export class Call {
  /**
   * @param {{
   *   name: string,
   *   symbol: string,
   *   stage: number,
   *   notation: string,
   *   replaces?: number
   * }} definition
   */
  constructor(definition) {
    if (definition === null || typeof definition !== 'object') {
      throw new ValidationError('a call needs a definition object');
    }
    assertNonEmptyString(definition.name, 'a call name');
    assertNonEmptyString(definition.symbol, 'a call symbol');
    assertNonEmptyString(definition.notation, 'a call notation');
    const changes = parseChanges(definition.notation, definition.stage);
    if (changes.length === 0) {
      throw new ValidationError('a call must substitute at least one change');
    }
    const replaces = definition.replaces ?? changes.length;
    if (!Number.isInteger(replaces) || replaces < 1) {
      throw new ValidationError(`a call cannot replace ${replaces} changes`);
    }
    this.name = definition.name.trim();
    this.symbol = definition.symbol.trim();
    this.stage = definition.stage;
    /** @type {readonly import('../core/change.js').Change[]} */
    this.changes = Object.freeze(changes);
    this.replaces = replaces;
    Object.freeze(this);
  }

  /**
   * The call that changes nothing, which is what a plain lead is.
   *
   * @param {number} stage
   * @returns {Call}
   */
  static plain(stage) {
    return new PlainCall(stage);
  }

  /**
   * True when the call leaves the lead exactly as it was.
   *
   * @returns {boolean}
   */
  isPlain() {
    return false;
  }

  /**
   * The changes of a lead of the method with this call made at the end of it.
   *
   * @param {Method} method
   * @returns {import('../core/change.js').Change[]}
   */
  applyToLead(method) {
    if (!(method instanceof Method)) {
      throw new ValidationError('a call is applied to a Method');
    }
    if (method.stage !== this.stage) {
      throw new ValidationError(
        `a call on ${this.stage} cannot be made in a method on ${method.stage}`,
      );
    }
    if (this.replaces > method.leadLength) {
      throw new ValidationError(
        `a call replacing ${this.replaces} changes cannot be made in a lead of ${method.leadLength}`,
      );
    }
    const kept = method.changes.slice(0, method.leadLength - this.replaces);
    return [...kept, ...this.changes];
  }

  /**
   * The notation the call substitutes.
   *
   * @returns {string}
   */
  get notation() {
    return writeChanges(this.changes);
  }

  /**
   * @returns {string}
   */
  toString() {
    return `${this.name} (${this.symbol}) ${this.notation}`;
  }
}

/**
 * The plain lead, modelled as a call so that a touch can be written as a run of
 * calls without a special case for the leads where nothing is called.
 */
class PlainCall extends Call {
  /**
   * @param {number} stage
   */
  constructor(stage) {
    super({ name: 'Plain', symbol: '.', stage, notation: '1', replaces: 1 });
  }

  isPlain() {
    return true;
  }

  /**
   * @param {Method} method
   * @returns {import('../core/change.js').Change[]}
   */
  applyToLead(method) {
    if (!(method instanceof Method)) {
      throw new ValidationError('a call is applied to a Method');
    }
    return [...method.changes];
  }
}

/**
 * Build a call from the pieces a composition file gives.
 *
 * @param {string} name
 * @param {string} symbol
 * @param {number} stage
 * @param {string} notation
 * @param {number} [replaces]
 * @returns {Call}
 */
export function call(name, symbol, stage, notation, replaces) {
  return new Call({ name, symbol, stage, notation, replaces });
}
