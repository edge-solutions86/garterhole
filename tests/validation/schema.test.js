import { describe, it, expect } from 'vitest';
import {
  anything,
  string,
  number,
  boolean,
  oneOf,
  arrayOf,
  object,
  optional,
  withDefault,
  check,
  validate,
} from '../../src/validation/schema.js';
import { ValidationError } from '../../src/errors.js';

const messages = (schema, value) => schema(value, '').map((problem) => problem.message);

describe('string', () => {
  it('accepts a string', () => {
    expect(messages(string(), 'hello')).toEqual([]);
  });

  it('refuses anything else', () => {
    expect(messages(string(), 7)).toEqual(['must be a string']);
    expect(messages(string(), null)).toEqual(['must be a string']);
  });

  it('checks a minimum and a maximum length', () => {
    expect(messages(string({ min: 3 }), 'ab')).toEqual(['must be at least 3 characters']);
    expect(messages(string({ max: 2 }), 'abc')).toEqual(['must be at most 2 characters']);
  });

  it('checks a pattern', () => {
    expect(messages(string({ pattern: /^[a-z]+$/ }), 'Abc')[0]).toContain('must match');
    expect(messages(string({ pattern: /^[a-z]+$/ }), 'abc')).toEqual([]);
  });
});

describe('number', () => {
  it('accepts a number', () => {
    expect(messages(number(), 4)).toEqual([]);
  });

  it('refuses anything that is not one', () => {
    expect(messages(number(), '4')).toEqual(['must be a number']);
    expect(messages(number(), Number.NaN)).toEqual(['must be a number']);
  });

  it('checks whole numbers and bounds', () => {
    expect(messages(number({ integer: true }), 1.5)).toEqual(['must be a whole number']);
    expect(messages(number({ min: 3 }), 2)).toEqual(['must be at least 3']);
    expect(messages(number({ max: 3 }), 4)).toEqual(['must be at most 3']);
  });
});

describe('boolean and oneOf', () => {
  it('accepts true and false', () => {
    expect(messages(boolean(), true)).toEqual([]);
    expect(messages(boolean(), 'yes')).toEqual(['must be true or false']);
  });

  it('accepts a listed value', () => {
    expect(messages(oneOf(['a', 'b']), 'b')).toEqual([]);
    expect(messages(oneOf(['a', 'b']), 'c')).toEqual(['must be one of a, b']);
  });

  it('refuses a choice with nothing to choose from', () => {
    expect(() => oneOf([])).toThrow(/something to choose from/);
  });
});

describe('arrayOf', () => {
  it('checks every entry', () => {
    expect(messages(arrayOf(number()), [1, 2])).toEqual([]);
    expect(messages(arrayOf(number()), [1, 'two'])).toEqual(['must be a number']);
  });

  it('names the entry that was wrong', () => {
    const problems = arrayOf(number())([1, 'two'], 'bells');
    expect(problems[0].path).toBe('bells[1]');
  });

  it('refuses something that is not a list', () => {
    expect(messages(arrayOf(number()), 'one')).toEqual(['must be a list']);
  });

  it('checks how many entries there are', () => {
    expect(messages(arrayOf(number(), { min: 2 }), [1])).toEqual(['must have at least 2 entries']);
    expect(messages(arrayOf(number(), { max: 1 }), [1, 2])).toEqual([
      'must have at most 1 entries',
    ]);
  });
});

describe('object', () => {
  const person = object({ name: string({ min: 1 }), age: number({ integer: true }) });

  it('checks every field', () => {
    expect(messages(person, { name: 'Alice', age: 40 })).toEqual([]);
  });

  it('reports every field that is wrong at once', () => {
    const problems = person({ name: '', age: 'old' }, '');
    expect(problems).toHaveLength(2);
    expect(problems.map((entry) => entry.path)).toEqual(['name', 'age']);
  });

  it('ignores fields the schema does not know about', () => {
    expect(messages(person, { name: 'Alice', age: 40, colour: 'blue' })).toEqual([]);
  });

  it('refuses something that is not an object', () => {
    expect(messages(person, [])).toEqual(['must be an object']);
    expect(messages(person, null)).toEqual(['must be an object']);
  });

  it('names nested paths', () => {
    const nested = object({ inner: object({ n: number() }) });
    expect(nested({ inner: { n: 'x' } }, '')[0].path).toBe('inner.n');
  });

  it('refuses a schema with no fields', () => {
    expect(() => object(null)).toThrow(/needs its fields/);
  });
});

describe('optional fields', () => {
  const record = object({ name: string(), note: optional(string()) });

  it('accepts a missing optional field', () => {
    expect(messages(record, { name: 'Alice' })).toEqual([]);
  });

  it('still checks it when it is there', () => {
    expect(messages(record, { name: 'Alice', note: 7 })).toEqual(['must be a string']);
  });

  it('carries a default that a reader can use', () => {
    const field = withDefault(number(), 8);
    expect(field.fallback).toBe(8);
    expect(messages(field, undefined)).toEqual([]);
    expect(messages(field, 'x')).toEqual(['must be a number']);
  });
});

describe('check and validate', () => {
  const person = object({ name: string({ min: 1 }) });

  it('gives the value back when it is sound', () => {
    expect(check(person, { name: 'Alice' })).toEqual({ name: 'Alice' });
  });

  it('throws with the first problem and keeps them all', () => {
    try {
      check(person, { name: '' }, 'the ringer');
      throw new Error('should have thrown');
    } catch (error) {
      expect(error).toBeInstanceOf(ValidationError);
      expect(error.message).toBe('the ringer is wrong: name must be at least 1 characters');
      expect(error.issues).toHaveLength(1);
    }
  });

  it('reports rather than throwing', () => {
    expect(validate(person, { name: 'Alice' })).toEqual({ ok: true, problems: [] });
    expect(validate(person, {}).ok).toBe(false);
  });

  it('names the root when the whole value is wrong', () => {
    expect(validate(person, 'Alice').problems[0].path).toBe('(root)');
  });

  it('accepts anything through the anything schema', () => {
    expect(messages(anything(), undefined)).toEqual([]);
    expect(messages(anything(), { a: 1 })).toEqual([]);
  });
});
