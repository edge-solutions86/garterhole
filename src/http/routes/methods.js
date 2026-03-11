import { json, created, noContent } from '../response.js';
import { paginate } from '../pagination.js';
import { check, object, string } from '../../validation/schema.js';
import { stage, placeNotation, methodTitle, touch } from '../../validation/types.js';
import { ValidationError } from '../../errors.js';

/**
 * The method library over HTTP.
 *
 * The interesting one is the analyse route, which takes a calling and gives
 * back what it actually comes to. A caller sends the calling they mean to ring
 * and finds out before the band turns up whether it is true, how long it is and
 * how it is built.
 *
 * Nothing here trusts the class or the lead head a caller sends. Those are
 * worked out from the notation on the way in, which is why the request body has
 * no field for either of them.
 */

/** What a new method has to say about itself. */
const NEW_METHOD = object({
  name: string({ min: 1, max: 80 }),
  stage: stage(),
  notation: string({ min: 1, max: 400 }),
});

/** What an analysis request has to say. */
const ANALYSIS = object({ touch: touch() });

/**
 * Add the routes to a router.
 *
 * @param {import('../router.js').Router} router
 * @returns {import('../router.js').Router}
 */
export function addMethodRoutes(router) {
  router.get('/methods', async (request, context) => {
    const methods = context.service.listMethods();
    const wantedStage = request.queryValue('stage');
    const wantedClass = request.queryValue('class');
    const filtered = methods.filter((entry) => {
      if (wantedStage !== undefined && String(entry.stage) !== wantedStage) {
        return false;
      }
      if (wantedClass !== undefined && entry.class !== wantedClass) {
        return false;
      }
      return true;
    });
    return json(paginate(request, filtered, 'methods'));
  });

  router.post('/methods', async (request, context) => {
    const body = check(NEW_METHOD, request.body, 'the method');
    checkNotationAgainstStage(body);
    const document = context.service.addMethod(body);
    return created(`/methods/${encodeURIComponent(document.title)}`, document);
  });

  router.get('/methods/:title', async (request, context) => {
    check(methodTitle(), request.param('title'), 'the title');
    return json(context.service.describeMethod(request.param('title')));
  });

  router.delete('/methods/:title', async (request, context) => {
    const removed = context.service.removeMethod(request.param('title'));
    return removed ? noContent() : json({ removed: false }, { status: 404 });
  });

  router.post('/methods/:title/analyse', async (request, context) => {
    const body = check(ANALYSIS, request.body, 'the analysis request');
    return json(context.service.analyse({ title: request.param('title'), touch: body.touch }));
  });

  return router;
}

/**
 * The notation has to be readable at the stage the caller named, and the
 * schema cannot check one field against another, so it is done here.
 *
 * @param {{ stage: number, notation: string }} body
 */
function checkNotationAgainstStage(body) {
  const problems = placeNotation({ stage: body.stage })(body.notation, 'notation');
  if (problems.length > 0) {
    throw new ValidationError(`the method is wrong: notation ${problems[0].message}`, {
      issues: problems,
    });
  }
}
