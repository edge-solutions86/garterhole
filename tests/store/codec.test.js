import { describe, it, expect } from 'vitest';
import { checksum, encode, decode, decodeAll, encodeAll } from '../../src/store/codec.js';
import { StorageError, ValidationError } from '../../src/errors.js';

describe('checksum', () => {
  it('is eight hexadecimal characters', () => {
    expect(checksum('hello')).toMatch(/^[0-9a-f]{8}$/);
  });

  it('is the same for the same text', () => {
    expect(checksum('hello')).toBe(checksum('hello'));
  });

  it('changes when the text changes', () => {
    expect(checksum('hello')).not.toBe(checksum('hellp'));
  });

  it('notices a transposition', () => {
    expect(checksum('ab')).not.toBe(checksum('ba'));
  });

  it('handles a long string and an empty one', () => {
    expect(checksum('')).toMatch(/^[0-9a-f]{8}$/);
    expect(checksum('x'.repeat(10000))).toMatch(/^[0-9a-f]{8}$/);
  });
});

describe('encode', () => {
  it('puts the checksum in front of the payload', () => {
    const line = encode({ a: 1 });
    expect(line.slice(0, 8)).toBe(checksum('{"a":1}'));
    expect(line.slice(9)).toBe('{"a":1}');
  });

  it('writes no newline', () => {
    expect(encode({ a: 1 })).not.toContain('\n');
  });

  it('refuses nothing at all', () => {
    expect(() => encode(undefined)).toThrow(/nothing to write/);
  });

  it('refuses something JSON cannot hold', () => {
    const looping = {};
    looping.self = looping;
    expect(() => encode(looping)).toThrow(/cannot be written as JSON/);
    expect(() => encode(() => 1)).toThrow(ValidationError);
  });
});

describe('decode', () => {
  it('reads a line back', () => {
    expect(decode(encode({ a: 1, b: 'two' }))).toEqual({ a: 1, b: 'two' });
  });

  it('round trips every kind of value', () => {
    for (const value of [null, 0, 'text', [1, 2, 3], { nested: { deep: true } }]) {
      expect(decode(encode(value))).toEqual(value);
    }
  });

  it('refuses a line with no checksum', () => {
    expect(() => decode('{"a":1}', 4)).toThrow('line 4 does not start with a checksum');
  });

  it('catches a payload that has been changed', () => {
    const line = encode({ a: 1 });
    const damaged = `${line.slice(0, 9)}{"a":2}`;
    expect(() => decode(damaged, 7)).toThrow(StorageError);
    expect(() => decode(damaged, 7)).toThrow(/line 7 is damaged/);
  });

  it('catches a payload that is not JSON', () => {
    const payload = 'not json';
    expect(() => decode(`${checksum(payload)} ${payload}`, 2)).toThrow(/line 2 is not JSON/);
  });

  it('refuses something that is not a string', () => {
    expect(() => decode(null)).toThrow(ValidationError);
  });
});

describe('whole files', () => {
  const records = [{ a: 1 }, { b: 2 }, { c: 3 }];

  it('writes a newline after every record', () => {
    const text = encodeAll(records);
    expect(text.endsWith('\n')).toBe(true);
    expect(text.split('\n').filter((line) => line.length > 0)).toHaveLength(3);
  });

  it('reads them back in order', () => {
    expect(decodeAll(encodeAll(records))).toEqual(records);
  });

  it('reads an empty file', () => {
    expect(decodeAll('')).toEqual([]);
    expect(encodeAll([])).toBe('');
  });

  it('reports a damaged line rather than skipping it', () => {
    const text = `${encodeAll(records)}00000000 {"d":4}\n`;
    expect(() => decodeAll(text)).toThrow(/line 4 is damaged/);
  });

  it('refuses something that is not a string', () => {
    expect(() => decodeAll(null)).toThrow(ValidationError);
  });
});
