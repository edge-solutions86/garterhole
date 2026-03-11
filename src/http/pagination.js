import { toInteger } from '../validation/coerce.js';
import { ValidationError } from '../errors.js';

/**
 * Handing back a long list a page at a time.
 *
 * Every listing route grew the same three lines: read a limit, read an offset,
 * slice. They had drifted apart by the time there were three of them, so one
 * of them capped the limit and the others did not, which is the sort of
 * difference nobody notices until a client asks for a million records.
 *
 * The page is described in the answer rather than in a header, because a
 * secretary reading the JSON in a browser can see it there.
 */

/** The page size used when the caller does not ask for one. */
export const DEFAULT_LIMIT = 50;

/** The largest page anybody may ask for. */
export const MAX_LIMIT = 500;

/**
 * Read the page a request is asking for.
 *
 * @param {import('./request.js').Request} request
 * @param {{ defaultLimit?: number, maxLimit?: number }} [options]
 * @returns {{ limit: number, offset: number }}
 */
export function pageOf(request, options = {}) {
  const defaultLimit = options.defaultLimit ?? DEFAULT_LIMIT;
  const maxLimit = options.maxLimit ?? MAX_LIMIT;
  const limitText = request.queryValue('limit');
  const offsetText = request.queryValue('offset');
  const limit = limitText === undefined ? defaultLimit : toInteger(limitText, 'limit');
  const offset = offsetText === undefined ? 0 : toInteger(offsetText, 'offset');
  if (limit < 1) {
    throw new ValidationError(`a limit of ${limit} would return nothing`);
  }
  if (limit > maxLimit) {
    throw new ValidationError(`a limit of ${limit} is more than the ${maxLimit} allowed`);
  }
  if (offset < 0) {
    throw new ValidationError(`an offset of ${offset} is before the start of the list`);
  }
  return { limit, offset };
}

/**
 * Cut a page out of a list and describe it.
 *
 * @template T
 * @param {readonly T[]} everything
 * @param {{ limit: number, offset: number }} page
 * @param {string} name what the list is called in the answer
 * @returns {Record<string, unknown>}
 */
export function pageOut(everything, page, name) {
  const items = everything.slice(page.offset, page.offset + page.limit);
  return {
    [name]: items,
    total: everything.length,
    limit: page.limit,
    offset: page.offset,
    returned: items.length,
    more: page.offset + items.length < everything.length,
  };
}

/**
 * Read the page and cut it out in one step, which is what a listing route
 * actually wants.
 *
 * @template T
 * @param {import('./request.js').Request} request
 * @param {readonly T[]} everything
 * @param {string} name
 * @param {{ defaultLimit?: number, maxLimit?: number }} [options]
 * @returns {Record<string, unknown>}
 */
export function paginate(request, everything, name, options = {}) {
  return pageOut(everything, pageOf(request, options), name);
}
