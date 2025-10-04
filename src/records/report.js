import { Performance } from './performance.js';
import { plural } from '../util/text.js';
import { bellSymbol } from '../core/bells.js';
import { ValidationError } from '../errors.js';

/**
 * Writing performances up.
 *
 * A single performance is written as a peal board: the tower, the date, the
 * length and the method, then the band one bell to a line. That is what lives
 * here. Writing a whole year of them out belongs to the writers, which cover
 * text, lists, spreadsheets and JSON without this module knowing about any of
 * them.
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
 * @param {unknown} rung
 */
function assertPerformance(rung) {
  if (!(rung instanceof Performance)) {
    throw new ValidationError('a Performance is needed to write it up');
  }
}
