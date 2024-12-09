/**
 * The error taxonomy. Everything the library throws on purpose is one of these,
 * which lets the HTTP layer map a failure onto a status code without inspecting
 * messages, and lets the CLI decide whether to print a stack trace.
 *
 * Guard failures from `util/assert.js` stay as the built in `RangeError` and
 * `TypeError`. Those mean the caller passed nonsense, which is a programming
 * mistake rather than a rejected document.
 */

/**
 * Base class. Carries a stable machine readable `code` and an optional bag of
 * details that is safe to serialise.
 */
export class GarterholeError extends Error {
  /**
   * @param {string} message
   * @param {{ code?: string, details?: Record<string, unknown>, cause?: unknown }} [options]
   */
  constructor(message, options = {}) {
    super(message, options.cause === undefined ? undefined : { cause: options.cause });
    this.name = new.target.name;
    this.code = options.code ?? 'garterhole_error';
    this.details = Object.freeze({ ...(options.details ?? {}) });
  }

  /**
   * @returns {{ name: string, code: string, message: string, details: Record<string, unknown> }}
   */
  toJSON() {
    return {
      name: this.name,
      code: this.code,
      message: this.message,
      details: { ...this.details },
    };
  }
}

/**
 * A document failed its own rules: a touch that does not come round, a tower
 * with two bells claiming the same number, a record with no ringers.
 */
export class ValidationError extends GarterholeError {
  /**
   * @param {string} message
   * @param {{ details?: Record<string, unknown>, issues?: unknown[] }} [options]
   */
  constructor(message, options = {}) {
    super(message, { code: 'validation_failed', details: options.details });
    /** @type {readonly unknown[]} */
    this.issues = Object.freeze([...(options.issues ?? [])]);
  }

  toJSON() {
    return { ...super.toJSON(), issues: [...this.issues] };
  }
}

/**
 * Text that should have been place notation, a row or a touch could not be
 * read. Carries where in the source the reader gave up, which is what makes a
 * useful message in a form.
 */
export class NotationError extends GarterholeError {
  /**
   * @param {string} message
   * @param {{ source?: string, position?: number, details?: Record<string, unknown> }} [options]
   */
  constructor(message, options = {}) {
    super(message, {
      code: 'notation_error',
      details: {
        ...(options.details ?? {}),
        source: options.source,
        position: options.position,
      },
    });
    /** @type {string | undefined} */
    this.source = options.source;
    /** @type {number | undefined} */
    this.position = options.position;
  }
}

/**
 * Something that was looked up by identifier is not there.
 */
export class NotFoundError extends GarterholeError {
  /**
   * @param {string} kind what was being looked for, for example `method`
   * @param {string} id
   */
  constructor(kind, id) {
    super(`no ${kind} with id ${JSON.stringify(id)}`, {
      code: 'not_found',
      details: { kind, id },
    });
    this.kind = kind;
    this.id = id;
  }
}

/**
 * A write lost a race, or would have created a duplicate.
 */
export class ConflictError extends GarterholeError {
  /**
   * @param {string} message
   * @param {{ details?: Record<string, unknown> }} [options]
   */
  constructor(message, options = {}) {
    super(message, { code: 'conflict', details: options.details });
  }
}

/**
 * The journal or a snapshot on disk is unusable.
 */
export class StorageError extends GarterholeError {
  /**
   * @param {string} message
   * @param {{ details?: Record<string, unknown>, cause?: unknown }} [options]
   */
  constructor(message, options = {}) {
    super(message, { code: 'storage_error', details: options.details, cause: options.cause });
  }
}

/**
 * Configuration is missing or contradictory, which is fatal at start up.
 */
export class ConfigError extends GarterholeError {
  /**
   * @param {string} message
   * @param {{ details?: Record<string, unknown> }} [options]
   */
  constructor(message, options = {}) {
    super(message, { code: 'config_error', details: options.details });
  }
}

/**
 * @param {unknown} error
 * @returns {boolean} true when the error came from this library on purpose
 */
export function isGarterholeError(error) {
  return error instanceof GarterholeError;
}
