import { byRinger, totals } from './statistics.js';
import { tabulate, plural } from '../util/text.js';
import { ValidationError } from '../errors.js';

/**
 * The table an association prints at the end of the year.
 *
 * Counting performances is not the only way to order it, and which way is
 * chosen changes who is at the top. Somebody who rings fifty quarters is not
 * obviously ahead of somebody who rings four peals, so the ordering is a
 * parameter with three honest choices rather than a decision buried in a sort.
 */

/** How a leaderboard may be ordered. */
export const ORDERINGS = Object.freeze(['performances', 'peals', 'changes', 'conducted']);

/**
 * The score a ringer gets under an ordering.
 *
 * @param {{ totals: object, conducted: number }} line
 * @param {string} ordering
 * @returns {number}
 */
export function scoreOf(line, ordering) {
  if (!ORDERINGS.includes(ordering)) {
    throw new ValidationError(
      `${JSON.stringify(ordering)} is not an ordering, so try one of ${ORDERINGS.join(', ')}`,
    );
  }
  if (ordering === 'conducted') {
    return line.conducted;
  }
  if (ordering === 'peals') {
    return line.totals.peals;
  }
  if (ordering === 'changes') {
    return line.totals.changes;
  }
  return line.totals.performances;
}

/**
 * The leaderboard.
 *
 * @param {Iterable<import('./performance.js').Performance>} records
 * @param {{ ordering?: string, top?: number }} [options]
 * @returns {{ place: number, name: string, score: number, totals: object, conducted: number }[]}
 */
export function leaderboard(records, options = {}) {
  const ordering = options.ordering ?? 'performances';
  const top = options.top ?? 20;
  if (!Number.isInteger(top) || top < 1) {
    throw new ValidationError(`a leaderboard of ${top} places is not one`);
  }
  const lines = byRinger(records).map((line) => ({
    name: line.name,
    score: scoreOf(line, ordering),
    totals: line.totals,
    conducted: line.conducted,
  }));
  lines.sort((left, right) => right.score - left.score || left.name.localeCompare(right.name));
  return lines
    .filter((line) => line.score > 0)
    .slice(0, top)
    .map((line, index) => ({ place: index + 1, ...line }));
}

/**
 * The leaderboard as lines of text.
 *
 * @param {Iterable<import('./performance.js').Performance>} records
 * @param {{ ordering?: string, top?: number }} [options]
 * @returns {string[]}
 */
export function leaderboardLines(records, options = {}) {
  const ordering = options.ordering ?? 'performances';
  const board = leaderboard(records, options);
  if (board.length === 0) {
    return ['nobody has rung anything'];
  }
  return [
    `Most ${ordering}`,
    ...tabulate(
      board.map((line) => [String(line.place), line.name, String(line.score)]),
      { align: ['right', 'left', 'right'] },
    ),
  ];
}

/**
 * A sentence summing the year up, which is what goes at the top of the report.
 *
 * @param {Iterable<import('./performance.js').Performance>} records
 * @returns {string}
 */
export function summary(records) {
  const kept = [...records];
  const counted = totals(kept);
  const board = leaderboard(kept, { top: 1 });
  if (board.length === 0) {
    return 'nothing was rung';
  }
  return (
    `${plural(counted.performances, 'performance')} in ${plural(counted.changes, 'change')}, ` +
    `most of them by ${board[0].name} with ${board[0].score}`
  );
}
