import { totals, byRinger, byTower, byMethod } from './statistics.js';
import { summaryLine } from './report.js';
import { tabulate, plural, joinWords } from '../util/text.js';
import { ValidationError } from '../errors.js';

/**
 * Writing a set of performances out in whatever format is wanted.
 *
 * The text report was the only one for a while, and then the association asked
 * for a spreadsheet and the website asked for JSON. Rather than growing a
 * format flag through the report code, each format is a writer with the same
 * shape: hand it records and options, get a string back.
 *
 * A writer is a plain object rather than a class, because there is no state and
 * because it makes registering a new one a one line change.
 */

/**
 * @typedef {{
 *   name: string,
 *   contentType: string,
 *   write: (records: readonly import('./performance.js').Performance[], options?: object) => string
 * }} Writer
 */

/** The columns a spreadsheet gets, in order. */
const CSV_COLUMNS = Object.freeze([
  'date',
  'tower',
  'title',
  'changes',
  'kind',
  'conductor',
  'composer',
  'band',
]);

/**
 * The report as lines of text.
 *
 * @param {readonly import('./performance.js').Performance[]} records
 * @param {{ title?: string, topRingers?: number }} [options]
 * @returns {string[]}
 */
export function reportLines(records, options = {}) {
  const kept = [...records];
  const heading = options.title ?? 'Performances';
  const counted = totals(kept);
  const lines = [heading, '='.repeat(heading.length), ''];
  lines.push(
    `${plural(counted.performances, 'performance')}: ` +
      joinWords([
        plural(counted.peals, 'peal'),
        plural(counted.quarters, 'quarter peal'),
        plural(counted.touches, 'touch', 'touches'),
      ]),
  );
  lines.push(`${counted.changes} changes altogether`);
  lines.push('');
  lines.push('By tower');
  lines.push(
    ...countTable(
      byTower(kept),
      (line) => line.tower,
      (line) => line.totals.performances,
    ),
  );
  lines.push('');
  lines.push('By method');
  lines.push(
    ...countTable(
      byMethod(kept),
      (line) => line.title,
      (line) => line.count,
    ),
  );
  lines.push('');
  lines.push('Most rung by');
  const top = byRinger(kept).slice(0, options.topRingers ?? 10);
  lines.push(
    ...tabulate(
      top.map((line) => [
        line.name,
        String(line.totals.performances),
        line.conducted === 0 ? '' : `${line.conducted} conducted`,
      ]),
      { align: ['left', 'right', 'left'] },
    ),
  );
  return lines;
}

/** Plain text, as an association report is printed. */
export const textWriter = Object.freeze({
  name: 'text',
  contentType: 'text/plain',
  /**
   * @param {readonly import('./performance.js').Performance[]} records
   * @param {object} [options]
   * @returns {string}
   */
  write(records, options = {}) {
    return reportLines(records, options).join('\n');
  },
});

/** One line per performance, as a list. */
export const listWriter = Object.freeze({
  name: 'list',
  contentType: 'text/plain',
  /**
   * @param {readonly import('./performance.js').Performance[]} records
   * @returns {string}
   */
  write(records) {
    return [...records].map((rung) => summaryLine(rung)).join('\n');
  },
});

/** A spreadsheet. */
export const csvWriter = Object.freeze({
  name: 'csv',
  contentType: 'text/csv',
  /**
   * @param {readonly import('./performance.js').Performance[]} records
   * @returns {string}
   */
  write(records) {
    const rows = [CSV_COLUMNS.join(',')];
    for (const rung of records) {
      rows.push(
        [
          rung.dateText,
          rung.tower,
          rung.title,
          String(rung.changes),
          rung.kind,
          rung.conductor ?? '',
          rung.composer ?? '',
          rung.band.join('; '),
        ]
          .map((cell) => escapeCsv(cell))
          .join(','),
      );
    }
    return rows.join('\n');
  },
});

/** JSON, for anything that is going to read it back. */
export const jsonWriter = Object.freeze({
  name: 'json',
  contentType: 'application/json',
  /**
   * @param {readonly import('./performance.js').Performance[]} records
   * @returns {string}
   */
  write(records) {
    return JSON.stringify(
      [...records].map((rung) => ({
        date: rung.dateText,
        tower: rung.tower,
        title: rung.title,
        changes: rung.changes,
        kind: rung.kind,
        conductor: rung.conductor,
        composer: rung.composer,
        band: [...rung.band],
        footnotes: [...rung.footnotes],
      })),
      null,
      2,
    );
  },
});

/** Every writer, by name. */
const WRITERS = Object.freeze({
  text: textWriter,
  list: listWriter,
  csv: csvWriter,
  json: jsonWriter,
});

/**
 * A writer by name.
 *
 * @param {string} name
 * @returns {Writer}
 */
export function writerNamed(name) {
  const writer = WRITERS[name];
  if (writer === undefined) {
    throw new ValidationError(
      `${JSON.stringify(name)} is not a format, so try one of ${Object.keys(WRITERS).join(', ')}`,
    );
  }
  return writer;
}

/**
 * Every format that can be written.
 *
 * @returns {string[]}
 */
export function formats() {
  return Object.keys(WRITERS);
}

/**
 * @param {readonly object[]} lines
 * @param {(line: object) => string} nameOf
 * @param {(line: object) => number} countOf
 * @returns {string[]}
 */
function countTable(lines, nameOf, countOf) {
  return tabulate(
    lines.map((line) => [nameOf(line), String(countOf(line))]),
    { align: ['left', 'right'] },
  );
}

/**
 * @param {string} cell
 * @returns {string}
 */
function escapeCsv(cell) {
  if (!/[",\n]/.test(cell)) {
    return cell;
  }
  return `"${cell.replaceAll('"', '""')}"`;
}
