import { describe, it, expect } from 'vitest';
import {
  assertInteger,
  assertIntegerInRange,
  assertNonEmptyString,
  assertOneOf,
  assertArray,
  assertNonEmptyArray,
  assertFunction,
  assertPlainObject,
  describe as describeValue,
} from '../../src/util/assert.js';

describe('assertInteger', () => {
  it('returns the value it was given', () => {
    expect(assertInteger(7, 'lead')).toBe(7);
    expect(assertInteger(0, 'lead')).toBe(0);
    expect(assertInteger(-3, 'lead')).toBe(-3);
  });

  it('refuses a fraction', () => {
    expect(() => assertInteger(1.5, 'lead')).toThrow(/lead 1.5 is not a whole number/);
  });

  it('refuses a string that looks like a number', () => {
    expect(() => assertInteger('7', 'lead')).toThrow(/lead "7" is not a whole number/);
  });

  it('refuses NaN and infinity', () => {
    expect(() => assertInteger(Number.NaN, 'lead')).toThrow(RangeError);
    expect(() => assertInteger(Number.POSITIVE_INFINITY, 'lead')).toThrow(RangeError);
  });
});

describe('assertIntegerInRange', () => {
  it('accepts both ends of the range', () => {
    expect(assertIntegerInRange(1, 1, 8, 'bell')).toBe(1);
    expect(assertIntegerInRange(8, 1, 8, 'bell')).toBe(8);
  });

  it('names the range in the message', () => {
    expect(() => assertIntegerInRange(9, 1, 8, 'bell')).toThrow('bell 9 is outside 1..8');
    expect(() => assertIntegerInRange(0, 1, 8, 'bell')).toThrow('bell 0 is outside 1..8');
  });

  it('refuses a value that is not a number at all', () => {
    expect(() => assertIntegerInRange(undefined, 1, 8, 'bell')).toThrow(
      'bell undefined is outside 1..8',
    );
  });
});

describe('assertNonEmptyString', () => {
  it('accepts a string with content', () => {
    expect(assertNonEmptyString('Cambridge', 'title')).toBe('Cambridge');
  });

  it('refuses the empty string', () => {
    expect(() => assertNonEmptyString('', 'title')).toThrow(TypeError);
  });

  it('refuses a non string', () => {
    expect(() => assertNonEmptyString(12, 'title')).toThrow(/title must be a non empty string/);
    expect(() => assertNonEmptyString(null, 'title')).toThrow(TypeError);
  });
});

describe('assertOneOf', () => {
  it('accepts a listed value', () => {
    expect(assertOneOf('bob', ['plain', 'bob', 'single'], 'call')).toBe('bob');
  });

  it('lists the alternatives when it refuses', () => {
    expect(() => assertOneOf('extreme', ['plain', 'bob', 'single'], 'call')).toThrow(
      'call "extreme" must be one of plain, bob, single',
    );
  });
});

describe('array guards', () => {
  it('accepts an array', () => {
    expect(assertArray([1, 2], 'places')).toEqual([1, 2]);
  });

  it('refuses a string, which is iterable but not an array', () => {
    expect(() => assertArray('12', 'places')).toThrow(/places must be an array/);
  });

  it('refuses an empty array only where content is required', () => {
    expect(assertArray([], 'places')).toEqual([]);
    expect(() => assertNonEmptyArray([], 'places')).toThrow(/places must not be empty/);
  });
});

describe('assertFunction', () => {
  it('accepts a function', () => {
    const fn = () => 1;
    expect(assertFunction(fn, 'callback')).toBe(fn);
  });

  it('refuses anything else', () => {
    expect(() => assertFunction({}, 'callback')).toThrow(/callback must be a function/);
  });
});

describe('assertPlainObject', () => {
  it('accepts an object literal', () => {
    expect(assertPlainObject({ a: 1 }, 'options')).toEqual({ a: 1 });
  });

  it('accepts an object with a null prototype', () => {
    const bare = Object.create(null);
    expect(assertPlainObject(bare, 'options')).toBe(bare);
  });

  it('refuses null, an array and a class instance', () => {
    expect(() => assertPlainObject(null, 'options')).toThrow(/options must be an object/);
    expect(() => assertPlainObject([], 'options')).toThrow(/options must be an object/);
    expect(() => assertPlainObject(new Date(), 'options')).toThrow(
      /options must be a plain object/,
    );
  });
});

describe('describe', () => {
  it('quotes strings so that padding is visible', () => {
    expect(describeValue(' x ')).toBe('" x "');
  });

  it('prints numbers, booleans and null as they are', () => {
    expect(describeValue(4)).toBe('4');
    expect(describeValue(true)).toBe('true');
    expect(describeValue(null)).toBe('null');
    expect(describeValue(undefined)).toBe('undefined');
  });

  it('summarises an array rather than dumping it', () => {
    expect(describeValue([1, 2, 3])).toBe('an array of 3');
  });

  it('falls back to the type name', () => {
    expect(describeValue({})).toBe('object');
    expect(describeValue(() => 1)).toBe('function');
  });
});
