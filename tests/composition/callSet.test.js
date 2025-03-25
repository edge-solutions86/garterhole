import { describe, it, expect } from 'vitest';
import {
  fourthsPlaceCalls,
  thirdsPlaceCalls,
  standardCalls,
  placeCall,
  callBySymbol,
  symbolsOf,
} from '../../src/composition/callSet.js';
import { method } from '../../src/method/method.js';
import { ValidationError } from '../../src/errors.js';

const plainBobMajor = () => method('Plain', 8, 'x18x18x18x18,12');
const grandsireDoubles = () => method('Grandsire', 5, '3,1.5.1.5.1');
const cambridgeMajor = () => method('Cambridge', 8, 'x38x14x1258x36x14x58x16x78,12');

describe('fourthsPlaceCalls', () => {
  it('makes a bob in fourths and a single under it', () => {
    const calls = fourthsPlaceCalls(8);
    expect(calls.bob.notation).toBe('14');
    expect(calls.single.notation).toBe('1234');
  });

  it('fills in the implied places at a stage that needs them', () => {
    expect(fourthsPlaceCalls(6).bob.notation).toBe('14');
    expect(fourthsPlaceCalls(5).bob.notation).toBe('145');
  });

  it('refuses a stage too small to make fourths', () => {
    expect(() => fourthsPlaceCalls(4)).toThrow(/at least five bells, not 4/);
  });
});

describe('thirdsPlaceCalls', () => {
  it('makes a bob in thirds', () => {
    expect(thirdsPlaceCalls(5).bob.notation).toBe('3');
    expect(thirdsPlaceCalls(7).bob.notation).toBe('3');
  });

  it('holds two bells under the single', () => {
    expect(thirdsPlaceCalls(5).single.notation).toBe('123');
  });

  it('refuses too small a stage', () => {
    expect(() => thirdsPlaceCalls(4)).toThrow(ValidationError);
  });
});

describe('standardCalls', () => {
  it('gives the fourths place pair to a method with one hunt bell', () => {
    expect(standardCalls(plainBobMajor()).bob.notation).toBe('14');
    expect(standardCalls(cambridgeMajor()).bob.notation).toBe('14');
  });

  it('gives the thirds place pair to grandsire', () => {
    expect(standardCalls(grandsireDoubles()).bob.notation).toBe('3');
  });

  it('includes the plain lead', () => {
    expect(standardCalls(plainBobMajor()).plain.isPlain()).toBe(true);
  });

  it('refuses something that is not a method', () => {
    expect(() => standardCalls('Plain Bob')).toThrow(ValidationError);
  });
});

describe('placeCall', () => {
  it('builds a call that makes a named place', () => {
    expect(placeCall(8, 6).notation).toBe('16');
    expect(placeCall(8, 6).name).toBe('6ths place bob');
  });

  it('takes a name and a symbol', () => {
    const named = placeCall(8, 6, { name: 'Middle', symbol: 'm' });
    expect(named.name).toBe('Middle');
    expect(named.symbol).toBe('m');
  });

  it('refuses a place outside the stage', () => {
    expect(() => placeCall(6, 9)).toThrow(/place 9 is outside/);
  });
});

describe('callBySymbol', () => {
  const calls = standardCalls(plainBobMajor());

  it('finds a call by its symbol', () => {
    expect(callBySymbol(calls, '-').name).toBe('Bob');
    expect(callBySymbol(calls, 's').name).toBe('Single');
    expect(callBySymbol(calls, '.').name).toBe('Plain');
  });

  it('gives undefined for a symbol nobody uses', () => {
    expect(callBySymbol(calls, 'z')).toBeUndefined();
  });
});

describe('symbolsOf', () => {
  it('lists every symbol', () => {
    expect(symbolsOf(standardCalls(plainBobMajor())).sort()).toEqual(['-', '.', 's']);
  });

  it('gives nothing for an empty set', () => {
    expect(symbolsOf({})).toEqual([]);
  });
});
