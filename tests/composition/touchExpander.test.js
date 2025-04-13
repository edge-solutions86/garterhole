import { describe, it, expect } from 'vitest';
import { expandTouch, touchLength, MAX_LEADS } from '../../src/composition/touchExpander.js';
import { parseTouch } from '../../src/composition/touchParser.js';
import { method } from '../../src/method/method.js';
import { standardCalls } from '../../src/composition/callSet.js';
import { rounds } from '../../src/core/row.js';
import { ValidationError } from '../../src/errors.js';

const plainBobMajor = () => method('Plain', 8, 'x18x18x18x18,12');
const plainBobMinor = () => method('Plain', 6, 'x1x1x1,2');

/**
 * @param {import('../../src/method/method.js').Method} method
 * @param {string} text
 * @param {object} [options]
 */
const ring = (method, text, options = {}) =>
  expandTouch({ method, touch: parseTouch(text), ...options });

describe('touches written as one symbol per lead', () => {
  it('rings a plain course', () => {
    const rung = ring(plainBobMinor(), '.....');
    expect(rung.block.changeCount).toBe(60);
    expect(rung.comesRound).toBe(true);
    expect(rung.leads).toHaveLength(5);
  });

  it('rings a bobbed touch that comes round', () => {
    const rung = ring(plainBobMinor(), '2(-..)');
    expect(rung.block.changeCount).toBe(72);
    expect(rung.comesRound).toBe(true);
  });

  it('reports a touch that does not come round rather than ringing on', () => {
    const rung = ring(plainBobMinor(), '-.-.-.');
    expect(rung.comesRound).toBe(false);
    expect(rung.block.changeCount).toBe(72);
  });

  it('records the call made at every lead', () => {
    const rung = ring(plainBobMinor(), '-..');
    expect(rung.leads.map((lead) => lead.symbol)).toEqual(['-', '.', '.']);
    expect(rung.leads.every((lead) => lead.position === null)).toBe(true);
  });

  it('measures the length', () => {
    expect(touchLength(ring(plainBobMinor(), '.....'))).toBe(60);
  });
});

describe('touches written as calling positions', () => {
  it('rings three bobs home on major', () => {
    const rung = ring(plainBobMajor(), '3(H)');
    expect(rung.block.changeCount).toBe(336);
    expect(rung.comesRound).toBe(true);
    expect(rung.leads).toHaveLength(21);
  });

  it('rings three bobs home on minor', () => {
    const rung = ring(plainBobMinor(), '3(H)');
    expect(rung.block.changeCount).toBe(180);
    expect(rung.comesRound).toBe(true);
  });

  it('is true', () => {
    const rung = ring(plainBobMajor(), '3(H)');
    const body = rung.block.slice(0, rung.block.length - 1);
    expect(body.isTrue()).toBe(true);
  });

  it('records the position of every call', () => {
    const rung = ring(plainBobMajor(), '3(H)');
    const called = rung.leads.filter((lead) => lead.symbol === '-');
    expect(called).toHaveLength(3);
    expect(called.every((lead) => lead.position === 'H')).toBe(true);
  });

  it('rings the plain leads in between without a position', () => {
    const rung = ring(plainBobMajor(), '3(H)');
    expect(rung.leads.filter((lead) => lead.position === null)).toHaveLength(18);
  });

  it('reports a calling that never comes round', () => {
    expect(() => ring(plainBobMinor(), '5(H)')).toThrow(/never comes round/);
  });

  it('can be told not to ring on to rounds', () => {
    const rung = ring(plainBobMajor(), 'H', { ringToRounds: false });
    expect(rung.comesRound).toBe(false);
    expect(rung.leads).toHaveLength(7);
  });
});

describe('choosing the calls', () => {
  it('uses the standard calls when none are given', () => {
    const rung = ring(plainBobMajor(), '3(H)');
    expect(rung.leads.some((lead) => lead.symbol === '-')).toBe(true);
  });

  it('takes a call set', () => {
    const calls = standardCalls(plainBobMajor());
    const rung = ring(plainBobMajor(), '3(H)', { calls });
    expect(rung.comesRound).toBe(true);
  });

  it('refuses a symbol no call uses', () => {
    expect(() => ring(plainBobMinor(), 'z..')).toThrow(/no call is written "z"/);
  });

  it('takes a different observation bell', () => {
    const rung = ring(plainBobMajor(), '3(H)', { observation: 7 });
    expect(rung.comesRound).toBe(true);
  });

  it('refuses a position the observation bell never reaches', () => {
    expect(() => ring(plainBobMinor(), 'B')).toThrow(/never reaches "B"/);
  });
});

describe('bad input', () => {
  it('refuses something that is not a method', () => {
    expect(() => expandTouch({ method: 'Plain Bob', touch: parseTouch('') })).toThrow(
      ValidationError,
    );
  });

  it('refuses something that is not a parsed touch', () => {
    expect(() => expandTouch({ method: plainBobMinor(), touch: '-.-' })).toThrow(
      /a parsed touch is needed/,
    );
  });

  it('starts from a row it is given', () => {
    const rung = ring(plainBobMinor(), '.....', { start: rounds(6) });
    expect(rung.block.first().isRounds()).toBe(true);
  });

  it('has a ceiling on how long a touch may run', () => {
    expect(MAX_LEADS).toBeGreaterThan(100);
  });
});
