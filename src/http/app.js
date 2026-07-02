import { Router } from './router.js';
import { Server } from './server.js';
import { addHealthRoutes } from './routes/health.js';
import { addMethodRoutes } from './routes/methods.js';
import { addTowerRoutes } from './routes/towers.js';
import { addPerformanceRoutes } from './routes/performances.js';
import { addStrikingRoutes } from './routes/striking.js';
import { Service } from '../service.js';
import { Repository } from '../store/repository.js';
import { Journal } from '../store/journal.js';
import { Snapshot } from '../store/snapshot.js';
import { Lock } from '../store/lock.js';
import { loadConfig, describeConfig } from '../config/config.js';
import { Logger } from '../util/logger.js';
import { ValidationError } from '../errors.js';

/**
 * Putting the whole thing together.
 *
 * This is the only place that knows about every layer at once, and it is
 * deliberately dull: open the store, build the service, register the routes,
 * hand the router to a server. Anything clever that lives here is something
 * that should have lived in a layer.
 *
 * The routes are registered in a stated order, and the health routes go first
 * so that a monitor is answered even if a later group of routes fails to
 * register for some reason.
 */

/** The groups of routes, in the order they are registered. */
const ROUTE_GROUPS = Object.freeze([
  ['health', addHealthRoutes],
  ['methods', addMethodRoutes],
  ['towers', addTowerRoutes],
  ['performances', addPerformanceRoutes],
  ['striking', addStrikingRoutes],
]);

/**
 * Build the application.
 *
 * @param {{
 *   config?: ReturnType<typeof loadConfig>,
 *   environment?: Record<string, string | undefined>,
 *   logger?: Logger,
 *   lock?: boolean
 * }} [options]
 * @returns {{
 *   server: Server,
 *   service: Service,
 *   repository: Repository,
 *   router: Router,
 *   config: object,
 *   logger: Logger,
 *   close: () => void
 * }}
 */
export function buildApp(options = {}) {
  const config = options.config ?? loadConfig(options.environment ?? {});
  const logger =
    options.logger ??
    new Logger({ level: config.logLevel, sink: (line) => process.stdout.write(`${line}\n`) });
  const lock = options.lock === false ? null : new Lock(config.lockPath);
  if (lock !== null) {
    lock.acquire();
  }
  const repository = new Repository({
    journal: new Journal(config.journalPath),
    snapshot: new Snapshot(config.snapshotPath),
  });
  const service = new Service({ repository, logger });
  const router = new Router();
  for (const [name, register] of ROUTE_GROUPS) {
    try {
      register(router);
    } catch (error) {
      throw new ValidationError(`the ${name} routes could not be registered: ${error.message}`);
    }
  }
  const server = new Server({
    router,
    logger,
    rateLimit: config.requestsPerMinute,
    context: { service, router, config },
  });
  logger.info('service built', { settings: describeConfig(config) });
  return {
    server,
    service,
    repository,
    router,
    config,
    logger,
    close: () => {
      repository.save();
      lock?.release();
    },
  };
}

/**
 * Build the application and start listening.
 *
 * @param {Parameters<typeof buildApp>[0]} [options]
 * @returns {Promise<{ port: number, close: () => Promise<void> }>}
 */
export async function start(options = {}) {
  const app = buildApp(options);
  const listening = await app.server.listen({ port: app.config.port, host: app.config.host });
  return {
    port: listening.port,
    close: async () => {
      await listening.close();
      app.close();
    },
  };
}

/**
 * The route groups, for the documentation and for a test that checks nothing
 * has been left out.
 *
 * @returns {string[]}
 */
export function routeGroups() {
  return ROUTE_GROUPS.map(([name]) => name);
}
