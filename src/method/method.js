import { rounds } from '../core/row.js';
import { RowBlock } from '../core/rowBlock.js';
import { isSupportedStage, stageName } from '../core/stage.js';
import { expandNotation } from '../notation/expand.js';
import { writeFolded, writeChanges } from '../notation/format.js';
import { ValidationError } from '../errors.js';
import { assertNonEmptyString } from '../util/assert.js';

/**
 * A method is a name and a lead of place notation. Everything else about it,
 * from its lead head to its classification, follows from the notation, so the
 * class stores only the two facts and derives the rest on demand.
 *
 * Deriving rather than storing matters more than it looks. Method libraries
 * are full of entries whose recorded lead head disagrees with their notation,
 * and a library that trusts the recorded value quietly gives wrong answers for
 * years.
 */
export class Method {
  /**
   * @param {{ name: string, stage: number, notation: string }} definition
   */
  constructor(definition) {
    if (definition === null || typeof definition !== 'object') {
      throw new ValidationError('a method needs a definition object');
    }
    const { name, stage, notation } = definition;
    assertNonEmptyString(name, 'a method name');
    if (!isSupportedStage(stage)) {
      throw new ValidationError(`stage ${stage} is not supported`);
    }
    assertNonEmptyString(notation, 'place notation');
    const changes = expandNotation(notation, stage);
    if (changes.length === 0) {
      throw new ValidationError('a method needs at least one change');
    }
    this.name = name.trim();
    this.stage = stage;
    /** @type {readonly import('../core/change.js').Change[]} */
    this.changes = Object.freeze(changes);
    /** The notation as written by the author, kept for round tripping. */
    this.source = notation;
    Object.freeze(this);
  }

  /**
   * The number of changes in a lead.
   *
   * @returns {number}
   */
  get leadLength() {
    return this.changes.length;
  }

  /**
   * The ringing name of the stage, so that a report can say `Major` without
   * looking the number up itself.
   *
   * @returns {string}
   */
  get stageName() {
    return stageName(this.stage);
  }

  /**
   * The notation written in the house style, folded when it can be.
   *
   * @returns {string}
   */
  get notation() {
    return writeFolded(this.changes);
  }

  /**
   * The notation written out in full, with no comma.
   *
   * @returns {string}
   */
  get fullNotation() {
    return writeChanges(this.changes);
  }

  /**
   * Ring one plain lead from a starting row.
   *
   * @param {import('../core/row.js').Row} [start] defaults to rounds
   * @returns {RowBlock}
   */
  plainLead(start = rounds(this.stage)) {
    if (start.stage !== this.stage) {
      throw new ValidationError(
        `a method on ${this.stage} cannot start from a row of ${start.stage}`,
      );
    }
    return RowBlock.fromChanges(start, this.changes);
  }

  /**
   * The row a plain lead finishes on.
   *
   * @param {import('../core/row.js').Row} [start]
   * @returns {import('../core/row.js').Row}
   */
  leadHead(start = rounds(this.stage)) {
    return this.plainLead(start).last();
  }

  /**
   * The row before the lead head, which is where a call is made.
   *
   * @param {import('../core/row.js').Row} [start]
   * @returns {import('../core/row.js').Row}
   */
  leadEnd(start = rounds(this.stage)) {
    const lead = this.plainLead(start);
    return lead.at(lead.length - 2);
  }

  /**
   * The change that takes the lead end to the lead head. A call replaces this
   * change with one of its own.
   *
   * @returns {import('../core/change.js').Change}
   */
  get leadEndChange() {
    return this.changes[this.changes.length - 1];
  }

  /**
   * @param {Method} other
   * @returns {boolean} true when the two ring the same rows, whatever they are called
   */
  ringsTheSameAs(other) {
    if (!(other instanceof Method) || other.stage !== this.stage) {
      return false;
    }
    return this.fullNotation === other.fullNotation;
  }

  /**
   * @returns {string}
   */
  toString() {
    return `${this.name} (${this.stageName}) ${this.notation}`;
  }
}

/**
 * Build a method from the two things a collection prints.
 *
 * @param {string} name
 * @param {number} stage
 * @param {string} notation
 * @returns {Method}
 */
export function method(name, stage, notation) {
  return new Method({ name, stage, notation });
}
