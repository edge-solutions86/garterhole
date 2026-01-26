import { problem } from '../response.js';
import {
  ValidationError,
  NotationError,
  NotFoundError,
  ConflictError,
  ConfigError,
  StorageError,
  isGarterholeError,
} from '../../errors.js';

/**
 * Turning a thrown error into an answer.
 *
 * The mapping is by type rather than by message, which is the whole reason the
 * error taxonomy exists. A handler that throws `NotFoundError` gets a 404
 * without having to know it is being served over HTTP, and a new handler that
 * throws one of the existing types is handled correctly the day it is written.
 *
 * Anything not in the taxonomy is a bug rather than a rejected request, so it
 * becomes a 500 and the message is not sent back. The caller cannot do anything
 * with the details of a bug, and the details are often the sort of thing that
 * should not leave the machine.
 */

/** Which status each kind of error becomes. */
const STATUSES = [
  [NotFoundError, 404],
  [ConflictError, 409],
  [NotationError, 400],
  [ValidationError, 400],
  [ConfigError, 500],
  [StorageError, 500],
];

/**
 * The status an error should be answered with.
 *
 * @param {unknown} error
 * @returns {number}
 */
export function statusFor(error) {
  for (const [kind, status] of STATUSES) {
    if (error instanceof kind) {
      return status;
    }
  }
  if (error instanceof RangeError || error instanceof TypeError) {
    return 400;
  }
  return 500;
}

/**
 * Turn an error into a response.
 *
 * @param {unknown} error
 * @param {{ logger?: import('../../util/logger.js').Logger, requestId?: string }} [context]
 * @returns {import('../response.js').Response}
 */
export function responseForError(error, context = {}) {
  const status = statusFor(error);
  if (status >= 500) {
    context.logger?.error('a request failed', error, { requestId: context.requestId });
    return problem(status, 'internal_error', 'something went wrong inside the service');
  }
  const code = isGarterholeError(error) ? error.code : 'bad_request';
  const message = error instanceof Error ? error.message : String(error);
  const issues =
    error instanceof ValidationError && error.issues.length > 0 ? [...error.issues] : undefined;
  context.logger?.debug('a request was refused', { requestId: context.requestId, code, message });
  return problem(status, code, message, { issues });
}

/**
 * Wrap a handler so that anything it throws becomes a response.
 *
 * @param {(request: unknown) => Promise<unknown> | unknown} handler
 * @param {{ logger?: import('../../util/logger.js').Logger }} [context]
 * @returns {(request: { id?: string }) => Promise<unknown>}
 */
export function catchingErrors(handler, context = {}) {
  return async (request) => {
    try {
      return await handler(request);
    } catch (error) {
      return responseForError(error, { ...context, requestId: request?.id ?? undefined });
    }
  };
}

/**
 * The answer for a path nobody serves.
 *
 * @param {string} path
 * @returns {import('../response.js').Response}
 */
export function notFound(path) {
  return problem(404, 'not_found', `nothing is served at ${path}`);
}

/**
 * The answer for a path that is served, but not with this method.
 *
 * @param {string} method
 * @param {readonly string[]} allowed
 * @returns {import('../response.js').Response}
 */
export function methodNotAllowed(method, allowed) {
  return problem(405, 'method_not_allowed', `${method} is not allowed here`).withHeader(
    'allow',
    allowed.join(', '),
  );
}
