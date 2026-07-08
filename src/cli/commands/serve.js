import { start } from '../../http/app.js';
import { loadConfig, describeConfig } from '../../config/config.js';
import { ValidationError } from '../../errors.js';

/**
 * Starting the service from the command line.
 *
 * This was a branch inside the dispatcher, which meant the one command that
 * does not return was the one command with no test. It is a command like the
 * others now, and the thing that makes it testable is that it gives back the
 * handle rather than waiting for ever: the caller decides whether to hold the
 * process open.
 */

/** The flags this command takes. */
export const SERVE_FLAGS = Object.freeze({
  port: { takesValue: true, short: 'p' },
  host: { takesValue: true },
  data: { takesValue: true, short: 'd' },
  json: {},
  quiet: {},
});

/**
 * The environment the settings should be read from, with anything named on the
 * command line laid over it.
 *
 * @param {import('../args.js').ParsedArgs} parsed
 * @param {Record<string, string | undefined>} [environment]
 * @returns {Record<string, string | undefined>}
 */
export function environmentFor(parsed, environment = {}) {
  const overrides = {};
  if (parsed.flags.port !== undefined && parsed.flags.port !== true) {
    overrides.GARTERHOLE_PORT = String(parsed.flags.port);
  }
  if (parsed.flags.host !== undefined && parsed.flags.host !== true) {
    overrides.GARTERHOLE_HOST = String(parsed.flags.host);
  }
  if (parsed.flags.data !== undefined && parsed.flags.data !== true) {
    overrides.GARTERHOLE_DATA = String(parsed.flags.data);
  }
  return { ...environment, ...overrides };
}

/**
 * `serve --port 8080 --data ./data`
 *
 * @param {import('../args.js').ParsedArgs} parsed
 * @param {{
 *   output: import('../output.js').Output,
 *   environment?: Record<string, string | undefined>,
 *   listen?: typeof start
 * }} context
 * @returns {Promise<{ port: number, close: () => Promise<void> }>}
 */
export async function serve(parsed, context) {
  if (context?.output === undefined) {
    throw new ValidationError('serving needs somewhere to write to');
  }
  const environment = environmentFor(parsed, context.environment);
  const config = loadConfig(environment);
  const listen = context.listen ?? start;
  const running = await listen({ environment });
  context.output.note(describeConfig({ ...config, port: running.port }));
  context.output.answer({ port: running.port, dataDirectory: config.dataDirectory });
  return running;
}
