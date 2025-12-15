import { LOG_LEVELS } from '../config/config.js';
import { ValidationError } from '../errors.js';

/**
 * Logging.
 *
 * One line per event, as JSON, with the fields the reader will want to filter
 * on at the front. A tower running this reads the log with `grep`, and an
 * association running several of them feeds it into something that wants
 * structure, so JSON serves both.
 *
 * The logger takes the sink and the clock rather than reaching for the console
 * and the wall clock. That is not ceremony: it is what makes the tests able to
 * assert on what was logged, and what lets the HTTP layer write a request log
 * that a caller can turn off.
 */
export class Logger {
  /**
   * @param {{
   *   level?: string,
   *   sink?: (line: string) => void,
   *   now?: () => Date,
   *   fields?: Record<string, unknown>
   * }} [options]
   */
  constructor(options = {}) {
    const level = options.level ?? 'info';
    if (!LOG_LEVELS.includes(level)) {
      throw new ValidationError(`${JSON.stringify(level)} is not a log level`);
    }
    this.level = level;
    this.sink = options.sink ?? (() => {});
    this.now = options.now ?? (() => new Date());
    this.fields = Object.freeze({ ...(options.fields ?? {}) });
  }

  /**
   * A logger that writes the same fields on every line, on top of this one's.
   *
   * @param {Record<string, unknown>} fields
   * @returns {Logger}
   */
  with(fields) {
    return new Logger({
      level: this.level,
      sink: this.sink,
      now: this.now,
      fields: { ...this.fields, ...fields },
    });
  }

  /**
   * True when a line at this level would be written.
   *
   * @param {string} level
   * @returns {boolean}
   */
  writes(level) {
    if (!LOG_LEVELS.includes(level)) {
      throw new ValidationError(`${JSON.stringify(level)} is not a log level`);
    }
    if (this.level === 'silent') {
      return false;
    }
    return LOG_LEVELS.indexOf(level) >= LOG_LEVELS.indexOf(this.level);
  }

  /**
   * Write a line.
   *
   * @param {string} level
   * @param {string} message
   * @param {Record<string, unknown>} [fields]
   * @returns {boolean} true when the line was written
   */
  log(level, message, fields = {}) {
    if (!this.writes(level)) {
      return false;
    }
    const line = {
      at: this.now().toISOString(),
      level,
      message,
      ...this.fields,
      ...fields,
    };
    this.sink(JSON.stringify(line));
    return true;
  }

  /**
   * @param {string} message
   * @param {Record<string, unknown>} [fields]
   * @returns {boolean}
   */
  debug(message, fields) {
    return this.log('debug', message, fields);
  }

  /**
   * @param {string} message
   * @param {Record<string, unknown>} [fields]
   * @returns {boolean}
   */
  info(message, fields) {
    return this.log('info', message, fields);
  }

  /**
   * @param {string} message
   * @param {Record<string, unknown>} [fields]
   * @returns {boolean}
   */
  warn(message, fields) {
    return this.log('warn', message, fields);
  }

  /**
   * Write an error, unpacking it into fields rather than stringifying it, since
   * an Error turns into an empty object under JSON.
   *
   * @param {string} message
   * @param {unknown} [error]
   * @param {Record<string, unknown>} [fields]
   * @returns {boolean}
   */
  error(message, error, fields = {}) {
    const details =
      error instanceof Error
        ? { error: error.message, errorName: error.name, code: error.code }
        : error === undefined
          ? {}
          : { error: String(error) };
    return this.log('error', message, { ...details, ...fields });
  }
}

/**
 * A logger that collects its lines, which is what the tests use.
 *
 * @param {{ level?: string, now?: () => Date }} [options]
 * @returns {{ logger: Logger, lines: string[], records: () => object[] }}
 */
export function collectingLogger(options = {}) {
  const lines = [];
  const logger = new Logger({
    level: options.level ?? 'debug',
    now: options.now ?? (() => new Date(0)),
    sink: (line) => lines.push(line),
  });
  return { logger, lines, records: () => lines.map((line) => JSON.parse(line)) };
}

/**
 * A logger that writes nothing, for the code paths that want one and do not
 * care.
 *
 * @returns {Logger}
 */
export function silentLogger() {
  return new Logger({ level: 'silent' });
}
