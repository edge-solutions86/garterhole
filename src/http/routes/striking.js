import { json, text } from '../response.js';
import { check, object, arrayOf, number, optional } from '../../validation/schema.js';
import { stage } from '../../validation/types.js';
import { analyseStriking } from '../../striking/session.js';
import { reportLines } from '../../striking/report.js';
import { asDocument } from '../../striking/export.js';
import { blowsAsCsv, bellsAsCsv } from '../../striking/export.js';
import { ValidationError } from '../../errors.js';

/**
 * Striking analysis over HTTP.
 *
 * The recorder in the tower posts what it heard and gets back the analysis.
 * Nothing is stored: a striking recording is large, it is only interesting for
 * about a week, and a tower that wants to keep one can keep the file it posted.
 *
 * The lenient flag matters more than it looks. A recording with one bad row in
 * the middle is the usual case rather than the exception, and a service that
 * refuses the whole thing is a service nobody uses twice.
 */

/** What a recording has to say about itself. */
const RECORDING = object({
  stage: stage(),
  strikes: arrayOf(
    object({
      bell: number({ integer: true, min: 1, max: 16 }),
      at: number({ min: 0 }),
    }),
    { min: 2, max: 200000 },
  ),
  lenient: optional(object({})),
});

/**
 * Add the routes to a router.
 *
 * @param {import('../router.js').Router} router
 * @returns {import('../router.js').Router}
 */
export function addStrikingRoutes(router) {
  router.post('/striking', async (request) => {
    const session = sessionFrom(request);
    return json(asDocument(session, { windowRows: windowRowsOf(request) }));
  });

  router.post('/striking/report', async (request) => {
    const session = sessionFrom(request);
    return text(reportLines(session).join('\n'));
  });

  router.post('/striking/blows.csv', async (request) => {
    const session = sessionFrom(request);
    return text(blowsAsCsv(session), { headers: { 'content-type': 'text/csv; charset=utf-8' } });
  });

  router.post('/striking/bells.csv', async (request) => {
    const session = sessionFrom(request);
    return text(bellsAsCsv(session), { headers: { 'content-type': 'text/csv; charset=utf-8' } });
  });

  return router;
}

/**
 * Read a recording out of a request and analyse it.
 *
 * @param {import('../request.js').Request} request
 * @returns {import('../../striking/session.js').StrikingSession}
 */
export function sessionFrom(request) {
  const body = check(RECORDING, request.body, 'the recording');
  const lenient = request.queryValue('lenient') !== 'false';
  return analyseStriking(body.stage, body.strikes, { lenient });
}

/**
 * How many rows a window covers, from the query.
 *
 * @param {import('../request.js').Request} request
 * @returns {number}
 */
export function windowRowsOf(request) {
  const asked = request.queryValue('window');
  if (asked === undefined) {
    return 20;
  }
  const rows = Number(asked);
  if (!Number.isInteger(rows) || rows < 1 || rows > 1000) {
    throw new ValidationError(`a window of ${asked} rows is not one this will measure`);
  }
  return rows;
}
