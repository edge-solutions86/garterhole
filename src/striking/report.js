import { StrikingSession } from './session.js';
import { describeFault } from './faults.js';
import { bellSymbol } from '../core/bells.js';
import { tabulate, plural } from '../util/text.js';
import { ValidationError } from '../errors.js';

/**
 * Turning a striking analysis into something a band will read.
 *
 * A report that opens with a table of milliseconds gets folded up. One that
 * opens with a sentence saying how the ringing went, and only then gives the
 * numbers, gets read to the end. So the order here is deliberate: the verdict,
 * then the bells, then the faults.
 */

/**
 * The verdict on a session, in the words a tower captain would use.
 *
 * @param {number} rms
 * @returns {string}
 */
export function verdict(rms) {
  if (typeof rms !== 'number' || rms < 0) {
    throw new ValidationError(`${rms} is not a striking accuracy`);
  }
  if (rms < 20) {
    return 'excellent';
  }
  if (rms < 40) {
    return 'good';
  }
  if (rms < 70) {
    return 'fair';
  }
  return 'ragged';
}

/**
 * The opening sentence of a report.
 *
 * @param {StrikingSession} session
 * @returns {string}
 */
export function headline(session) {
  assertSession(session);
  const summary = session.accuracy();
  const rms = Math.round(summary.rms);
  return `${plural(session.rowCount, 'row')} rung, ${verdict(summary.rms)} striking at ${rms}ms`;
}

/**
 * Which way a bell leans, in words.
 *
 * @param {number} meanError
 * @returns {string}
 */
export function tendency(meanError) {
  const size = Math.round(Math.abs(meanError));
  if (size < 10) {
    return 'even';
  }
  return meanError < 0 ? `${size}ms early` : `${size}ms late`;
}

/**
 * The per bell table.
 *
 * @param {StrikingSession} session
 * @returns {string[]}
 */
export function bellTable(session) {
  assertSession(session);
  const rows = [['bell', 'blows', 'accuracy', 'tendency']];
  for (const [bell, summary] of session.byBell()) {
    rows.push([
      bellSymbol(bell),
      String(summary.blows),
      `${Math.round(summary.rms)}ms`,
      tendency(summary.meanError),
    ]);
  }
  return tabulate(rows, { align: ['left', 'right', 'right', 'left'] });
}

/**
 * The faults worth mentioning.
 *
 * @param {StrikingSession} session
 * @param {number} [limit]
 * @returns {string[]}
 */
export function faultLines(session, limit = 10) {
  assertSession(session);
  const faults = session.faults();
  if (faults.length === 0) {
    return ['no faults worth reporting'];
  }
  const lines = faults.slice(0, limit).map((fault) => describeFault(fault));
  if (faults.length > limit) {
    lines.push(`and ${faults.length - limit} more`);
  }
  return lines;
}

/**
 * The whole report.
 *
 * @param {StrikingSession} session
 * @returns {string[]}
 */
export function reportLines(session) {
  assertSession(session);
  const lines = [headline(session)];
  if (session.stoppedAt !== null) {
    lines.push(`the recording stopped making sense at row ${session.stoppedAt + 1}`);
  }
  lines.push('');
  lines.push(...bellTable(session));
  lines.push('');
  lines.push(...faultLines(session));
  return lines;
}

/**
 * @param {unknown} session
 */
function assertSession(session) {
  if (!(session instanceof StrikingSession)) {
    throw new ValidationError('a StrikingSession is needed to report on it');
  }
}
