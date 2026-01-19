import { buildLabel } from '../version.js';
import { ValidationError } from '../errors.js';

/**
 * What goes back.
 *
 * A handler returns one of these rather than writing to a socket, which is what
 * lets the routes be tested without a server running and what lets the
 * middleware add headers after the handler has finished.
 *
 * A response is immutable. Adding a header gives a new one, so a middleware
 * cannot change a response another middleware is holding.
 */
export class Response {
  /**
   * @param {{ status?: number, headers?: Record<string, string>, body?: unknown }} [parts]
   */
  constructor(parts = {}) {
    const status = parts.status ?? 200;
    if (!Number.isInteger(status) || status < 100 || status > 599) {
      throw new ValidationError(`${status} is not a status code`);
    }
    this.status = status;
    this.headers = Object.freeze({ ...(parts.headers ?? {}) });
    this.body = parts.body;
    Object.freeze(this);
  }

  /**
   * The same response with a header added.
   *
   * @param {string} name
   * @param {string} value
   * @returns {Response}
   */
  withHeader(name, value) {
    return new Response({
      status: this.status,
      headers: { ...this.headers, [name]: String(value) },
      body: this.body,
    });
  }

  /**
   * The same response with a different status.
   *
   * @param {number} status
   * @returns {Response}
   */
  withStatus(status) {
    return new Response({ status, headers: this.headers, body: this.body });
  }

  /**
   * The body as bytes, and the headers that describe it.
   *
   * @returns {{ status: number, headers: Record<string, string>, text: string }}
   */
  render() {
    const text = this.body === undefined ? '' : renderBody(this.body, this.headers);
    return {
      status: this.status,
      headers: {
        'content-length': String(Buffer.byteLength(text, 'utf8')),
        'x-served-by': buildLabel(),
        ...this.headers,
      },
      text,
    };
  }
}

/**
 * A JSON response.
 *
 * @param {unknown} body
 * @param {{ status?: number, headers?: Record<string, string> }} [options]
 * @returns {Response}
 */
export function json(body, options = {}) {
  return new Response({
    status: options.status ?? 200,
    headers: { 'content-type': 'application/json; charset=utf-8', ...(options.headers ?? {}) },
    body,
  });
}

/**
 * A plain text response.
 *
 * @param {string} body
 * @param {{ status?: number, headers?: Record<string, string> }} [options]
 * @returns {Response}
 */
export function text(body, options = {}) {
  return new Response({
    status: options.status ?? 200,
    headers: { 'content-type': 'text/plain; charset=utf-8', ...(options.headers ?? {}) },
    body: String(body),
  });
}

/**
 * A response with nothing in it, for a delete that worked.
 *
 * @returns {Response}
 */
export function noContent() {
  return new Response({ status: 204 });
}

/**
 * A response saying where something was made.
 *
 * @param {string} location
 * @param {unknown} body
 * @returns {Response}
 */
export function created(location, body) {
  return json(body, { status: 201, headers: { location } });
}

/**
 * An error response in the shape every error takes, so that a client can read
 * one without knowing which handler produced it.
 *
 * @param {number} status
 * @param {string} code
 * @param {string} message
 * @param {{ issues?: unknown[] }} [details]
 * @returns {Response}
 */
export function problem(status, code, message, details = {}) {
  return json(
    {
      error: { code, message, ...(details.issues === undefined ? {} : { issues: details.issues }) },
    },
    { status },
  );
}

/**
 * @param {unknown} body
 * @param {Record<string, string>} headers
 * @returns {string}
 */
function renderBody(body, headers) {
  const type = headers['content-type'] ?? '';
  if (typeof body === 'string') {
    return body;
  }
  if (type.startsWith('application/json')) {
    return JSON.stringify(body);
  }
  return String(body);
}
