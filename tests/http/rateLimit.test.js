import { describe, it, expect } from 'vitest';
import { RateLimiter, callerOf, withRateLimit } from '../../src/http/middleware/rateLimit.js';
import { Request } from '../../src/http/request.js';
import { json } from '../../src/http/response.js';
import { FixedClock, MINUTE } from '../../src/util/clock.js';
import { ValidationError } from '../../src/errors.js';

describe('RateLimiter', () => {
  it('lets requests through up to the limit', () => {
    const limiter = new RateLimiter({ limit: 3, clock: new FixedClock(0) });
    expect(limiter.offer('a').allowed).toBe(true);
    expect(limiter.offer('a').allowed).toBe(true);
    expect(limiter.offer('a').allowed).toBe(true);
    expect(limiter.offer('a').allowed).toBe(false);
  });

  it('counts down what is left', () => {
    const limiter = new RateLimiter({ limit: 3, clock: new FixedClock(0) });
    expect(limiter.offer('a').remaining).toBe(2);
    expect(limiter.offer('a').remaining).toBe(1);
  });

  it('keeps callers apart', () => {
    const limiter = new RateLimiter({ limit: 1, clock: new FixedClock(0) });
    expect(limiter.offer('a').allowed).toBe(true);
    expect(limiter.offer('b').allowed).toBe(true);
    expect(limiter.offer('a').allowed).toBe(false);
  });

  it('lets requests through again once the window has passed', () => {
    const clock = new FixedClock(0);
    const limiter = new RateLimiter({ limit: 1, window: 1000, clock });
    expect(limiter.offer('a').allowed).toBe(true);
    expect(limiter.offer('a').allowed).toBe(false);
    clock.advance(1001);
    expect(limiter.offer('a').allowed).toBe(true);
  });

  it('slides rather than resetting on a boundary', () => {
    const clock = new FixedClock(0);
    const limiter = new RateLimiter({ limit: 2, window: 1000, clock });
    clock.advance(900);
    expect(limiter.offer('a').allowed).toBe(true);
    clock.advance(99);
    expect(limiter.offer('a').allowed).toBe(true);
    clock.advance(2);
    expect(limiter.offer('a').allowed).toBe(false);
  });

  it('drops a request that has aged out of the window', () => {
    const clock = new FixedClock(0);
    const limiter = new RateLimiter({ limit: 2, window: 1000, clock });
    limiter.offer('a');
    clock.advance(900);
    limiter.offer('a');
    clock.advance(200);
    expect(limiter.offer('a').allowed).toBe(true);
  });

  it('says how long to wait', () => {
    const clock = new FixedClock(0);
    const limiter = new RateLimiter({ limit: 1, window: 5000, clock });
    limiter.offer('a');
    clock.advance(1000);
    expect(limiter.offer('a').retryAfter).toBe(4);
  });

  it('forgets callers who have gone away', () => {
    const clock = new FixedClock(0);
    const limiter = new RateLimiter({ limit: 5, window: 1000, clock });
    limiter.offer('a');
    limiter.offer('b');
    expect(limiter.size).toBe(2);
    clock.advance(2000);
    expect(limiter.sweep()).toBe(2);
    expect(limiter.size).toBe(0);
  });

  it('keeps a caller who is still going', () => {
    const clock = new FixedClock(0);
    const limiter = new RateLimiter({ limit: 5, window: 1000, clock });
    limiter.offer('a');
    clock.advance(500);
    expect(limiter.sweep()).toBe(0);
  });

  it('refuses settings that would let nobody in', () => {
    expect(() => new RateLimiter({ limit: 0 })).toThrow(/would let nobody in/);
    expect(() => new RateLimiter({ window: 0 })).toThrow(ValidationError);
  });

  it('defaults to a minute', () => {
    expect(new RateLimiter().window).toBe(MINUTE);
  });
});

describe('callerOf', () => {
  it('uses the forwarded address when there is one', () => {
    const request = new Request({ url: '/', headers: { 'x-forwarded-for': '10.0.0.1, 10.0.0.2' } });
    expect(callerOf(request)).toBe('10.0.0.1');
  });

  it('falls back to a caller header', () => {
    expect(callerOf(new Request({ url: '/', headers: { 'x-caller': 'cli' } }))).toBe('cli');
  });

  it('calls an unidentified request anonymous', () => {
    expect(callerOf(new Request({ url: '/' }))).toBe('anonymous');
  });
});

describe('withRateLimit', () => {
  const handler = async () => json({ ok: true });
  const request = () => new Request({ url: '/', headers: { 'x-caller': 'cli' } });

  it('lets a request through and says what is left', async () => {
    const wrapped = withRateLimit(handler, { limit: 2, clock: new FixedClock(0) });
    const response = await wrapped(request());
    expect(response.status).toBe(200);
    expect(response.headers['x-rate-limit-remaining']).toBe('1');
  });

  it('refuses one over the limit', async () => {
    const wrapped = withRateLimit(handler, { limit: 1, clock: new FixedClock(0) });
    await wrapped(request());
    const response = await wrapped(request());
    expect(response.status).toBe(429);
    expect(response.body.error.code).toBe('too_many_requests');
    expect(response.headers['retry-after']).toBeDefined();
  });

  it('takes a limiter it is given, so several handlers can share one', async () => {
    const limiter = new RateLimiter({ limit: 1, clock: new FixedClock(0) });
    const first = withRateLimit(handler, { limiter });
    const second = withRateLimit(handler, { limiter });
    expect((await first(request())).status).toBe(200);
    expect((await second(request())).status).toBe(429);
  });
});
