import { Method } from '../method/method.js';
import { methodToJson, methodFromJson } from '../method/serialise.js';
import { analyseTouch, checkClaim } from './analysis.js';
import { titleOf } from '../method/title.js';
import { squash } from '../util/text.js';
import { assertNonEmptyString } from '../util/assert.js';
import { ValidationError } from '../errors.js';

/**
 * A composition as it is filed: who wrote it, what method it is in, how it is
 * called, and what its author says it comes to.
 *
 * The claimed length is stored rather than derived on purpose. Half the value
 * of the library is telling a composer that the calling in front of them does
 * not give the length printed above it, and that comparison needs both numbers.
 */
export class Composition {
  /**
   * @param {{
   *   composer: string,
   *   method: Method,
   *   touch: string,
   *   claimedLength?: number,
   *   notes?: string
   * }} definition
   */
  constructor(definition) {
    if (definition === null || typeof definition !== 'object') {
      throw new ValidationError('a composition needs a definition object');
    }
    assertNonEmptyString(definition.composer, 'a composer');
    if (!(definition.method instanceof Method)) {
      throw new ValidationError('a composition needs a Method');
    }
    assertNonEmptyString(definition.touch, 'a calling');
    if (
      definition.claimedLength !== undefined &&
      (!Number.isInteger(definition.claimedLength) || definition.claimedLength < 1)
    ) {
      throw new ValidationError(`a composition cannot be ${definition.claimedLength} changes long`);
    }
    this.composer = squash(definition.composer);
    this.method = definition.method;
    this.touch = squash(definition.touch);
    this.claimedLength = definition.claimedLength;
    this.notes = definition.notes === undefined ? '' : squash(definition.notes);
    Object.freeze(this);
  }

  /**
   * The title a peal board would carry.
   *
   * @returns {string}
   */
  get title() {
    const length = this.claimedLength === undefined ? '' : `${this.claimedLength} `;
    return `${length}${titleOf(this.method)}`;
  }

  /**
   * Ring the composition and report on it.
   *
   * @returns {ReturnType<typeof analyseTouch>}
   */
  analyse() {
    return analyseTouch({ method: this.method, touch: this.touch });
  }

  /**
   * Everything wrong with the composition, empty when it is sound.
   *
   * @returns {string[]}
   */
  problems() {
    return checkClaim(this.analyse(), {
      length: this.claimedLength,
      stage: this.method.stage,
    });
  }

  /**
   * @returns {boolean}
   */
  isSound() {
    return this.problems().length === 0;
  }

  /**
   * @returns {string}
   */
  toString() {
    return `${this.title} by ${this.composer}`;
  }
}

/**
 * @param {Composition} composition
 * @returns {Record<string, unknown>}
 */
export function compositionToJson(composition) {
  if (!(composition instanceof Composition)) {
    throw new ValidationError('only a Composition can be serialised');
  }
  return {
    format: 'garterhole.composition',
    version: 1,
    composer: composition.composer,
    method: methodToJson(composition.method),
    touch: composition.touch,
    claimedLength: composition.claimedLength ?? null,
    notes: composition.notes,
  };
}

/**
 * @param {Record<string, unknown>} document
 * @returns {Composition}
 */
export function compositionFromJson(document) {
  if (document === null || typeof document !== 'object') {
    throw new ValidationError('a composition document must be an object');
  }
  if (document.version !== 1) {
    throw new ValidationError(
      `this reader understands version 1 but the document is version ${document.version}`,
    );
  }
  return new Composition({
    composer: String(document.composer ?? ''),
    method: methodFromJson(document.method),
    touch: String(document.touch ?? ''),
    claimedLength: document.claimedLength === null ? undefined : document.claimedLength,
    notes: document.notes === undefined ? undefined : String(document.notes),
  });
}
