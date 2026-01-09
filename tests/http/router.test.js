import { describe, it, expect } from 'vitest';
import { Router } from '../../src/http/router.js';
import { ValidationError } from '../../src/errors.js';

const handler = () => 'answered';

describe('adding routes', () => {
  it('takes a method, a pattern and a handler', () => {
    const router = new Router().get('/health', handler);
    expect(router.list()).toEqual([{ method: 'GET', pattern: '/health' }]);
  });

  it('has a shortcut for each method it uses', () => {
    const router = new Router().get('/a', handler).post('/b', handler).delete('/c', handler);
    expect(router.list().map((route) => route.method)).toEqual(['GET', 'POST', 'DELETE']);
  });

  it('upper cases the method', () => {
    expect(new Router().add('get', '/a', handler).list()[0].method).toBe('GET');
  });

  it('refuses a pattern with no leading slash', () => {
    expect(() => new Router().get('health', handler)).toThrow(/must start with a slash/);
  });

  it('refuses a route it cannot use', () => {
    expect(() => new Router().get('', handler)).toThrow(/a route pattern/);
    expect(() => new Router().get('/a', 'answer')).toThrow(/a route handler/);
    expect(() => new Router().get('health', handler)).toThrow(ValidationError);
  });

  it('refuses a route that could never be reached', () => {
    const router = new Router().get('/towers/:id', handler);
    expect(() => router.get('/towers/:name', handler)).toThrow(/would never be reached/);
  });

  it('allows a literal route the parameter route would shadow, in either order', () => {
    const router = new Router().get('/towers/all', handler);
    expect(() => router.get('/towers/:id', handler)).not.toThrow();
  });

  it('allows the same pattern under a different method', () => {
    const router = new Router().get('/towers/:id', handler);
    expect(() => router.delete('/towers/:id', handler)).not.toThrow();
  });
});

describe('matching', () => {
  const router = () =>
    new Router()
      .get('/health', handler)
      .get('/towers/all', handler)
      .get('/towers/:id', handler)
      .get('/towers/:id/performances', handler)
      .post('/towers', handler);

  it('finds a plain route', () => {
    expect(router().match('GET', '/health').pattern).toBe('/health');
  });

  it('captures a parameter', () => {
    const found = router().match('GET', '/towers/st-mary');
    expect(found.params).toEqual({ id: 'st-mary' });
  });

  it('captures several segments deep', () => {
    const found = router().match('GET', '/towers/st-mary/performances');
    expect(found.pattern).toBe('/towers/:id/performances');
    expect(found.params.id).toBe('st-mary');
  });

  it('prefers the route added first', () => {
    expect(router().match('GET', '/towers/all').pattern).toBe('/towers/all');
  });

  it('decodes what is in a parameter', () => {
    expect(router().match('GET', '/towers/st%20mary').params.id).toBe('st mary');
  });

  it('ignores a trailing slash', () => {
    expect(router().match('GET', '/health/')).not.toBeNull();
  });

  it('gives null when nothing matches', () => {
    expect(router().match('GET', '/nothing')).toBeNull();
    expect(router().match('GET', '/towers/st-mary/bells')).toBeNull();
  });

  it('gives null when the method is wrong', () => {
    expect(router().match('POST', '/health')).toBeNull();
  });
});

describe('methodsFor', () => {
  const router = () =>
    new Router().get('/towers/:id', handler).delete('/towers/:id', handler).get('/health', handler);

  it('lists the methods a path answers to', () => {
    expect(router().methodsFor('/towers/st-mary')).toEqual(['DELETE', 'GET']);
  });

  it('lists one method where there is one', () => {
    expect(router().methodsFor('/health')).toEqual(['GET']);
  });

  it('lists nothing for a path nobody serves', () => {
    expect(router().methodsFor('/nothing')).toEqual([]);
  });
});
