import { problem } from '../response.js';
import { systemClock, MINUTE } from '../../util/clock.js';
import { ValidationError } from '../../errors.js';

/**
 * Keeping one caller from taking the whole service.
 *
 * The window slides: each caller has a list of the moments they made a request,
 * anything older than the window is dropped, and the request is refused when
 * what is left is already at the limit. That costs a little more memory than
 * counting into fixed buckets, and it does not have the flaw that fixed buckets
 * have, where twice the limit gets through by straddling a boundary.
 *
 * The clock is handed in, so the tests can move time rather than waiting.
 */
export class RateLimiter {
  /**
   * @param {{ limit?: number, window?: number, clock?: { now: () => number } }} [options]
   */
  constructor(options = {}) {
    this.limit = options.limit ?? 120;
    this.window = options.window ?? MINUTE;
    if (!Number.isInteger(this.limit) || this.limit < 1) {
      throw new ValidationError(`a limit of ${this.limit} requests would let nobody in`);
    }
    if (typeof this.window !== 'number' || this.window <= 0) {
      throw new ValidationError(`a window of ${this.window} is not a length of time`);
    }
    this.clock = options.clock ?? systemClock();
    /** @type {Map<string, number[]>} */
    this.seen = new Map();
  }

  /**
   * Offer a request from a caller.
   *
   * @param {string} caller
   * @returns {{ allowed: boolean, remaining: number, retryAfter: number }}
   */
  offer(caller) {
    const now = this.clock.now();
    const moments = (this.seen.get(caller) ?? []).filter((at) => at > now - this.window);
    if (moments.length >= this.limit) {
      this.seen.set(caller, moments);
      const oldest = moments[0];
      return {
        allowed: false,
        remaining: 0,
        retryAfter: Math.max(1, Math.ceil((oldest + this.window - now) / 1000)),
      };
    }
    moments.push(now);
    this.seen.set(caller, moments);
    return { allowed: true, remaining: this.limit - moments.length, retryAfter: 0 };
  }

  /**
   * Forget callers who have not been seen for a whole window, so that the map
   * does not grow without limit on a service that is left running.
   *
   * @returns {number} how many were forgotten
   */
  sweep() {
    const now = this.clock.now();
    let forgotten = 0;
    for (const [caller, moments] of this.seen) {
      if (moments.every((at) => at <= now - this.window)) {
        this.seen.delete(caller);
        forgotten += 1;
      }
    }
    return forgotten;
  }

  /**
   * @returns {number} how many callers are being tracked
   */
  get size() {
    return this.seen.size;
  }
}

/**
 * Who a request is from, as far as the limiter is concerned.
 *
 * @param {import('../request.js').Request} request
 * @returns {string}
 */
export function callerOf(request) {
  const forwarded = request.header('x-forwarded-for');
  if (typeof forwarded === 'string' && forwarded.length > 0) {
    return forwarded.split(',')[0].trim();
  }
  return request.header('x-caller') ?? 'anonymous';
}

/**
 * Wrap a handler so that a caller making too many requests is refused.
 *
 * @param {(request: import('../request.js').Request) => Promise<import('../response.js').Response>} handler
 * @param {{ limiter?: RateLimiter, limit?: number, clock?: { now: () => number } }} [options]
 * @returns {(request: import('../request.js').Request) => Promise<import('../response.js').Response>}
 */
export function withRateLimit(handler, options = {}) {
  const limiter =
    options.limiter ?? new RateLimiter({ limit: options.limit, clock: options.clock });
  return async (request) => {
    const verdict = limiter.offer(callerOf(request));
    if (!verdict.allowed) {
      return problem(429, 'too_many_requests', 'that is more requests than this service allows')
        .withHeader('retry-after', String(verdict.retryAfter))
        .withHeader('x-rate-limit-remaining', '0');
    }
    const response = await handler(request);
    return response.withHeader('x-rate-limit-remaining', String(verdict.remaining));
  };
}
