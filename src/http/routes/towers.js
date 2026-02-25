import { json, created, noContent } from '../response.js';
import { check, object, string, number, arrayOf, optional } from '../../validation/schema.js';
import { Tower } from '../../tower/tower.js';
import { Bell, bellWeighing } from '../../tower/bell.js';
import { slugify } from '../../util/text.js';
import { ValidationError } from '../../errors.js';

/**
 * Towers over HTTP.
 *
 * A tower is stored as the document that came in rather than as the class,
 * which keeps the stored shape stable while the class changes. The class is
 * built on the way in to check the document and on the way out to answer the
 * questions that need it, such as what stages the tower can ring.
 */

/** What a tower has to say about itself. */
const NEW_TOWER = object({
  name: string({ min: 1, max: 120 }),
  place: optional(string({ max: 120 })),
  bells: arrayOf(
    object({
      number: number({ integer: true, min: 1, max: 16 }),
      weight: optional(string({ pattern: /^\d+-[0-3]-([0-9]|1[0-9]|2[0-7])$/ })),
      weightPounds: optional(number({ min: 1 })),
      note: optional(string({ min: 1, max: 2 })),
      founder: optional(string({ max: 120 })),
      cast: optional(number({ integer: true, min: 1200, max: 2200 })),
    }),
    { min: 1, max: 16 },
  ),
});

/**
 * Add the routes to a router.
 *
 * @param {import('../router.js').Router} router
 * @returns {import('../router.js').Router}
 */
export function addTowerRoutes(router) {
  router.get('/towers', async (_request, context) => {
    const towers = context.service.listTowers();
    return json({ towers, total: towers.length });
  });

  router.post('/towers', async (request, context) => {
    const body = check(NEW_TOWER, request.body, 'the tower');
    const tower = towerFrom(body);
    const document = { ...body, id: tower.id, size: tower.size, stages: tower.stages() };
    context.service.putTower(tower.id, document);
    return created(`/towers/${tower.id}`, document);
  });

  router.get('/towers/:id', async (request, context) =>
    json(context.service.getTower(request.param('id'))),
  );

  router.get('/towers/:id/bells', async (request, context) => {
    const document = context.service.getTower(request.param('id'));
    const tower = towerFrom(document);
    return json({
      bells: tower.bells.map((bell) => ({
        number: bell.number,
        weight: bell.weightText,
        weightPounds: bell.weightPounds,
        note: bell.note,
      })),
      tenor: tower.tenor.weightText,
      totalWeightPounds: tower.totalWeightPounds,
    });
  });

  router.delete('/towers/:id', async (request, context) => {
    const removed = context.service.repository.delete('towers', request.param('id'));
    return removed ? noContent() : json({ removed: false }, { status: 404 });
  });

  return router;
}

/**
 * Build the class from a document, so that a tower with a gap in its ring is
 * refused rather than stored.
 *
 * @param {object} document
 * @returns {Tower}
 */
export function towerFrom(document) {
  const bells = document.bells.map((entry) => bellFrom(entry));
  const tower = new Tower({ name: document.name, place: document.place, bells });
  if (document.id !== undefined && document.id !== tower.id) {
    throw new ValidationError(
      `the tower says its id is ${JSON.stringify(document.id)} but its name gives ${JSON.stringify(tower.id)}`,
    );
  }
  return tower;
}

/**
 * @param {object} entry
 * @returns {Bell}
 */
function bellFrom(entry) {
  if (entry.weight !== undefined) {
    return bellWeighing(entry.number, entry.weight, {
      note: entry.note,
      founder: entry.founder,
      cast: entry.cast,
    });
  }
  if (entry.weightPounds === undefined) {
    throw new ValidationError(`bell ${entry.number} does not say what it weighs`);
  }
  return new Bell(entry);
}

/**
 * The identifier a tower would be filed under.
 *
 * @param {{ name: string, place?: string }} document
 * @returns {string}
 */
export function towerId(document) {
  return slugify(
    document.place === undefined ? document.name : `${document.place} ${document.name}`,
  );
}
