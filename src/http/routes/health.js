import { json, text } from '../response.js';
import { VERSION, PRODUCT } from '../../version.js';
import { systemClock } from '../../util/clock.js';

/**
 * The routes a monitor asks for.
 *
 * There are two and they answer different questions. `/health` says the process
 * is up and answering, and is what a load balancer polls; it does no work, so
 * it cannot fail for a reason the caller can do anything about. `/ready` says
 * the service can actually do its job, which means the store answered, and is
 * what a deployment waits on before sending traffic.
 *
 * Keeping them apart matters. A service whose store has gone is not healthy in
 * any useful sense, but restarting it will not help, and a monitor that cannot
 * tell the difference will restart it in a loop.
 */

/**
 * Add the routes to a router.
 *
 * @param {import('../router.js').Router} router
 * @param {{ clock?: { now: () => number } }} [options]
 * @returns {import('../router.js').Router} the router
 */
export function addHealthRoutes(router, options = {}) {
  const clock = options.clock ?? systemClock();
  const startedAt = clock.now();

  router.get('/health', async () =>
    json({
      status: 'up',
      product: PRODUCT,
      version: VERSION,
      uptimeMs: clock.now() - startedAt,
    }),
  );

  router.get('/ready', async (_request, context) => {
    try {
      const counts = context.service.counts();
      return json({ status: 'ready', holding: counts });
    } catch (error) {
      return json({ status: 'not ready', reason: error.message }, { status: 503 });
    }
  });

  router.get('/routes', async (_request, context) =>
    text(
      context.router
        .list()
        .map((route) => `${route.method} ${route.pattern}`)
        .join('\n'),
    ),
  );

  return router;
}
