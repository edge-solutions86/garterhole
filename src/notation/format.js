import { symmetryPoints } from './expand.js';

/**
 * Writing notation back out is not quite the inverse of reading it. The reader
 * accepts three spellings of a cross change and lets separators appear wherever
 * they like; the writer picks one house style and sticks to it.
 *
 * The house style is the one the method collections print: `x` for a cross, no
 * separator next to a cross, a full stop between two runs of places, and the
 * palindrome folded at a comma when the lead has one.
 */

/**
 * Write a run of changes with no fold.
 *
 * @param {readonly import('../core/change.js').Change[]} changes
 * @returns {string}
 */
export function writeChanges(changes) {
  let text = '';
  let previousWasPlaces = false;
  for (const change of changes) {
    const written = change.toString();
    const isPlaces = !change.isCross();
    if (isPlaces && previousWasPlaces) {
      text += '.';
    }
    text += written;
    previousWasPlaces = isPlaces;
  }
  return text;
}

/**
 * Split a palindromic lead back into the two sections a comma divides, so that
 * it can be written the short way. Gives null when the lead has no axis, or has
 * an axis structure the short form cannot describe.
 *
 * @param {readonly import('../core/change.js').Change[]} changes
 * @returns {{ first: import('../core/change.js').Change[], second: import('../core/change.js').Change[] } | null}
 */
export function foldChanges(changes) {
  const axes = symmetryPoints(changes);
  if (axes.length !== 2) {
    return null;
  }
  const [first, second] = axes;
  const headLength = first + 1;
  const tailLength = changes.length - 2 * first - 1;
  if (tailLength <= 0 || tailLength % 2 === 0) {
    return null;
  }
  if (second !== 2 * first + (tailLength + 1) / 2) {
    return null;
  }
  return {
    first: changes.slice(0, headLength),
    second: changes.slice(2 * first + 1, second + 1),
  };
}

/**
 * Write a lead the way a method collection prints it, folded at the comma when
 * that is possible and written out in full when it is not.
 *
 * @param {readonly import('../core/change.js').Change[]} changes
 * @returns {string}
 */
export function writeFolded(changes) {
  const folded = foldChanges(changes);
  if (folded === null) {
    return writeChanges(changes);
  }
  return `${writeChanges(folded.first)},${writeChanges(folded.second)}`;
}

/**
 * Write one change per line with the row it produces beside it, which is what
 * the CLI prints when somebody asks to see a lead.
 *
 * @param {readonly import('../core/change.js').Change[]} changes
 * @param {readonly import('../core/row.js').Row[]} rows
 * @returns {string[]}
 */
export function writeLeadLines(changes, rows) {
  if (rows.length !== changes.length + 1) {
    throw new RangeError(
      `expected ${changes.length + 1} rows for ${changes.length} changes but got ${rows.length}`,
    );
  }
  const width = Math.max(...changes.map((change) => change.toString().length), 1);
  const lines = [`${' '.repeat(width)}  ${rows[0].toString()}`];
  for (let index = 0; index < changes.length; index += 1) {
    const notation = changes[index].toString().padStart(width, ' ');
    lines.push(`${notation}  ${rows[index + 1].toString()}`);
  }
  return lines;
}
