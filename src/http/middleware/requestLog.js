import { defaultIds } from '../../util/id.js';
import { systemClock } from '../../util/clock.js';
import { silentLogger } from '../../util/logger.js';

/**
 * Giving every request an identifier and writing a line when it finishes.
 *
 * The identifier goes on the request, into every log line the handler writes,
 * and back to the caller in a header. That last part is what makes a support
 * conversation short: the caller quotes the identifier and the log line is
 * found at once, without anybody guessing at timestamps.
 *
 * A caller may supply its own identifier, which is what lets one request
 * through several services be followed end to end. It is accepted only when it
 * looks like an identifier, so that a header from somewhere untrusted cannot
 * fill the log with whatever it likes.
 */

/** The header a request identifier travels in. */
export const REQUEST_ID_HEADER = 'x-request-id';

/** The most of a supplied identifier that is kept. */
const MAX_SUPPLIED_LENGTH = 64;

/**
 * Wrap a handler so that every request is identified, timed and logged.
 *
 * @param {(request: import('../request.js').Request) => Promise<import('../response.js').Response>} handler
 * @param {{
 *   logger?: import('../../util/logger.js').Logger,
 *   ids?: import('../../util/id.js').IdSource,
 *   clock?: { now: () => number }
 * }} [options]
 * @returns {(request: import('../request.js').Request) => Promise<import('../response.js').Response>}
 */
export function withRequestLog(handler, options = {}) {
  const logger = options.logger ?? silentLogger();
  const ids = options.ids ?? defaultIds();
  const clock = options.clock ?? systemClock();
  return async (request) => {
    const id = identifierFor(request, ids);
    const identified = request.withId(id);
    const started = clock.now();
    const response = await handler(identified);
    const elapsed = clock.now() - started;
    logger.info('request', {
      requestId: id,
      method: identified.method,
      path: identified.path,
      status: response.status,
      ms: elapsed,
    });
    return response.withHeader(REQUEST_ID_HEADER, id);
  };
}

/**
 * The identifier a request should carry: the one it brought when that is
 * usable, and a fresh one otherwise.
 *
 * @param {import('../request.js').Request} request
 * @param {import('../../util/id.js').IdSource} ids
 * @returns {string}
 */
export function identifierFor(request, ids) {
  const supplied = request.header(REQUEST_ID_HEADER);
  if (typeof supplied === 'string' && isUsable(supplied)) {
    return supplied;
  }
  return ids.next('req');
}

/**
 * True when a supplied identifier is safe to put in a log line: short enough,
 * and made only of the characters an identifier is made of.
 *
 * @param {string} supplied
 * @returns {boolean}
 */
export function isUsable(supplied) {
  if (supplied.length === 0 || supplied.length > MAX_SUPPLIED_LENGTH) {
    return false;
  }
  return /^[A-Za-z0-9_-]+$/.test(supplied);
}
