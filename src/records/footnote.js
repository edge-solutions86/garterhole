import { squash } from '../util/text.js';
import { ValidationError } from '../errors.js';

/**
 * Footnotes.
 *
 * A performance record carries a line or two under the band: a first quarter
 * for somebody, a birthday, a death. Most of them are free text and always will
 * be, but the common ones follow a shape, and recognising that shape is what
 * lets the service say "this is Alice's first quarter of Minor" without anybody
 * typing it.
 *
 * Nothing here rewrites what somebody wrote. A footnote that is recognised gets
 * a structure attached to it and keeps its own words.
 */

/** The kinds of footnote that are recognised. */
export const KINDS = Object.freeze(['first', 'birthday', 'memorial', 'wedding', 'other']);

/**
 * A word that could be part of a name: a capital, then lower case, allowing for
 * the capital after an apostrophe that a name like O'Brien has.
 */
const NAME_WORD = /^[A-Z][a-z]*(?:'[A-Z]?[a-z]+)?$/;

const PATTERNS = Object.freeze([
  { kind: 'first', pattern: /^(?:first|1st)\b/i },
  { kind: 'birthday', pattern: /\bbirthday\b/i },
  { kind: 'memorial', pattern: /\b(?:memory|memoriam|rip)\b/i },
  { kind: 'wedding', pattern: /\b(?:wedding|marriage)\b/i },
]);

/**
 * What kind of footnote a line is.
 *
 * @param {string} text
 * @returns {string}
 */
export function kindOf(text) {
  const line = squash(String(text ?? ''));
  for (const entry of PATTERNS) {
    if (entry.pattern.test(line)) {
      return entry.kind;
    }
  }
  return 'other';
}

/**
 * Read a footnote.
 *
 * @param {string} text
 * @returns {{ text: string, kind: string, names: string[] }}
 */
export function readFootnote(text) {
  const line = squash(String(text ?? ''));
  if (line === '') {
    throw new ValidationError('a footnote cannot be blank');
  }
  return { text: line, kind: kindOf(line), names: namesIn(line) };
}

/**
 * The names a footnote mentions, taken as the runs of capitalised words that
 * are not at the start of a sentence.
 *
 * This is a guess and it is documented as one. It is right often enough to be
 * worth offering as a suggestion in a form, and it is never used to change what
 * was written.
 *
 * @param {string} text
 * @returns {string[]}
 */
export function namesIn(text) {
  const line = squash(String(text ?? ''));
  const names = [];
  let current = [];
  const words = line.replace(/[.,;!?]/g, '').split(' ');
  words.forEach((word, index) => {
    const capitalised = NAME_WORD.test(word);
    if (capitalised && index > 0) {
      current.push(word);
      return;
    }
    if (current.length > 0) {
      names.push(current.join(' '));
      current = [];
    }
  });
  if (current.length > 0) {
    names.push(current.join(' '));
  }
  return names;
}

/**
 * The footnotes of a performance, read.
 *
 * @param {Iterable<string>} footnotes
 * @returns {{ text: string, kind: string, names: string[] }[]}
 */
export function readAll(footnotes) {
  const read = [];
  for (const line of footnotes) {
    read.push(readFootnote(line));
  }
  return read;
}

/**
 * Write the footnote a first would get, so that a form can offer it.
 *
 * @param {string} name
 * @param {string} what for example `quarter peal of Minor`
 * @returns {string}
 */
export function firstFootnote(name, what) {
  const who = squash(String(name ?? ''));
  const thing = squash(String(what ?? ''));
  if (who === '' || thing === '') {
    throw new ValidationError('a first needs somebody and something');
  }
  return `First ${thing} for ${who}.`;
}

/**
 * Group footnotes by kind, so that a report can put the firsts together.
 *
 * @param {Iterable<string>} footnotes
 * @returns {Map<string, string[]>}
 */
export function groupByKind(footnotes) {
  /** @type {Map<string, string[]>} */
  const groups = new Map();
  for (const entry of readAll(footnotes)) {
    const bucket = groups.get(entry.kind);
    if (bucket === undefined) {
      groups.set(entry.kind, [entry.text]);
    } else {
      bucket.push(entry.text);
    }
  }
  return groups;
}
