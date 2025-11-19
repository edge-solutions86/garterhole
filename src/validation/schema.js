import { ValidationError } from '../errors.js';

/**
 * Checking the shape of something that came in over the wire.
 *
 * There is no dependency here on purpose. A schema library is a large thing to
 * take on for what this needs, which is a dozen field types and a list of
 * problems rather than the first one.
 *
 * A schema is a function from a value and a path to a list of problems. That is
 * the whole idea; everything below builds bigger schemas out of smaller ones.
 * The list rather than an exception matters: a form with three empty fields
 * should say so once, not three times in a row.
 */

/**
 * @typedef {{ path: string, message: string }} Problem
 * @typedef {(value: unknown, path?: string) => Problem[]} Schema
 */

/**
 * @param {string} path
 * @param {string} message
 * @returns {Problem[]}
 */
function problem(path, message) {
  return [{ path: path === '' ? '(root)' : path, message }];
}

/**
 * Anything at all, including undefined.
 *
 * @returns {Schema}
 */
export function anything() {
  return () => [];
}

/**
 * @param {{ min?: number, max?: number, pattern?: RegExp }} [rules]
 * @returns {Schema}
 */
export function string(rules = {}) {
  return (value, path = '') => {
    if (typeof value !== 'string') {
      return problem(path, 'must be a string');
    }
    if (rules.min !== undefined && value.length < rules.min) {
      return problem(path, `must be at least ${rules.min} characters`);
    }
    if (rules.max !== undefined && value.length > rules.max) {
      return problem(path, `must be at most ${rules.max} characters`);
    }
    if (rules.pattern !== undefined && !rules.pattern.test(value)) {
      return problem(path, `must match ${rules.pattern}`);
    }
    return [];
  };
}

/**
 * @param {{ min?: number, max?: number, integer?: boolean }} [rules]
 * @returns {Schema}
 */
export function number(rules = {}) {
  return (value, path = '') => {
    if (typeof value !== 'number' || !Number.isFinite(value)) {
      return problem(path, 'must be a number');
    }
    if (rules.integer === true && !Number.isInteger(value)) {
      return problem(path, 'must be a whole number');
    }
    if (rules.min !== undefined && value < rules.min) {
      return problem(path, `must be at least ${rules.min}`);
    }
    if (rules.max !== undefined && value > rules.max) {
      return problem(path, `must be at most ${rules.max}`);
    }
    return [];
  };
}

/**
 * @returns {Schema}
 */
export function boolean() {
  return (value, path = '') =>
    typeof value === 'boolean' ? [] : problem(path, 'must be true or false');
}

/**
 * @param {readonly unknown[]} allowed
 * @returns {Schema}
 */
export function oneOf(allowed) {
  if (!Array.isArray(allowed) || allowed.length === 0) {
    throw new ValidationError('a choice needs something to choose from');
  }
  return (value, path = '') =>
    allowed.includes(value) ? [] : problem(path, `must be one of ${allowed.join(', ')}`);
}

/**
 * @param {Schema} element
 * @param {{ min?: number, max?: number }} [rules]
 * @returns {Schema}
 */
export function arrayOf(element, rules = {}) {
  return (value, path = '') => {
    if (!Array.isArray(value)) {
      return problem(path, 'must be a list');
    }
    if (rules.min !== undefined && value.length < rules.min) {
      return problem(path, `must have at least ${rules.min} entries`);
    }
    if (rules.max !== undefined && value.length > rules.max) {
      return problem(path, `must have at most ${rules.max} entries`);
    }
    const problems = [];
    value.forEach((entry, index) => {
      problems.push(...element(entry, `${path}[${index}]`));
    });
    return problems;
  };
}

/**
 * An object with named fields. Fields not in the schema are ignored, because a
 * client sending a field this version does not know about should not be
 * refused for it.
 *
 * @param {Record<string, Schema>} fields
 * @returns {Schema}
 */
export function object(fields) {
  if (fields === null || typeof fields !== 'object') {
    throw new ValidationError('an object schema needs its fields');
  }
  return (value, path = '') => {
    if (value === null || typeof value !== 'object' || Array.isArray(value)) {
      return problem(path, 'must be an object');
    }
    const problems = [];
    for (const [name, schema] of Object.entries(fields)) {
      const where = path === '' ? name : `${path}.${name}`;
      problems.push(...schema(value[name], where));
    }
    return problems;
  };
}

/**
 * A field that may be left out.
 *
 * @param {Schema} schema
 * @returns {Schema}
 */
export function optional(schema) {
  return (value, path = '') => (value === undefined ? [] : schema(value, path));
}

/**
 * A field that may be left out, with a stated default when it is.
 *
 * @param {Schema} schema
 * @param {unknown} fallback
 * @returns {Schema & { fallback: unknown }}
 */
export function withDefault(schema, fallback) {
  const checked = (value, path = '') => (value === undefined ? [] : schema(value, path));
  checked.fallback = fallback;
  return checked;
}

/**
 * Check a value and throw when anything is wrong.
 *
 * @param {Schema} schema
 * @param {unknown} value
 * @param {string} [what] what the value is, for the message
 * @returns {unknown} the value, so that this can be used inline
 */
export function check(schema, value, what = 'the request') {
  const problems = schema(value, '');
  if (problems.length === 0) {
    return value;
  }
  const first = problems[0];
  throw new ValidationError(`${what} is wrong: ${first.path} ${first.message}`, {
    issues: problems,
  });
}

/**
 * Check a value and report rather than throwing.
 *
 * @param {Schema} schema
 * @param {unknown} value
 * @returns {{ ok: boolean, problems: Problem[] }}
 */
export function validate(schema, value) {
  const problems = schema(value, '');
  return { ok: problems.length === 0, problems };
}
