import { isSupportedStage, MIN_STAGE, MAX_STAGE } from '../core/stage.js';
import { tryParseRow } from '../core/rowParse.js';
import { expandNotation } from '../notation/expand.js';
import { isWellFormedTitle } from '../method/title.js';
import { parseTouch } from '../composition/touchParser.js';
import { number, string } from './schema.js';

/**
 * The field types this library actually has, as schemas.
 *
 * Every one of these is a thin wrapper around a reader that already exists.
 * That matters: the HTTP layer must accept exactly what the domain accepts, and
 * the only way to be sure of that is to ask the domain rather than to write a
 * regular expression that agrees with it today.
 */

/**
 * A stage this library can work with.
 *
 * @returns {import('./schema.js').Schema}
 */
export function stage() {
  const bounds = number({ integer: true, min: MIN_STAGE, max: MAX_STAGE });
  return (value, path = '') => {
    const problems = bounds(value, path);
    if (problems.length > 0) {
      return problems;
    }
    return isSupportedStage(value)
      ? []
      : [{ path: path === '' ? '(root)' : path, message: 'is not a stage' }];
  };
}

/**
 * A row, written out.
 *
 * @param {{ stage?: number }} [rules]
 * @returns {import('./schema.js').Schema}
 */
export function row(rules = {}) {
  const text = string({ min: 1 });
  return (value, path = '') => {
    const problems = text(value, path);
    if (problems.length > 0) {
      return problems;
    }
    const parsed = tryParseRow(value, rules);
    return parsed.ok ? [] : [{ path: path === '' ? '(root)' : path, message: parsed.message }];
  };
}

/**
 * Place notation, at a stage.
 *
 * @param {{ stage: number }} rules
 * @returns {import('./schema.js').Schema}
 */
export function placeNotation(rules) {
  const text = string({ min: 1 });
  return (value, path = '') => {
    const problems = text(value, path);
    if (problems.length > 0) {
      return problems;
    }
    try {
      expandNotation(value, rules.stage);
      return [];
    } catch (error) {
      return [{ path: path === '' ? '(root)' : path, message: error.message }];
    }
  };
}

/**
 * A method title.
 *
 * @returns {import('./schema.js').Schema}
 */
export function methodTitle() {
  const text = string({ min: 1 });
  return (value, path = '') => {
    const problems = text(value, path);
    if (problems.length > 0) {
      return problems;
    }
    return isWellFormedTitle(value)
      ? []
      : [{ path: path === '' ? '(root)' : path, message: 'is not a method title' }];
  };
}

/**
 * A touch, written either way.
 *
 * @returns {import('./schema.js').Schema}
 */
export function touch() {
  const text = string({ min: 1 });
  return (value, path = '') => {
    const problems = text(value, path);
    if (problems.length > 0) {
      return problems;
    }
    try {
      parseTouch(value);
      return [];
    } catch (error) {
      return [{ path: path === '' ? '(root)' : path, message: error.message }];
    }
  };
}

/**
 * A date written the way a form sends it.
 *
 * @returns {import('./schema.js').Schema}
 */
export function isoDate() {
  const text = string({ pattern: /^\d{4}-\d{2}-\d{2}$/ });
  return (value, path = '') => {
    const problems = text(value, path);
    if (problems.length > 0) {
      return [{ path: path === '' ? '(root)' : path, message: 'must be a date like 2025-05-18' }];
    }
    const parsed = new Date(`${value}T00:00:00Z`);
    if (Number.isNaN(parsed.getTime()) || !parsed.toISOString().startsWith(value)) {
      return [{ path: path === '' ? '(root)' : path, message: 'is not a real date' }];
    }
    return [];
  };
}

/**
 * A number of changes.
 *
 * @returns {import('./schema.js').Schema}
 */
export function changeCount() {
  return number({ integer: true, min: 1, max: 100000 });
}
