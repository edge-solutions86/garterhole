import { Method } from './method.js';
import { MethodLibrary } from './library.js';
import { classify } from './classify.js';
import { titleOf, parseTitle } from './title.js';
import { ValidationError } from '../errors.js';

/**
 * Methods on the wire and on disk.
 *
 * The serialised form carries the title and the class as well as the notation,
 * even though both can be worked out from the notation. That is for the reader
 * on the other end, which may not be this library. Coming back in, the derived
 * fields are ignored and everything is worked out again, so a file written by
 * something that got the class wrong still loads correctly.
 */

/** The version stamped into every document, so that a reader can refuse a future one. */
export const FORMAT_VERSION = 1;

/**
 * @param {Method} method
 * @returns {Record<string, unknown>}
 */
export function methodToJson(method) {
  if (!(method instanceof Method)) {
    throw new ValidationError('only a Method can be serialised');
  }
  const facts = classify(method);
  return {
    title: titleOf(method),
    name: method.name,
    stage: method.stage,
    notation: method.notation,
    class: facts.className,
    leadLength: method.leadLength,
    leadHead: method.leadHead().toString(),
    huntBells: facts.huntBells,
  };
}

/**
 * @param {Record<string, unknown>} document
 * @returns {Method}
 */
export function methodFromJson(document) {
  if (document === null || typeof document !== 'object') {
    throw new ValidationError('a method document must be an object');
  }
  const { notation } = document;
  if (typeof notation !== 'string') {
    throw new ValidationError('a method document needs its notation');
  }
  const { name, stage } = namePartsFrom(document);
  return new Method({ name, stage, notation });
}

/**
 * @param {MethodLibrary} library
 * @returns {Record<string, unknown>}
 */
export function libraryToJson(library) {
  if (!(library instanceof MethodLibrary)) {
    throw new ValidationError('only a MethodLibrary can be serialised');
  }
  return {
    format: 'garterhole.methods',
    version: FORMAT_VERSION,
    methods: library.methods().map((method) => methodToJson(method)),
  };
}

/**
 * @param {Record<string, unknown>} document
 * @returns {MethodLibrary}
 */
export function libraryFromJson(document) {
  if (document === null || typeof document !== 'object') {
    throw new ValidationError('a library document must be an object');
  }
  if (document.version !== FORMAT_VERSION) {
    throw new ValidationError(
      `this reader understands version ${FORMAT_VERSION} but the document is version ${document.version}`,
      { details: { expected: FORMAT_VERSION, found: document.version } },
    );
  }
  if (!Array.isArray(document.methods)) {
    throw new ValidationError('a library document needs a list of methods');
  }
  const library = new MethodLibrary();
  document.methods.forEach((entry, index) => {
    try {
      library.add(methodFromJson(entry));
    } catch (error) {
      throw new ValidationError(`method ${index} could not be read: ${error.message}`, {
        details: { index },
      });
    }
  });
  return library;
}

/**
 * Work out the name and stage of a method document. A document with a title and
 * nothing else is enough, because a title carries both.
 *
 * @param {Record<string, unknown>} document
 * @returns {{ name: string, stage: number }}
 */
function namePartsFrom(document) {
  if (typeof document.name === 'string' && Number.isInteger(document.stage)) {
    return { name: document.name, stage: document.stage };
  }
  if (typeof document.title === 'string') {
    const parts = parseTitle(document.title);
    return { name: parts.name === '' ? parts.className : parts.name, stage: parts.stage };
  }
  throw new ValidationError('a method document needs either a title or a name and a stage');
}
