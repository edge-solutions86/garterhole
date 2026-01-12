import { describe, it, expect } from 'vitest';
import { Request, readBody, parseJsonBody, MAX_BODY_BYTES } from '../../src/http/request.js';
import { ValidationError } from '../../src/errors.js';

/**
 * A stream that hands over the chunks it was given.
 *
 * @param {string[]} chunks
 * @param {Error} [failure]
 */
function stream(chunks, failure) {
  const listeners = {};
  const fake = {
    on(event, listener) {
      listeners[event] = listener;
      return fake;
    },
  };
  queueMicrotask(() => {
    if (failure !== undefined) {
      listeners.error?.(failure);
      return;
    }
    for (const chunk of chunks) {
      listeners.data?.(Buffer.from(chunk, 'utf8'));
    }
    listeners.end?.();
  });
  return fake;
}

describe('Request', () => {
  it('reads the path and the query apart', () => {
    const request = new Request({ method: 'get', url: '/towers?stage=8&cover=yes' });
    expect(request.method).toBe('GET');
    expect(request.path).toBe('/towers');
    expect(request.query).toEqual({ stage: '8', cover: 'yes' });
  });

  it('lower cases the header names', () => {
    const request = new Request({ url: '/', headers: { 'Content-Type': 'application/json' } });
    expect(request.header('content-type')).toBe('application/json');
    expect(request.header('CONTENT-TYPE')).toBe('application/json');
  });

  it('joins a header sent more than once', () => {
    const request = new Request({ url: '/', headers: { accept: ['a', 'b'] } });
    expect(request.header('accept')).toBe('a, b');
  });

  it('gives undefined for a header that was not sent', () => {
    expect(new Request({ url: '/' }).header('x-nothing')).toBeUndefined();
  });

  it('refuses parts it cannot use', () => {
    expect(() => new Request(null)).toThrow(/a request needs its parts/);
  });

  it('defaults the method and the path', () => {
    const request = new Request({});
    expect(request.method).toBe('GET');
    expect(request.path).toBe('/');
  });
});

describe('parameters', () => {
  const request = () => new Request({ url: '/towers/st-mary', params: { id: 'st-mary' } });

  it('reads a captured parameter', () => {
    expect(request().param('id')).toBe('st-mary');
  });

  it('complains about one the route does not capture', () => {
    expect(() => request().param('name')).toThrow(/no parameter called "name"/);
  });

  it('reads a query value with a fallback', () => {
    const withQuery = new Request({ url: '/towers?stage=8' });
    expect(withQuery.queryValue('stage')).toBe('8');
    expect(withQuery.queryValue('cover', 'no')).toBe('no');
  });

  it('treats an empty query value as missing', () => {
    expect(new Request({ url: '/towers?stage=' }).queryValue('stage', '6')).toBe('6');
  });
});

describe('rebuilding a request', () => {
  const original = () =>
    new Request({
      method: 'POST',
      url: '/towers?stage=8',
      headers: { 'content-type': 'application/json' },
      body: { name: 'St Mary' },
    });

  it('keeps everything when parameters are added', () => {
    const withParams = original().withParams({ id: 'st-mary' });
    expect(withParams.param('id')).toBe('st-mary');
    expect(withParams.body).toEqual({ name: 'St Mary' });
    expect(withParams.query).toEqual({ stage: '8' });
    expect(withParams.method).toBe('POST');
  });

  it('keeps everything when an identifier is added', () => {
    const withId = original().withId('req_1');
    expect(withId.id).toBe('req_1');
    expect(withId.header('content-type')).toBe('application/json');
  });
});

describe('sendsJson', () => {
  it('is true for a JSON content type', () => {
    expect(
      new Request({ url: '/', headers: { 'content-type': 'application/json' } }).sendsJson,
    ).toBe(true);
  });

  it('ignores the parameters after the type', () => {
    const request = new Request({
      url: '/',
      headers: { 'content-type': 'application/json; charset=utf-8' },
    });
    expect(request.sendsJson).toBe(true);
  });

  it('is false for anything else or nothing', () => {
    expect(new Request({ url: '/', headers: { 'content-type': 'text/plain' } }).sendsJson).toBe(
      false,
    );
    expect(new Request({ url: '/' }).sendsJson).toBe(false);
  });
});

describe('readBody', () => {
  it('joins the chunks', async () => {
    await expect(readBody(stream(['{"a":', '1}']))).resolves.toBe('{"a":1}');
  });

  it('gives an empty string for no body', async () => {
    await expect(readBody(stream([]))).resolves.toBe('');
  });

  it('refuses a body over the limit', async () => {
    await expect(readBody(stream(['abcdef']), { limit: 3 })).rejects.toThrow(/longer than 3 bytes/);
  });

  it('passes a stream failure on', async () => {
    await expect(readBody(stream([], new Error('gone')))).rejects.toThrow('gone');
  });

  it('has a default limit', () => {
    expect(MAX_BODY_BYTES).toBeGreaterThan(1000);
  });
});

describe('parseJsonBody', () => {
  it('parses a body', () => {
    expect(parseJsonBody('{"a":1}')).toEqual({ a: 1 });
  });

  it('gives undefined for an empty body', () => {
    expect(parseJsonBody('')).toBeUndefined();
  });

  it('complains about a body that is not JSON', () => {
    expect(() => parseJsonBody('{')).toThrow(ValidationError);
    expect(() => parseJsonBody('{')).toThrow(/is not JSON/);
  });
});
