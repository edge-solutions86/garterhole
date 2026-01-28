import { describe, it, expect } from 'vitest';
import {
  withRequestLog,
  identifierFor,
  isUsable,
  REQUEST_ID_HEADER,
} from '../../src/http/middleware/requestLog.js';
import { Request } from '../../src/http/request.js';
import { json } from '../../src/http/response.js';
import { collectingLogger } from '../../src/util/logger.js';
import { IdSource } from '../../src/util/id.js';
import { FixedClock } from '../../src/util/clock.js';

const ids = () => new IdSource({ now: () => 1_700_000_000_000, random: () => 0 });

describe('isUsable', () => {
  it('accepts an identifier made of safe characters', () => {
    expect(isUsable('req_abc123')).toBe(true);
    expect(isUsable('trace-1')).toBe(true);
  });

  it('rejects an empty one or one that is far too long', () => {
    expect(isUsable('')).toBe(false);
    expect(isUsable('a'.repeat(100))).toBe(false);
  });

  it('rejects anything that would make a mess of a log line', () => {
    expect(isUsable('req 1')).toBe(false);
    expect(isUsable('req\n1')).toBe(false);
    expect(isUsable('{"a":1}')).toBe(false);
  });
});

describe('identifierFor', () => {
  it('keeps an identifier the caller sent', () => {
    const request = new Request({ url: '/', headers: { [REQUEST_ID_HEADER]: 'trace-9' } });
    expect(identifierFor(request, ids())).toBe('trace-9');
  });

  it('makes one when none was sent', () => {
    expect(identifierFor(new Request({ url: '/' }), ids()).startsWith('req_')).toBe(true);
  });

  it('makes one when what was sent is unusable', () => {
    const request = new Request({ url: '/', headers: { [REQUEST_ID_HEADER]: 'not a trace' } });
    expect(identifierFor(request, ids()).startsWith('req_')).toBe(true);
  });
});

describe('withRequestLog', () => {
  const handler = async (request) => json({ path: request.path, id: request.id });

  it('gives the handler a request with an identifier', async () => {
    const wrapped = withRequestLog(handler, { ids: ids() });
    const response = await wrapped(new Request({ url: '/health' }));
    expect(response.body.id.startsWith('req_')).toBe(true);
  });

  it('sends the identifier back in a header', async () => {
    const wrapped = withRequestLog(handler, { ids: ids() });
    const response = await wrapped(new Request({ url: '/health' }));
    expect(response.headers[REQUEST_ID_HEADER]).toBe(response.body.id);
  });

  it('writes one line per request', async () => {
    const { logger, records } = collectingLogger();
    const wrapped = withRequestLog(handler, { logger, ids: ids() });
    await wrapped(new Request({ method: 'POST', url: '/towers?stage=8' }));
    expect(records()).toHaveLength(1);
    expect(records()[0]).toMatchObject({
      message: 'request',
      method: 'POST',
      path: '/towers',
      status: 200,
    });
  });

  it('times the request with the clock it was given', async () => {
    const { logger, records } = collectingLogger();
    const clock = new FixedClock(0);
    const slow = async (request) => {
      clock.advance(37);
      return json({ path: request.path });
    };
    await withRequestLog(slow, { logger, ids: ids(), clock })(new Request({ url: '/' }));
    expect(records()[0].ms).toBe(37);
  });

  it('logs the status the handler answered with', async () => {
    const { logger, records } = collectingLogger();
    const failing = async () => json({ error: 'no' }, { status: 404 });
    await withRequestLog(failing, { logger, ids: ids() })(new Request({ url: '/nothing' }));
    expect(records()[0].status).toBe(404);
  });

  it('writes nothing when no logger was given', async () => {
    const wrapped = withRequestLog(handler, { ids: ids() });
    await expect(wrapped(new Request({ url: '/' }))).resolves.toBeDefined();
  });
});
