import { createServer } from 'node:http';
import { Router } from './router.js';
import { Request, readBody, parseJsonBody } from './request.js';
import { Response } from './response.js';
import { catchingErrors, notFound, methodNotAllowed } from './middleware/errorHandler.js';
import { withRequestLog } from './middleware/requestLog.js';
import { withRateLimit } from './middleware/rateLimit.js';
import { silentLogger } from '../util/logger.js';
import { ValidationError } from '../errors.js';

/**
 * The HTTP surface.
 *
 * Two things happen here and they are kept apart on purpose. `handle` takes a
 * request object and gives a response object, and knows nothing about sockets;
 * `listen` wires that to the runtime. Every test in this package exercises the
 * first, which is why there is not a port open anywhere in the suite.
 *
 * The middleware is applied in one place and in a stated order, so that a
 * reader can see that the rate limit runs before the handler and the request
 * log wraps everything including the error handling.
 *
 * A handler that returns something other than a response throws a plain error
 * rather than a validation failure, because that is a bug in this service and
 * not something the caller sent, and the two must not answer with the same
 * status.
 */
export class Server {
  /**
   * @param {{
   *   router: Router,
   *   logger?: import('../util/logger.js').Logger,
   *   rateLimit?: number,
   *   context?: object
   * }} options
   */
  constructor(options) {
    if (options === null || typeof options !== 'object') {
      throw new ValidationError('a server needs its options');
    }
    if (!(options.router instanceof Router)) {
      throw new ValidationError('a server needs a Router');
    }
    this.router = options.router;
    this.logger = options.logger ?? silentLogger();
    this.context = options.context ?? {};
    this.handle = withRequestLog(
      withRateLimit(
        catchingErrors((request) => this.route(request), { logger: this.logger }),
        {
          limit: options.rateLimit,
        },
      ),
      { logger: this.logger },
    );
  }

  /**
   * Find the handler for a request and run it.
   *
   * @param {Request} request
   * @returns {Promise<Response>}
   */
  async route(request) {
    const found = this.router.match(request.method, request.path);
    if (found === null) {
      const allowed = this.router.methodsFor(request.path);
      if (allowed.length > 0) {
        return methodNotAllowed(request.method, allowed);
      }
      return notFound(request.path);
    }
    const answer = await found.handler(request.withParams(found.params), this.context);
    if (!(answer instanceof Response)) {
      throw new Error(`the handler for ${found.pattern} did not return a response`);
    }
    return answer;
  }

  /**
   * Start listening.
   *
   * @param {{ port: number, host?: string }} where
   * @returns {Promise<{ port: number, close: () => Promise<void> }>}
   */
  listen(where) {
    const server = createServer((incoming, outgoing) => {
      this.serve(incoming, outgoing);
    });
    return new Promise((resolve, reject) => {
      server.on('error', reject);
      server.listen(where.port, where.host ?? '127.0.0.1', () => {
        const address = server.address();
        this.logger.info('listening', { port: address.port, host: address.address });
        resolve({
          port: address.port,
          close: () =>
            new Promise((done, failed) => {
              server.close((error) => (error ? failed(error) : done()));
            }),
        });
      });
    });
  }

  /**
   * Answer one request from the runtime.
   *
   * @param {import('node:http').IncomingMessage} incoming
   * @param {import('node:http').ServerResponse} outgoing
   */
  async serve(incoming, outgoing) {
    let response;
    try {
      const request = await requestFrom(incoming);
      response = await this.handle(request);
    } catch (error) {
      this.logger.error('a request could not be read', error);
      response = new Response({
        status: 400,
        headers: { 'content-type': 'application/json; charset=utf-8' },
        body: { error: { code: 'bad_request', message: 'the request could not be read' } },
      });
    }
    const rendered = response.render();
    outgoing.writeHead(rendered.status, rendered.headers);
    outgoing.end(rendered.text);
  }
}

/**
 * Build a request from what the runtime handed over.
 *
 * @param {import('node:http').IncomingMessage} incoming
 * @returns {Promise<Request>}
 */
export async function requestFrom(incoming) {
  const text = await readBody(incoming);
  const headers = incoming.headers ?? {};
  const type = String(headers['content-type'] ?? '');
  const body = type.startsWith('application/json')
    ? parseJsonBody(text)
    : text === ''
      ? undefined
      : text;
  return new Request({
    method: incoming.method ?? 'GET',
    url: incoming.url ?? '/',
    headers,
    body,
  });
}
