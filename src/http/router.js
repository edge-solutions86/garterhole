import { assertNonEmptyString, assertFunction } from '../util/assert.js';
import { ValidationError } from '../errors.js';

/**
 * Matching a request to the code that answers it.
 *
 * Patterns look like `/towers/:id/performances`, where a segment starting with
 * a colon captures whatever is there. There are no regular expressions, no
 * optional segments and no wildcards in the middle, because the routes this
 * service has do not need them and every one of those features is a way for two
 * routes to overlap without anybody noticing.
 *
 * Routes are matched in the order they were added, and a route that would never
 * be reached because an earlier one always matches first is refused rather than
 * quietly ignored.
 */
export class Router {
  constructor() {
    /** @type {{ method: string, pattern: string, segments: string[], handler: Function }[]} */
    this.routes = [];
  }

  /**
   * Add a route.
   *
   * @param {string} method
   * @param {string} pattern
   * @param {Function} handler
   * @returns {Router} this
   */
  add(method, pattern, handler) {
    assertNonEmptyString(method, 'a request method');
    assertNonEmptyString(pattern, 'a route pattern');
    assertFunction(handler, 'a route handler');
    if (!pattern.startsWith('/')) {
      throw new ValidationError(`the route ${JSON.stringify(pattern)} must start with a slash`);
    }
    const segments = splitPath(pattern);
    const upper = method.toUpperCase();
    for (const existing of this.routes) {
      if (existing.method === upper && sameShape(existing.segments, segments)) {
        throw new ValidationError(
          `the route ${upper} ${pattern} would never be reached, because ${existing.pattern} matches first`,
          { details: { pattern, existing: existing.pattern } },
        );
      }
    }
    this.routes.push({ method: upper, pattern, segments, handler });
    return this;
  }

  /**
   * @param {string} pattern
   * @param {Function} handler
   * @returns {Router}
   */
  get(pattern, handler) {
    return this.add('GET', pattern, handler);
  }

  /**
   * @param {string} pattern
   * @param {Function} handler
   * @returns {Router}
   */
  post(pattern, handler) {
    return this.add('POST', pattern, handler);
  }

  /**
   * @param {string} pattern
   * @param {Function} handler
   * @returns {Router}
   */
  delete(pattern, handler) {
    return this.add('DELETE', pattern, handler);
  }

  /**
   * Find the route for a request.
   *
   * @param {string} method
   * @param {string} path
   * @returns {{ handler: Function, params: Record<string, string>, pattern: string } | null}
   */
  match(method, path) {
    const wanted = splitPath(path);
    const upper = String(method).toUpperCase();
    for (const route of this.routes) {
      if (route.method !== upper) {
        continue;
      }
      const params = matchSegments(route.segments, wanted);
      if (params !== null) {
        return { handler: route.handler, params, pattern: route.pattern };
      }
    }
    return null;
  }

  /**
   * The methods a path would answer to, which is what a 405 has to list.
   *
   * @param {string} path
   * @returns {string[]}
   */
  methodsFor(path) {
    const wanted = splitPath(path);
    const methods = new Set();
    for (const route of this.routes) {
      if (matchSegments(route.segments, wanted) !== null) {
        methods.add(route.method);
      }
    }
    return [...methods].sort();
  }

  /**
   * Every route, for the listing the service publishes about itself.
   *
   * @returns {{ method: string, pattern: string }[]}
   */
  list() {
    return this.routes.map((route) => ({ method: route.method, pattern: route.pattern }));
  }
}

/**
 * @param {string} path
 * @returns {string[]}
 */
function splitPath(path) {
  return String(path)
    .split('/')
    .filter((segment) => segment.length > 0);
}

/**
 * @param {readonly string[]} pattern
 * @param {readonly string[]} path
 * @returns {Record<string, string> | null}
 */
function matchSegments(pattern, path) {
  if (pattern.length !== path.length) {
    return null;
  }
  const params = {};
  for (let index = 0; index < pattern.length; index += 1) {
    const segment = pattern[index];
    if (segment.startsWith(':')) {
      params[segment.slice(1)] = decodeURIComponent(path[index]);
      continue;
    }
    if (segment !== path[index]) {
      return null;
    }
  }
  return params;
}

/**
 * Two patterns have the same shape when every segment either matches literally
 * or is a parameter in both, which means one can never be reached past the
 * other.
 *
 * @param {readonly string[]} left
 * @param {readonly string[]} right
 * @returns {boolean}
 */
function sameShape(left, right) {
  if (left.length !== right.length) {
    return false;
  }
  return left.every((segment, index) => {
    const other = right[index];
    if (segment.startsWith(':')) {
      return true;
    }
    return other.startsWith(':') ? false : segment === other;
  });
}
