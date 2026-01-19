import { describe, it, expect } from 'vitest';
import { Response, json, text, noContent, created, problem } from '../../src/http/response.js';
import { VERSION } from '../../src/version.js';
import { ValidationError } from '../../src/errors.js';

describe('Response', () => {
  it('defaults to two hundred', () => {
    expect(new Response().status).toBe(200);
  });

  it('is frozen', () => {
    const response = new Response({ body: 'hello' });
    expect(Object.isFrozen(response)).toBe(true);
    expect(Object.isFrozen(response.headers)).toBe(true);
  });

  it('refuses a status that is not one', () => {
    expect(() => new Response({ status: 99 })).toThrow(/is not a status code/);
    expect(() => new Response({ status: 600 })).toThrow(ValidationError);
    expect(() => new Response({ status: 200.5 })).toThrow(ValidationError);
  });

  it('gives a new response when a header is added', () => {
    const first = new Response();
    const second = first.withHeader('x-tower', 'St Mary');
    expect(second.headers['x-tower']).toBe('St Mary');
    expect(first.headers['x-tower']).toBeUndefined();
  });

  it('gives a new response when the status changes', () => {
    const first = json({ a: 1 });
    expect(first.withStatus(201).status).toBe(201);
    expect(first.status).toBe(200);
  });
});

describe('rendering', () => {
  it('writes JSON out and measures it', () => {
    const rendered = json({ a: 1 }).render();
    expect(rendered.text).toBe('{"a":1}');
    expect(rendered.headers['content-length']).toBe('7');
    expect(rendered.headers['content-type']).toBe('application/json; charset=utf-8');
  });

  it('says which version answered', () => {
    expect(json({}).render().headers['x-served-by']).toContain(VERSION);
  });

  it('measures bytes rather than characters', () => {
    const rendered = text('café').render();
    expect(rendered.headers['content-length']).toBe('5');
  });

  it('renders an empty body as nothing', () => {
    const rendered = noContent().render();
    expect(rendered.text).toBe('');
    expect(rendered.status).toBe(204);
  });

  it('leaves a string body alone', () => {
    expect(text('hello').render().text).toBe('hello');
  });

  it('lets a header the caller set win', () => {
    const rendered = json({}, { headers: { 'content-type': 'application/problem+json' } }).render();
    expect(rendered.headers['content-type']).toBe('application/problem+json');
  });
});

describe('the shortcuts', () => {
  it('makes a JSON response', () => {
    const response = json({ a: 1 }, { status: 202 });
    expect(response.status).toBe(202);
    expect(response.body).toEqual({ a: 1 });
  });

  it('makes a text response', () => {
    const response = text('hello');
    expect(response.headers['content-type']).toBe('text/plain; charset=utf-8');
    expect(response.body).toBe('hello');
  });

  it('makes a created response with a location', () => {
    const response = created('/towers/st-mary', { id: 'st-mary' });
    expect(response.status).toBe(201);
    expect(response.headers.location).toBe('/towers/st-mary');
  });

  it('makes an empty response', () => {
    expect(noContent().body).toBeUndefined();
  });
});

describe('problem', () => {
  it('puts every error in the same shape', () => {
    const response = problem(404, 'not_found', 'no tower with id "x"');
    expect(response.status).toBe(404);
    expect(response.body).toEqual({
      error: { code: 'not_found', message: 'no tower with id "x"' },
    });
  });

  it('carries the issues when there are some', () => {
    const response = problem(400, 'validation_failed', 'the request is wrong', {
      issues: [{ path: 'name', message: 'must be a string' }],
    });
    expect(response.body.error.issues).toHaveLength(1);
  });

  it('leaves the issues out when there are none', () => {
    expect(problem(400, 'validation_failed', 'nope').body.error.issues).toBeUndefined();
  });
});
