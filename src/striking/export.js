import { StrikingSession } from './session.js';
import { rollingAccuracy } from './window.js';
import { describeFault } from './faults.js';
import { ValidationError } from '../errors.js';

/**
 * Getting a striking analysis out of the service.
 *
 * A band wants the numbers in a spreadsheet so that they can draw their own
 * graph of how the practice went, and the recorder that produced the strike
 * times usually wants them back in a form it can play alongside the audio. Both
 * are the same data written differently.
 */

/**
 * Every blow, as rows for a spreadsheet.
 *
 * @param {StrikingSession} session
 * @returns {string}
 */
export function blowsAsCsv(session) {
  assertSession(session);
  const rows = ['row,place,bell,at,expected,error'];
  for (const entry of session.errors) {
    rows.push(
      [
        entry.rowIndex,
        entry.place,
        entry.bell,
        entry.at,
        Math.round(entry.expected),
        Math.round(entry.error),
      ].join(','),
    );
  }
  return rows.join('\n');
}

/**
 * The per bell summary, as rows for a spreadsheet.
 *
 * @param {StrikingSession} session
 * @returns {string}
 */
export function bellsAsCsv(session) {
  assertSession(session);
  const rows = ['bell,blows,rms,meanError'];
  for (const [bell, summary] of session.byBell()) {
    rows.push([bell, summary.blows, round(summary.rms), round(summary.meanError)].join(','));
  }
  return rows.join('\n');
}

/**
 * The whole analysis as a document something else can read.
 *
 * @param {StrikingSession} session
 * @param {{ windowRows?: number }} [options]
 * @returns {object}
 */
export function asDocument(session, options = {}) {
  assertSession(session);
  const summary = session.accuracy();
  return {
    format: 'garterhole.striking',
    version: 1,
    stage: session.stream.stage,
    rows: session.rowCount,
    model: {
      interval: round(session.model.interval),
      handstrokeGap: round(session.model.handstrokeGap),
      pealMinutes: round(session.model.pealMinutes()),
    },
    accuracy: {
      blows: summary.blows,
      rms: round(summary.rms),
      meanError: round(summary.meanError),
    },
    bells: [...session.byBell().entries()].map(([bell, entry]) => ({
      bell,
      blows: entry.blows,
      rms: round(entry.rms),
      meanError: round(entry.meanError),
    })),
    windows: rollingAccuracy(session.errors, { rows: options.windowRows ?? 20 }).map((window) => ({
      from: window.from,
      to: window.to,
      rms: round(window.rms),
    })),
    faults: session.faults().map((fault) => describeFault(fault)),
    stoppedAt: session.stoppedAt,
  };
}

/**
 * The document as JSON.
 *
 * @param {StrikingSession} session
 * @param {{ windowRows?: number }} [options]
 * @returns {string}
 */
export function asJson(session, options = {}) {
  return JSON.stringify(asDocument(session, options), null, 2);
}

/**
 * @param {number} value
 * @returns {number}
 */
function round(value) {
  return Math.round(value * 10) / 10;
}

/**
 * @param {unknown} session
 */
function assertSession(session) {
  if (!(session instanceof StrikingSession)) {
    throw new ValidationError('a StrikingSession is needed to export it');
  }
}
