/**
 * Text helpers for the report writers and the CLI. Everything here works on
 * plain strings and never touches a locale, because a peal report printed in
 * one place has to match the same report printed in another.
 */

/**
 * Pad on the right to a width, leaving longer text alone.
 *
 * @param {string} text
 * @param {number} width
 * @returns {string}
 */
export function padRight(text, width) {
  return text.length >= width ? text : text + ' '.repeat(width - text.length);
}

/**
 * Pad on the left to a width, leaving longer text alone.
 *
 * @param {string} text
 * @param {number} width
 * @returns {string}
 */
export function padLeft(text, width) {
  return text.length >= width ? text : ' '.repeat(width - text.length) + text;
}

/**
 * Lay out rows of cells as a fixed width table, one string per line. Column
 * widths come from the widest cell in each column, and a row with too few
 * cells is padded out rather than refused.
 *
 * @param {readonly (readonly string[])[]} rows
 * @param {{ gap?: string, align?: readonly ('left' | 'right')[] }} [options]
 * @returns {string[]}
 */
export function tabulate(rows, options = {}) {
  const gap = options.gap ?? '  ';
  const align = options.align ?? [];
  const columnCount = rows.reduce((widest, row) => Math.max(widest, row.length), 0);
  const widths = new Array(columnCount).fill(0);
  for (const row of rows) {
    for (let column = 0; column < columnCount; column += 1) {
      widths[column] = Math.max(widths[column], (row[column] ?? '').length);
    }
  }
  return rows.map((row) => {
    const cells = [];
    for (let column = 0; column < columnCount; column += 1) {
      const cell = row[column] ?? '';
      cells.push(
        align[column] === 'right' ? padLeft(cell, widths[column]) : padRight(cell, widths[column]),
      );
    }
    return cells.join(gap).trimEnd();
  });
}

/**
 * `1 bell` or `4 bells`, with the number written out.
 *
 * @param {number} count
 * @param {string} singular
 * @param {string} [pluralForm] defaults to the singular with an s
 * @returns {string}
 */
export function plural(count, singular, pluralForm) {
  const many = pluralForm ?? `${singular}s`;
  return `${count} ${count === 1 ? singular : many}`;
}

/**
 * Join a list the way a sentence would: `a`, `a and b`, `a, b and c`.
 *
 * @param {readonly string[]} items
 * @returns {string}
 */
export function joinWords(items) {
  if (items.length === 0) {
    return '';
  }
  if (items.length === 1) {
    return items[0];
  }
  return `${items.slice(0, -1).join(', ')} and ${items[items.length - 1]}`;
}

/**
 * Cut text to a width, ending with an ellipsis when it had to cut.
 *
 * @param {string} text
 * @param {number} width
 * @returns {string}
 */
export function truncate(text, width) {
  if (width < 1) {
    throw new RangeError(`width ${width} must be at least 1`);
  }
  if (text.length <= width) {
    return text;
  }
  if (width === 1) {
    return '.';
  }
  return `${text.slice(0, width - 1)}.`;
}

/**
 * Turn a title into something safe to use in a file name or a URL.
 *
 * @param {string} text
 * @returns {string}
 */
export function slugify(text) {
  return text
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

/**
 * Collapse runs of whitespace and trim, which is what a form field needs before
 * anything is compared against it.
 *
 * @param {string} text
 * @returns {string}
 */
export function squash(text) {
  return text.replace(/\s+/g, ' ').trim();
}

/**
 * Wrap text at a width without breaking words. A word longer than the width
 * gets a line of its own rather than being cut in half.
 *
 * @param {string} text
 * @param {number} width
 * @returns {string[]}
 */
export function wrap(text, width) {
  if (width < 1) {
    throw new RangeError(`width ${width} must be at least 1`);
  }
  const words = squash(text)
    .split(' ')
    .filter((word) => word.length > 0);
  if (words.length === 0) {
    return [];
  }
  const lines = [];
  let line = words[0];
  for (const word of words.slice(1)) {
    if (line.length + 1 + word.length <= width) {
      line += ` ${word}`;
    } else {
      lines.push(line);
      line = word;
    }
  }
  lines.push(line);
  return lines;
}
