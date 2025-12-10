import { join } from 'node:path';
import { toInteger, toBoolean } from '../validation/coerce.js';
import { ConfigError } from '../errors.js';

/**
 * Where the settings come from and what they mean.
 *
 * Every setting has a default that works, so the service starts with no
 * configuration at all. That is worth insisting on: a tower installs this on a
 * spare machine in the vestry and nobody is going to write a configuration file
 * before finding out whether it runs.
 *
 * Anything wrong in the environment is fatal at start up rather than at the
 * first request, because a service running with a port it did not want is
 * harder to notice than one that refused to start.
 */

/** The settings, their defaults and how their text is read. */
const SETTINGS = Object.freeze({
  port: { variable: 'GARTERHOLE_PORT', fallback: 8080, read: toInteger },
  host: { variable: 'GARTERHOLE_HOST', fallback: '127.0.0.1', read: (value) => String(value) },
  dataDirectory: {
    variable: 'GARTERHOLE_DATA',
    fallback: join('.', 'data'),
    read: (value) => String(value),
  },
  logLevel: {
    variable: 'GARTERHOLE_LOG_LEVEL',
    fallback: 'info',
    read: (value) => readLevel(value),
  },
  requestsPerMinute: {
    variable: 'GARTERHOLE_RATE_LIMIT',
    fallback: 120,
    read: toInteger,
  },
  readOnly: { variable: 'GARTERHOLE_READ_ONLY', fallback: false, read: toBoolean },
});

/** The log levels, quietest last. */
export const LOG_LEVELS = Object.freeze(['debug', 'info', 'warn', 'error', 'silent']);

/**
 * Read the settings out of an environment.
 *
 * @param {Record<string, string | undefined>} [environment]
 * @returns {{
 *   port: number,
 *   host: string,
 *   dataDirectory: string,
 *   logLevel: string,
 *   requestsPerMinute: number,
 *   readOnly: boolean,
 *   journalPath: string,
 *   snapshotPath: string,
 *   lockPath: string
 * }}
 */
export function loadConfig(environment = {}) {
  const settings = {};
  for (const [name, setting] of Object.entries(SETTINGS)) {
    const text = environment[setting.variable];
    if (text === undefined || text === '') {
      settings[name] = setting.fallback;
      continue;
    }
    try {
      settings[name] = setting.read(text, setting.variable);
    } catch (error) {
      throw new ConfigError(`${setting.variable} is wrong: ${error.message}`, {
        details: { variable: setting.variable, value: text },
      });
    }
  }
  if (settings.port < 1 || settings.port > 65535) {
    throw new ConfigError(`GARTERHOLE_PORT must be a port number, not ${settings.port}`);
  }
  if (settings.requestsPerMinute < 1) {
    throw new ConfigError(
      `GARTERHOLE_RATE_LIMIT must allow at least one request, not ${settings.requestsPerMinute}`,
    );
  }
  return {
    ...settings,
    journalPath: join(settings.dataDirectory, 'journal.jsonl'),
    snapshotPath: join(settings.dataDirectory, 'snapshot.json'),
    lockPath: join(settings.dataDirectory, 'lock'),
  };
}

/**
 * The settings written out for the log line at start up. Nothing here is
 * secret, but the habit of listing what is printed rather than printing the
 * whole object is worth keeping.
 *
 * @param {ReturnType<typeof loadConfig>} config
 * @returns {string}
 */
export function describeConfig(config) {
  return [
    `listening on ${config.host}:${config.port}`,
    `data in ${config.dataDirectory}`,
    `log level ${config.logLevel}`,
    config.readOnly ? 'read only' : 'accepting writes',
  ].join(', ');
}

/**
 * The names of every setting and the variable it reads.
 *
 * @returns {{ name: string, variable: string, fallback: unknown }[]}
 */
export function settingsList() {
  return Object.entries(SETTINGS).map(([name, setting]) => ({
    name,
    variable: setting.variable,
    fallback: setting.fallback,
  }));
}

/**
 * @param {string} value
 * @returns {string}
 */
function readLevel(value) {
  const level = String(value).toLowerCase();
  if (!LOG_LEVELS.includes(level)) {
    throw new ConfigError(`must be one of ${LOG_LEVELS.join(', ')}`);
  }
  return level;
}
