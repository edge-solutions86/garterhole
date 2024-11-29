/**
 * Argument guards. Every module in the library validates what it is handed,
 * because most of the input arrives from a person typing a touch into a form
 * and a message that names the offending value saves a support email.
 *
 * The guards throw the built in error types on purpose. A caller that wants to
 * turn a bad argument into an HTTP 400 catches `RangeError` and `TypeError`
 * rather than importing anything from here.
 */

/**
 * @param {unknown} value
 * @param {string} label how the value is described in the message
 * @returns {number}
 */
export function assertInteger(value, label) {
  if (!Number.isInteger(value)) {
    throw new RangeError(`${label} ${describe(value)} is not a whole number`);
  }
  return value;
}

/**
 * @param {unknown} value
 * @param {number} min inclusive
 * @param {number} max inclusive
 * @param {string} label
 * @returns {number}
 */
export function assertIntegerInRange(value, min, max, label) {
  if (!Number.isInteger(value) || value < min || value > max) {
    throw new RangeError(`${label} ${describe(value)} is outside ${min}..${max}`);
  }
  return value;
}

/**
 * @param {unknown} value
 * @param {string} label
 * @returns {string}
 */
export function assertNonEmptyString(value, label) {
  if (typeof value !== 'string' || value.length === 0) {
    throw new TypeError(`${label} must be a non empty string`);
  }
  return value;
}

/**
 * @param {unknown} value
 * @param {readonly unknown[]} allowed
 * @param {string} label
 * @returns {unknown}
 */
export function assertOneOf(value, allowed, label) {
  if (!allowed.includes(value)) {
    throw new RangeError(`${label} ${describe(value)} must be one of ${allowed.join(', ')}`);
  }
  return value;
}

/**
 * @param {unknown} value
 * @param {string} label
 * @returns {unknown[]}
 */
export function assertArray(value, label) {
  if (!Array.isArray(value)) {
    throw new TypeError(`${label} must be an array`);
  }
  return value;
}

/**
 * @param {unknown[]} value
 * @param {string} label
 * @returns {unknown[]}
 */
export function assertNonEmptyArray(value, label) {
  assertArray(value, label);
  if (value.length === 0) {
    throw new RangeError(`${label} must not be empty`);
  }
  return value;
}

/**
 * @param {unknown} value
 * @param {string} label
 * @returns {Function}
 */
export function assertFunction(value, label) {
  if (typeof value !== 'function') {
    throw new TypeError(`${label} must be a function`);
  }
  return value;
}

/**
 * A plain object, which is what every options bag in the library expects. A
 * class instance or an array is refused so that a mistyped argument fails at
 * the boundary instead of half way through a loop.
 *
 * @param {unknown} value
 * @param {string} label
 * @returns {Record<string, unknown>}
 */
export function assertPlainObject(value, label) {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    throw new TypeError(`${label} must be an object`);
  }
  const proto = Object.getPrototypeOf(value);
  if (proto !== Object.prototype && proto !== null) {
    throw new TypeError(`${label} must be a plain object`);
  }
  return value;
}

/**
 * Render a value for an error message without letting a huge object flood the
 * log. Strings are quoted so that a stray space is visible.
 *
 * @param {unknown} value
 * @returns {string}
 */
export function describe(value) {
  if (typeof value === 'string') {
    return JSON.stringify(value);
  }
  if (typeof value === 'number' || typeof value === 'boolean' || value === null) {
    return String(value);
  }
  if (value === undefined) {
    return 'undefined';
  }
  if (Array.isArray(value)) {
    return `an array of ${value.length}`;
  }
  return typeof value;
}
