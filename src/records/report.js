import { Performance } from './performance.js';
import { totals, byRinger, byTower, byMethod } from './statistics.js';
import { tabulate, plural, joinWords } from '../util/text.js';
import { bellSymbol } from '../core/bells.js';
import { ValidationError } from '../errors.js';

/**
 * Writing performances up.
 *
 * There are two shapes of report and they are not variations of each other. A
 * single performance is written as a peal board: the tower, the date, the
 * length and the method, then the band one bell to a line. A year of them is
 * written as a set of tables.
 */

/**
 * One performance, laid out the way it goes on a board.
 *
 * @param {Performance} rung
 * @returns {string[]}
 */
export function boardLines(rung) {
  assertPerformance(rung);
  const lines = [rung.tower, rung.dateText, `${rung.changes} ${rung.title}`];
  if (rung.composer !== null) {
    lines.push(`composed by ${rung.composer}`);
  }
  if (rung.duration !== null) {
    lines.push(`${plural(rung.duration, 'minute')}`);
  }
  lines.push('');
  rung.band.forEach((name, index) => {
    const conductor = name === rung.conductor ? ' (c)' : '';
    lines.push(`${bellSymbol(index + 1)}  ${name}${conductor}`);
  });
  if (rung.footnotes.length > 0) {
    lines.push('');
    lines.push(...rung.footnotes);
  }
  return lines;
}

/**
 * The one line form, which is what an association report prints.
 *
 * @param {Performance} rung
 * @returns {string}
 */
export function summaryLine(rung) {
  assertPerformance(rung);
  const conductor = rung.conductor === null ? '' : ` (c: ${rung.conductor})`;
  return `${rung.dateText}  ${rung.tower}  ${rung.changes} ${rung.title}${conductor}`;
}

/**
 * A whole set of performances, written as a report.
 *
 * @param {Iterable<Performance>} records
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
    ...tabulate(
      byTower(kept).map((line) => [line.tower, String(line.totals.performances)]),
      { align: ['left', 'right'] },
    ),
  );
  lines.push('');
  lines.push('By method');
  lines.push(
    ...tabulate(
      byMethod(kept).map((line) => [line.title, String(line.count)]),
      { align: ['left', 'right'] },
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

/**
 * @param {unknown} rung
 */
function assertPerformance(rung) {
  if (!(rung instanceof Performance)) {
    throw new ValidationError('a Performance is needed to write it up');
  }
}
