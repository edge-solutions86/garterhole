import { ValidationError } from '../errors.js';

/**
 * What arrived.
 *
 * The request object the runtime hands over is a stream with a few headers on
 * it. This wraps it in something the route handlers can read without knowing
 * that: the path, the query, the captured parameters and the body already
 * parsed.
 *
 * The body is read with a size limit. Without one, a single request can make
 * the service run out of memory, and a tower running this on a machine in the
 * vestry has no monitoring to notice.
 */

/** The most a request body may be, in bytes. */
export const MAX_BODY_BYTES = 1024 * 1024;

export class Request {
  /**
   * @param {{
   *   method: string,
   *   url: string,
   *   headers?: Record<string, string>,
   *   params?: Record<string, string>,
   *   body?: unknown,
   *   id?: string
   * }} parts
   */
  constructor(parts) {
    if (parts === null || typeof parts !== 'object') {
      throw new ValidationError('a request needs its parts');
    }
    const url = new URL(String(parts.url ?? '/'), 'http://localhost');
    this.method = String(parts.method ?? 'GET').toUpperCase();
    this.path = url.pathname;
    this.query = Object.fromEntries(url.searchParams.entries());
    this.headers = normaliseHeaders(parts.headers ?? {});
    this.params = { ...(parts.params ?? {}) };
    this.body = parts.body;
    this.id = parts.id ?? null;
    Object.freeze(this.query);
    Object.freeze(this.headers);
  }

  /**
   * A header, whatever case it was sent in.
   *
   * @param {string} name
   * @returns {string | undefined}
   */
  header(name) {
    return this.headers[String(name).toLowerCase()];
  }

  /**
   * A captured parameter, or a complaint naming the one that is missing.
   *
   * @param {string} name
   * @returns {string}
   */
  param(name) {
    const found = this.params[name];
    if (found === undefined) {
      throw new ValidationError(`this route has no parameter called ${JSON.stringify(name)}`);
    }
    return found;
  }

  /**
   * A query parameter, with a fallback when it was not sent.
   *
   * @param {string} name
   * @param {string} [fallback]
   * @returns {string | undefined}
   */
  queryValue(name, fallback) {
    const found = this.query[name];
    return found === undefined || found === '' ? fallback : found;
  }

  /**
   * True when the caller said it was sending JSON.
   *
   * @returns {boolean}
   */
  get sendsJson() {
    const type = this.header('content-type') ?? '';
    return type.split(';')[0].trim() === 'application/json';
  }

  /**
   * The request with parameters filled in by the router.
   *
   * @param {Record<string, string>} params
   * @returns {Request}
   */
  withParams(params) {
    return new Request({
      method: this.method,
      url: `${this.path}${searchOf(this.query)}`,
      headers: this.headers,
      params,
      body: this.body,
      id: this.id,
    });
  }

  /**
   * The request with an identifier attached.
   *
   * @param {string} id
   * @returns {Request}
   */
  withId(id) {
    return new Request({
      method: this.method,
      url: `${this.path}${searchOf(this.query)}`,
      headers: this.headers,
      params: this.params,
      body: this.body,
      id,
    });
  }
}

/**
 * Read a body off a stream, stopping if it is too big.
 *
 * @param {{ on: Function }} stream
 * @param {{ limit?: number }} [options]
 * @returns {Promise<string>}
 */
export function readBody(stream, options = {}) {
  const limit = options.limit ?? MAX_BODY_BYTES;
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    stream.on('data', (chunk) => {
      size += chunk.length;
      if (size > limit) {
        reject(new ValidationError(`the request body is longer than ${limit} bytes`));
        return;
      }
      chunks.push(chunk);
    });
    stream.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    stream.on('error', (error) => reject(error));
  });
}

/**
 * Parse a body that says it is JSON.
 *
 * @param {string} text
 * @returns {unknown}
 */
export function parseJsonBody(text) {
  if (text === '') {
    return undefined;
  }
  try {
    return JSON.parse(text);
  } catch (error) {
    throw new ValidationError(`the request body is not JSON: ${error.message}`);
  }
}

/**
 * @param {Record<string, unknown>} headers
 * @returns {Record<string, string>}
 */
function normaliseHeaders(headers) {
  const lowered = {};
  for (const [name, value] of Object.entries(headers)) {
    lowered[name.toLowerCase()] = Array.isArray(value) ? value.join(', ') : String(value);
  }
  return lowered;
}

/**
 * @param {Record<string, string>} query
 * @returns {string}
 */
function searchOf(query) {
  const entries = Object.entries(query);
  if (entries.length === 0) {
    return '';
  }
  return `?${new URLSearchParams(entries).toString()}`;
}
