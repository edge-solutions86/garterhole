import { describe, it, expect } from 'vitest';
import { pageOf, pageOut, paginate, DEFAULT_LIMIT, MAX_LIMIT } from '../../src/http/pagination.js';
import { Request } from '../../src/http/request.js';
import { ValidationError } from '../../src/errors.js';

const ask = (query) => new Request({ url: `/things${query}` });
const things = (count) => Array.from({ length: count }, (_value, index) => index);

describe('pageOf', () => {
  it('uses the defaults when nothing is asked for', () => {
    expect(pageOf(ask(''))).toEqual({ limit: DEFAULT_LIMIT, offset: 0 });
  });

  it('reads a limit and an offset', () => {
    expect(pageOf(ask('?limit=10&offset=20'))).toEqual({ limit: 10, offset: 20 });
  });

  it('refuses a limit that would return nothing', () => {
    expect(() => pageOf(ask('?limit=0'))).toThrow(/would return nothing/);
  });

  it('refuses a limit larger than the ceiling', () => {
    expect(() => pageOf(ask(`?limit=${MAX_LIMIT + 1}`))).toThrow(/more than the 500 allowed/);
  });

  it('refuses an offset before the start', () => {
    expect(() => pageOf(ask('?offset=-1'))).toThrow(/before the start of the list/);
  });

  it('refuses a limit that is not a number', () => {
    expect(() => pageOf(ask('?limit=lots'))).toThrow(ValidationError);
  });

  it('takes different bounds', () => {
    expect(pageOf(ask(''), { defaultLimit: 5 }).limit).toBe(5);
    expect(() => pageOf(ask('?limit=10'), { maxLimit: 5 })).toThrow(/the 5 allowed/);
  });
});

describe('pageOut', () => {
  it('cuts the page out and describes it', () => {
    const answer = pageOut(things(10), { limit: 3, offset: 0 }, 'things');
    expect(answer.things).toEqual([0, 1, 2]);
    expect(answer.total).toBe(10);
    expect(answer.returned).toBe(3);
    expect(answer.more).toBe(true);
  });

  it('says there is no more at the end', () => {
    const answer = pageOut(things(10), { limit: 3, offset: 8 }, 'things');
    expect(answer.things).toEqual([8, 9]);
    expect(answer.more).toBe(false);
  });

  it('handles an offset past the end', () => {
    const answer = pageOut(things(3), { limit: 3, offset: 10 }, 'things');
    expect(answer.things).toEqual([]);
    expect(answer.more).toBe(false);
  });

  it('handles an empty list', () => {
    const answer = pageOut([], { limit: 10, offset: 0 }, 'things');
    expect(answer.total).toBe(0);
    expect(answer.more).toBe(false);
  });

  it('names the list the way the caller asked', () => {
    expect(pageOut(things(1), { limit: 1, offset: 0 }, 'methods').methods).toEqual([0]);
  });
});

describe('paginate', () => {
  it('reads the page and cuts it in one step', () => {
    const answer = paginate(ask('?limit=2&offset=1'), things(5), 'things');
    expect(answer.things).toEqual([1, 2]);
    expect(answer.offset).toBe(1);
  });

  it('passes a bad page on as a refusal', () => {
    expect(() => paginate(ask('?limit=0'), things(5), 'things')).toThrow(ValidationError);
  });
});
