import { stageName, stageFromName, supportedStages } from '../core/stage.js';
import { classify } from './classify.js';
import { squash } from '../util/text.js';
import { ValidationError } from '../errors.js';

/**
 * A method title is its name, then its class, then the name of its stage:
 * `Cambridge Surprise Major`. The class is left out for a principle, which is
 * why `Stedman Triples` has only two words of its own.
 *
 * Reading a title back apart is worth doing carefully, because the name can
 * contain any word at all. `Double Norwich Court Bob Major` has a class word in
 * the middle of its name and a different one at the end.
 */

/** The class words, longest first so that Treble Bob is matched before Bob. */
const CLASS_WORDS = Object.freeze([
  'Treble Place',
  'Treble Bob',
  'Slow Course',
  'Surprise',
  'Delight',
  'Alliance',
  'Place',
  'Hybrid',
  'Bob',
]);

/** Words that can sit in front of the class word. */
const MODIFIERS = Object.freeze(['Differential', 'Little']);

/**
 * Build a title from its parts.
 *
 * @param {{ name: string, className?: string, stage: number }} parts
 * @returns {string}
 */
export function buildTitle(parts) {
  const name = squash(String(parts.name ?? ''));
  const className = squash(String(parts.className ?? ''));
  if (name === '' && (className === '' || className === 'Principle')) {
    throw new ValidationError('a title needs a name or a class');
  }
  const words = name === '' ? [] : [name];
  if (className !== '' && className !== 'Principle') {
    words.push(className);
  }
  words.push(stageName(parts.stage));
  return words.join(' ');
}

/**
 * The title a method should have, worked out from its own changes.
 *
 * @param {import('./method.js').Method} method
 * @returns {string}
 */
export function titleOf(method) {
  const { className } = classify(method);
  return buildTitle({ name: method.name, className, stage: method.stage });
}

/**
 * Read a title apart. The stage comes off the end first, then any class word
 * and its modifiers, and whatever is left is the name. Some titles have no name
 * of their own at all, `Little Bob Major` being one, and those give back an
 * empty name rather than an error.
 *
 * @param {string} title
 * @returns {{ name: string, className: string, stage: number }}
 */
export function parseTitle(title) {
  const text = squash(String(title ?? ''));
  if (text === '') {
    throw new ValidationError('a title cannot be blank');
  }
  const stage = trailingStage(text);
  if (stage === null) {
    throw new ValidationError(`${JSON.stringify(title)} does not end with the name of a stage`);
  }
  let rest = text.slice(0, text.length - stageName(stage).length).trim();
  const className = trailingClass(rest);
  if (className !== '') {
    rest = rest.slice(0, rest.length - className.length).trim();
  }
  if (rest === '' && className === '') {
    throw new ValidationError(`${JSON.stringify(title)} is nothing but a stage name`);
  }
  return { name: rest, className: className === '' ? 'Principle' : className, stage };
}

/**
 * True when a title has the shape a title should have.
 *
 * @param {string} title
 * @returns {boolean}
 */
export function isWellFormedTitle(title) {
  try {
    parseTitle(title);
    return true;
  } catch {
    return false;
  }
}

/**
 * @param {string} text
 * @returns {number | null}
 */
function trailingStage(text) {
  for (const stage of supportedStages()) {
    const suffix = ` ${stageName(stage)}`;
    if (text.endsWith(suffix)) {
      return stage;
    }
  }
  try {
    return stageFromName(text);
  } catch {
    return null;
  }
}

/**
 * @param {string} text
 * @returns {string} the class words at the end of the text, or an empty string
 */
function trailingClass(text) {
  for (const word of CLASS_WORDS) {
    if (text === word || text.endsWith(` ${word}`)) {
      const withModifier = withModifiers(text, word);
      return withModifier;
    }
  }
  return '';
}

/**
 * @param {string} text
 * @param {string} word
 * @returns {string}
 */
function withModifiers(text, word) {
  let found = word;
  let head = text.slice(0, text.length - found.length).trim();
  let changed = true;
  while (changed) {
    changed = false;
    for (const modifier of MODIFIERS) {
      if (head === modifier || head.endsWith(` ${modifier}`)) {
        found = `${modifier} ${found}`;
        head = head.slice(0, head.length - modifier.length).trim();
        changed = true;
      }
    }
  }
  return found;
}
