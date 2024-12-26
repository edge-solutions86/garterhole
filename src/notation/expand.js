import { parseNotation } from './parser.js';
import { NotationError } from '../errors.js';

/**
 * Most methods are palindromes, and the printed literature takes advantage of
 * that by writing only half of the lead. `x1x1x1,2` is a whole lead of Plain
 * Bob Minor written in seven changes instead of twelve.
 *
 * The rule is the same for every section a comma divides: write the section
 * out, then write it again backwards leaving off the change it ended on. The
 * change that is left off is the one on the axis of the mirror, and repeating
 * it would ring it twice.
 */

/**
 * Mirror a single section about its last change.
 *
 * @param {readonly import('../core/change.js').Change[]} section
 * @returns {import('../core/change.js').Change[]}
 */
export function mirrorSection(section) {
  if (section.length === 0) {
    return [];
  }
  const reflected = section.slice(0, -1).reverse();
  return [...section, ...reflected];
}

/**
 * Expand the sections of a folded notation into the changes of a whole lead.
 * A notation with only one section is already written out and is returned as
 * it stands.
 *
 * @param {readonly import('../core/change.js').Change[][]} sections
 * @returns {import('../core/change.js').Change[]}
 */
export function expandSections(sections) {
  if (!Array.isArray(sections) || sections.length === 0) {
    throw new NotationError('there is nothing to expand');
  }
  if (sections.length === 1) {
    return [...sections[0]];
  }
  const changes = [];
  for (const section of sections) {
    changes.push(...mirrorSection(section));
  }
  return changes;
}

/**
 * Read notation and give back the changes of one whole lead.
 *
 * @param {string} text
 * @param {number} stage
 * @returns {import('../core/change.js').Change[]}
 */
export function expandNotation(text, stage) {
  const parsed = parseNotation(text, stage);
  return expandSections(parsed.sections);
}

/**
 * True when a run of changes reads the same forwards as backwards about the
 * two points a folded notation implies. This is what makes a method
 * palindromic, and it is the property the classification rules lean on.
 *
 * @param {readonly import('../core/change.js').Change[]} changes
 * @returns {boolean}
 */
export function isPalindromic(changes) {
  return findSymmetryPoint(changes) !== null;
}

/**
 * Find the index the lead is a palindrome about, or null when it is not one.
 * The index names the change that sits on the axis.
 *
 * @param {readonly import('../core/change.js').Change[]} changes
 * @returns {number | null}
 */
export function findSymmetryPoint(changes) {
  const count = changes.length;
  if (count === 0) {
    return null;
  }
  for (let axis = 0; axis < count; axis += 1) {
    if (reflectsAbout(changes, axis)) {
      return axis;
    }
  }
  return null;
}

/**
 * Rotate a lead so that its axis of symmetry sits at the end of the first
 * section, which is where a folded notation expects to find it.
 *
 * @param {readonly import('../core/change.js').Change[]} changes
 * @param {number} axis
 * @returns {import('../core/change.js').Change[]}
 */
export function rotateToAxis(changes, axis) {
  if (!Number.isInteger(axis) || axis < 0 || axis >= changes.length) {
    throw new RangeError(`axis ${axis} is outside 0..${changes.length - 1}`);
  }
  return [...changes.slice(axis + 1), ...changes.slice(0, axis + 1)];
}

/**
 * @param {readonly import('../core/change.js').Change[]} changes
 * @param {number} axis
 * @returns {boolean}
 */
function reflectsAbout(changes, axis) {
  const count = changes.length;
  for (let step = 1; step < count; step += 1) {
    const left = changes[(((axis - step) % count) + count) % count];
    const right = changes[(axis + step) % count];
    if (!left.equals(right)) {
      return false;
    }
  }
  return true;
}
