import { describe, it, expect } from 'vitest';
import { Server, requestFrom } from '../../src/http/server.js';
import { Router } from '../../src/http/router.js';
import { Request } from '../../src/http/request.js';
import { json, text } from '../../src/http/response.js';
import { collectingLogger } from '../../src/util/logger.js';
import { NotFoundError, ValidationError } from '../../src/errors.js';

const router = () =>
  new Router()
    .get('/health', async () => json({ ok: true }))
    .get('/towers/:id', async (request) => json({ id: request.param('id') }))
    .get('/context', async (_request, context) => json({ name: context.name }))
    .get('/broken', async () => {
      throw new NotFoundError('tower', 'x');
    })
    .get('/bug', async () => {
      throw new Error('the password is hunter2');
    })
    .get('/wrong', async () => 'not a response')
    .delete('/towers/:id', async () => json({ removed: true }));

const server = (options = {}) => new Server({ router: router(), ...options });

/**
 * A fake incoming message, so the reading path can be tested without a socket.
 *
 * @param {{ method?: string, url?: string, headers?: object, body?: string }} parts
 */
function incoming(parts = {}) {
  const listeners = {};
  const stream = {
    method: parts.method ?? 'GET',
    url: parts.url ?? '/',
    headers: parts.headers ?? {},
    on(event, listener) {
      listeners[event] = listener;
      return stream;
    },
  };
  queueMicrotask(() => {
    if (parts.body !== undefined) {
      listeners.data?.(Buffer.from(parts.body, 'utf8'));
    }
    listeners.end?.();
  });
  return stream;
}

describe('routing a request', () => {
  it('answers a route it has', async () => {
    const response = await server().handle(new Request({ url: '/health' }));
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ ok: true });
  });

  it('passes the captured parameters through', async () => {
    const response = await server().handle(new Request({ url: '/towers/st-mary' }));
    expect(response.body).toEqual({ id: 'st-mary' });
  });

  it('passes the context to the handler', async () => {
    const response = await server({ context: { name: 'St Mary' } }).handle(
      new Request({ url: '/context' }),
    );
    expect(response.body).toEqual({ name: 'St Mary' });
  });

  it('answers 404 for a path nobody serves', async () => {
    const response = await server().handle(new Request({ url: '/nowhere' }));
    expect(response.status).toBe(404);
    expect(response.body.error.message).toBe('nothing is served at /nowhere');
  });

  it('answers 405 with the methods that would work', async () => {
    const response = await server().handle(new Request({ method: 'POST', url: '/towers/st-mary' }));
    expect(response.status).toBe(405);
    expect(response.headers.allow).toBe('DELETE, GET');
  });

  it('turns an error from a handler into an answer', async () => {
    const response = await server().handle(new Request({ url: '/broken' }));
    expect(response.status).toBe(404);
    expect(response.body.error.code).toBe('not_found');
  });

  it('hides the details of a bug', async () => {
    const response = await server().handle(new Request({ url: '/bug' }));
    expect(response.status).toBe(500);
    expect(JSON.stringify(response.body)).not.toContain('hunter2');
  });

  it('refuses a handler that does not return a response', async () => {
    const response = await server().handle(new Request({ url: '/wrong' }));
    expect(response.status).toBe(500);
  });

  it('refuses options it cannot use', () => {
    expect(() => new Server(null)).toThrow(/needs its options/);
    expect(() => new Server({ router: {} })).toThrow(/needs a Router/);
  });
});

describe('the middleware around every route', () => {
  it('identifies and logs the request', async () => {
    const { logger, records } = collectingLogger();
    await server({ logger }).handle(new Request({ url: '/health' }));
    expect(records()[0]).toMatchObject({ message: 'request', path: '/health', status: 200 });
  });

  it('sends the rate limit back', async () => {
    const response = await server().handle(new Request({ url: '/health' }));
    expect(response.headers['x-rate-limit-remaining']).toBeDefined();
  });

  it('refuses a caller who asks too often', async () => {
    const limited = server({ rateLimit: 1 });
    await limited.handle(new Request({ url: '/health' }));
    const response = await limited.handle(new Request({ url: '/health' }));
    expect(response.status).toBe(429);
  });
});

describe('requestFrom', () => {
  it('reads the method, the path and the query', async () => {
    const request = await requestFrom(incoming({ method: 'POST', url: '/towers?stage=8' }));
    expect(request.method).toBe('POST');
    expect(request.path).toBe('/towers');
    expect(request.query.stage).toBe('8');
  });

  it('parses a JSON body', async () => {
    const request = await requestFrom(
      incoming({ headers: { 'content-type': 'application/json' }, body: '{"name":"St Mary"}' }),
    );
    expect(request.body).toEqual({ name: 'St Mary' });
  });

  it('keeps a text body as text', async () => {
    const request = await requestFrom(
      incoming({ headers: { 'content-type': 'text/plain' }, body: 'hello' }),
    );
    expect(request.body).toBe('hello');
  });

  it('leaves the body out when there is none', async () => {
    expect((await requestFrom(incoming())).body).toBeUndefined();
  });

  it('complains about a JSON body that is not JSON', async () => {
    await expect(
      requestFrom(incoming({ headers: { 'content-type': 'application/json' }, body: '{' })),
    ).rejects.toThrow(ValidationError);
  });
});

describe('serving over a socket', () => {
  it('answers a real request', async () => {
    const listening = await server().listen({ port: 0 });
    try {
      const answer = await fetch(`http://127.0.0.1:${listening.port}/health`);
      expect(answer.status).toBe(200);
      expect(await answer.json()).toEqual({ ok: true });
    } finally {
      await listening.close();
    }
  });

  it('answers 404 over the socket as well', async () => {
    const listening = await server().listen({ port: 0 });
    try {
      const answer = await fetch(`http://127.0.0.1:${listening.port}/nowhere`);
      expect(answer.status).toBe(404);
      expect(answer.headers.get('content-type')).toContain('application/json');
    } finally {
      await listening.close();
    }
  });

  it('sends a text response back as text', async () => {
    const plain = new Server({ router: new Router().get('/plain', async () => text('hello')) });
    const listening = await plain.listen({ port: 0 });
    try {
      const answer = await fetch(`http://127.0.0.1:${listening.port}/plain`);
      expect(await answer.text()).toBe('hello');
    } finally {
      await listening.close();
    }
  });
});
