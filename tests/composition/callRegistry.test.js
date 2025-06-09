import { describe, it, expect, afterEach } from 'vitest';
import { CallRegistry, defaultRegistry } from '../../src/composition/callRegistry.js';
import {
  standardCalls,
  sharedRegistry,
  resetRegistry,
  placeCall,
} from '../../src/composition/callSet.js';
import { method } from '../../src/method/method.js';
import { ValidationError, NotFoundError } from '../../src/errors.js';

const plainBobMajor = () => method('Plain', 8, 'x18x18x18x18,12');
const grandsireDoubles = () => method('Grandsire', 5, '3,1.5.1.5.1');

afterEach(() => {
  resetRegistry();
});

describe('defaultRegistry', () => {
  it('tries the thirds place family first', () => {
    expect(defaultRegistry().names()).toEqual(['thirds place', 'fourths place']);
  });

  it('gives grandsire its thirds place calls', () => {
    expect(defaultRegistry().entryFor(grandsireDoubles())).toBe('thirds place');
    expect(defaultRegistry().callsFor(grandsireDoubles()).bob.notation).toBe('3');
  });

  it('gives everything else the fourths place calls', () => {
    expect(defaultRegistry().entryFor(plainBobMajor())).toBe('fourths place');
    expect(defaultRegistry().callsFor(plainBobMajor()).bob.notation).toBe('14');
  });

  it('always includes the plain lead', () => {
    expect(defaultRegistry().callsFor(plainBobMajor()).plain.isPlain()).toBe(true);
  });
});

describe('CallRegistry', () => {
  it('starts empty and finds nothing', () => {
    const registry = new CallRegistry();
    expect(registry.names()).toEqual([]);
    expect(() => registry.entryFor(plainBobMajor())).toThrow(NotFoundError);
  });

  it('tries the most recently registered entry first', () => {
    const registry = defaultRegistry().register(
      'tower special',
      () => true,
      (m) => ({ bob: placeCall(m.stage, 6) }),
    );
    expect(registry.names()[0]).toBe('tower special');
    expect(registry.callsFor(plainBobMajor()).bob.notation).toBe('16');
  });

  it('lets an entry decline so a later one takes it', () => {
    const registry = defaultRegistry().register(
      'minor only',
      (m) => m.stage === 6,
      (m) => ({ bob: placeCall(m.stage, 6) }),
    );
    expect(registry.entryFor(plainBobMajor())).toBe('fourths place');
  });

  it('refuses an entry with no name', () => {
    expect(() =>
      new CallRegistry().register(
        '',
        () => true,
        () => ({}),
      ),
    ).toThrow(/needs a name/);
  });

  it('refuses an entry with no test or builder', () => {
    expect(() => new CallRegistry().register('bad', null, () => ({}))).toThrow(
      /needs a test and a builder/,
    );
  });

  it('refuses something that is not a method', () => {
    expect(() => defaultRegistry().entryFor('Plain Bob')).toThrow(ValidationError);
  });
});

describe('the shared registry', () => {
  it('is used when standardCalls is given none', () => {
    expect(standardCalls(plainBobMajor()).bob.notation).toBe('14');
  });

  it('is the same registry every time', () => {
    expect(sharedRegistry()).toBe(sharedRegistry());
  });

  it('keeps an entry a tower registers', () => {
    sharedRegistry().register(
      'tower special',
      (m) => m.stage === 8,
      (m) => ({ bob: placeCall(m.stage, 6) }),
    );
    expect(standardCalls(plainBobMajor()).bob.notation).toBe('16');
  });

  it('is rebuilt after a reset', () => {
    const first = sharedRegistry();
    resetRegistry();
    expect(sharedRegistry()).not.toBe(first);
  });

  it('takes a registry of its own instead', () => {
    const registry = new CallRegistry().register(
      'everything',
      () => true,
      (m) => ({ bob: placeCall(m.stage, 6) }),
    );
    expect(standardCalls(plainBobMajor(), registry).bob.notation).toBe('16');
  });
});
