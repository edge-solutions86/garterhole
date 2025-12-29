import { describe, it, expect } from 'vitest';
import { IdSource, defaultIds, looksLikeId, madeAt } from '../../src/util/id.js';
import { ValidationError } from '../../src/errors.js';

/**
 * A source whose randomness is a fixed sequence, so the ids are known.
 *
 * @param {number} at
 */
function fixed(at = 1_700_000_000_000) {
  let step = 0;
  return new IdSource({
    now: () => at,
    random: () => {
      step += 1;
      return (step % 32) / 32;
    },
  });
}

describe('making identifiers', () => {
  it('makes one of a stated length', () => {
    const id = new IdSource({ length: 6, now: () => 0, random: () => 0 }).next();
    expect(id).toHaveLength(15);
  });

  it('puts a prefix in front with an underscore', () => {
    expect(fixed().next('req').startsWith('req_')).toBe(true);
  });

  it('makes different identifiers each time', () => {
    const source = defaultIds();
    const ids = new Set(source.many(50));
    expect(ids.size).toBe(50);
  });

  it('makes several at once', () => {
    expect(fixed().many(3, 'req')).toHaveLength(3);
  });

  it('makes none when asked for none', () => {
    expect(fixed().many(0)).toEqual([]);
  });

  it('refuses a count that is not one', () => {
    expect(() => fixed().many(-1)).toThrow(/is not a number of identifiers/);
  });

  it('refuses a length that would be useless or silly', () => {
    expect(() => new IdSource({ length: 2 })).toThrow(/cannot have 2 random characters/);
    expect(() => new IdSource({ length: 64 })).toThrow(ValidationError);
  });
});

describe('sorting by time', () => {
  it('sorts identifiers made later after ones made earlier', () => {
    const early = fixed(1_700_000_000_000).next();
    const late = fixed(1_700_000_001_000).next();
    expect([late, early].sort()).toEqual([early, late]);
  });

  it('keeps the same length as time passes', () => {
    expect(fixed(1_000_000_000_000).next()).toHaveLength(fixed(1_900_000_000_000).next().length);
  });
});

describe('looksLikeId', () => {
  it('recognises one it made', () => {
    expect(looksLikeId(fixed().next())).toBe(true);
    expect(looksLikeId(fixed().next('req'))).toBe(true);
  });

  it('rejects something too short', () => {
    expect(looksLikeId('abc')).toBe(false);
  });

  it('rejects a character an identifier never contains', () => {
    expect(looksLikeId('ABCDEFGHIJKLMN')).toBe(false);
    expect(looksLikeId('abcdefg-hijklmn')).toBe(false);
  });

  it('draws the random tail from the alphabet that leaves out the misread letters', () => {
    const tail = fixed().next().slice(9);
    for (const character of tail) {
      expect('ilou'.includes(character)).toBe(false);
    }
  });

  it('rejects something that is not a string', () => {
    expect(looksLikeId(12345)).toBe(false);
    expect(looksLikeId(null)).toBe(false);
  });
});

describe('madeAt', () => {
  it('reads the moment back out', () => {
    const at = 1_700_000_000_000;
    expect(madeAt(fixed(at).next()).getTime()).toBe(at);
  });

  it('reads it out of a prefixed identifier too', () => {
    const at = 1_700_000_000_000;
    expect(madeAt(fixed(at).next('req')).getTime()).toBe(at);
  });

  it('gives null for something that is not one', () => {
    expect(madeAt('not an id')).toBeNull();
    expect(madeAt(7)).toBeNull();
  });
});
