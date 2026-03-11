import { json, created, text } from '../response.js';
import { paginate } from '../pagination.js';
import { check, object, string, arrayOf, optional, number } from '../../validation/schema.js';
import { methodTitle, isoDate, changeCount } from '../../validation/types.js';
import { Performance } from '../../records/performance.js';
import { validatePerformance } from '../../records/validate.js';
import { writerNamed, formats } from '../../records/writers.js';
import { totals, byRinger, byTower } from '../../records/statistics.js';
import { ValidationError } from '../../errors.js';

/**
 * Performance records over HTTP.
 *
 * The report route is the one a secretary uses, and it can answer in any of the
 * formats the writers know. The format comes from a query parameter rather than
 * content negotiation, because a secretary is going to paste the URL into a
 * browser and wants a spreadsheet without setting a header.
 */

/** What a performance has to say about itself. */
const NEW_PERFORMANCE = object({
  tower: string({ min: 1, max: 120 }),
  date: isoDate(),
  title: methodTitle(),
  changes: changeCount(),
  band: arrayOf(string({ min: 1, max: 80 }), { min: 3, max: 17 }),
  conductor: optional(string({ min: 1, max: 80 })),
  composer: optional(string({ min: 1, max: 80 })),
  duration: optional(number({ integer: true, min: 1, max: 1000 })),
  footnotes: optional(arrayOf(string({ max: 500 }), { max: 20 })),
});

/**
 * Add the routes to a router.
 *
 * @param {import('../router.js').Router} router
 * @returns {import('../router.js').Router}
 */
export function addPerformanceRoutes(router) {
  router.get('/performances', async (request, context) => {
    const records = context.service.listPerformances();
    const wantedTower = request.queryValue('tower');
    const wantedRinger = request.queryValue('ringer');
    const filtered = records.filter((entry) => {
      if (
        wantedTower !== undefined &&
        !entry.tower.toLowerCase().includes(wantedTower.toLowerCase())
      ) {
        return false;
      }
      if (wantedRinger !== undefined && !entry.band.includes(wantedRinger)) {
        return false;
      }
      return true;
    });
    return json(paginate(request, filtered, 'performances'));
  });

  router.post('/performances', async (request, context) => {
    const body = check(NEW_PERFORMANCE, request.body, 'the performance');
    const filed = context.service.filePerformance(body);
    return created(`/performances/${filed.id}`, filed);
  });

  router.get('/performances/:id', async (request, context) => {
    const found = context.service.repository.find('performances', request.param('id'));
    if (found === undefined) {
      return json(
        { error: { code: 'not_found', message: 'no such performance' } },
        { status: 404 },
      );
    }
    return json(found);
  });

  router.post('/performances/check', async (request) => {
    const body = check(NEW_PERFORMANCE, request.body, 'the performance');
    const verdict = validatePerformance(new Performance({ ...body, date: new Date(body.date) }));
    return json(verdict);
  });

  router.get('/report', async (request, context) => {
    const format = request.queryValue('format', 'text');
    if (!formats().includes(format)) {
      throw new ValidationError(
        `${JSON.stringify(format)} is not a format, so try one of ${formats().join(', ')}`,
      );
    }
    const records = context.service
      .listPerformances()
      .map((entry) => new Performance({ ...entry, date: new Date(entry.date) }));
    const writer = writerNamed(format);
    const written = writer.write(records, { title: request.queryValue('title') });
    if (writer.contentType === 'application/json') {
      return json(JSON.parse(written));
    }
    return text(written, { headers: { 'content-type': `${writer.contentType}; charset=utf-8` } });
  });

  router.get('/statistics', async (_request, context) => {
    const records = context.service
      .listPerformances()
      .map((entry) => new Performance({ ...entry, date: new Date(entry.date) }));
    return json({
      totals: totals(records),
      byTower: byTower(records),
      byRinger: byRinger(records).slice(0, 20),
    });
  });

  return router;
}
