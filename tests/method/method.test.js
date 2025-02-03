import { describe, it, expect } from 'vitest';
import { Method, method } from '../../src/method/method.js';
import { rounds } from '../../src/core/row.js';
import { parseRow } from '../../src/core/rowParse.js';
import { ValidationError } from '../../src/errors.js';

const plainBobMinor = () => method('Plain Bob', 6, 'x1x1x1,2');
const cambridgeMinor = () => method('Cambridge Surprise', 6, 'x36x14x12x36x14x56,12');
const grandsireDoubles = () => method('Grandsire', 5, '3,1.5.1.5.1');

describe('Method construction', () => {
  it('expands the notation into a lead', () => {
    const bob = plainBobMinor();
    expect(bob.leadLength).toBe(12);
    expect(bob.stage).toBe(6);
    expect(bob.name).toBe('Plain Bob');
  });

  it('is frozen', () => {
    const bob = plainBobMinor();
    expect(Object.isFrozen(bob)).toBe(true);
    expect(Object.isFrozen(bob.changes)).toBe(true);
  });

  it('trims the name', () => {
    expect(method('  Plain Bob  ', 6, 'x1x1x1,2').name).toBe('Plain Bob');
  });

  it('keeps the notation the author wrote', () => {
    expect(plainBobMinor().source).toBe('x1x1x1,2');
  });

  it('refuses a definition that is not an object', () => {
    expect(() => new Method('Plain Bob')).toThrow('a method needs a definition object');
    expect(() => new Method(null)).toThrow(ValidationError);
  });

  it('refuses a missing name or notation', () => {
    expect(() => method('', 6, 'x1x1x1,2')).toThrow(/a method name must be a non empty string/);
    expect(() => method('Plain Bob', 6, '')).toThrow(/place notation must be a non empty string/);
  });

  it('refuses an unsupported stage', () => {
    expect(() => method('Plain Bob', 2, 'x1')).toThrow('stage 2 is not supported');
  });

  it('passes a notation error straight through', () => {
    expect(() => method('Nonsense', 6, 'x1?x1')).toThrow(/not part of place notation/);
  });
});

describe('method notation', () => {
  it('writes the notation back in the house style', () => {
    expect(plainBobMinor().notation).toBe('x16x16x16,12');
    expect(grandsireDoubles().notation).toBe('3,1.5.1.5.1');
  });

  it('writes the notation out in full when asked', () => {
    expect(plainBobMinor().fullNotation).toBe('x16x16x16x16x16x12');
  });

  it('names the stage', () => {
    expect(plainBobMinor().stageName).toBe('Minor');
    expect(grandsireDoubles().stageName).toBe('Doubles');
  });

  it('prints itself', () => {
    expect(plainBobMinor().toString()).toBe('Plain Bob (Minor) x16x16x16,12');
  });
});

describe('ringing a lead', () => {
  it('rings a plain lead from rounds', () => {
    const lead = plainBobMinor().plainLead();
    expect(lead.length).toBe(13);
    expect(lead.first().toString()).toBe('123456');
    expect(lead.last().toString()).toBe('135264');
  });

  it('rings a plain lead from anywhere', () => {
    const bob = plainBobMinor();
    const lead = bob.plainLead(parseRow('135264'));
    expect(lead.first().toString()).toBe('135264');
    expect(lead.last().toString()).toBe('156342');
  });

  it('gives the lead head and the lead end', () => {
    const bob = plainBobMinor();
    expect(bob.leadHead().toString()).toBe('135264');
    expect(bob.leadEnd().toString()).toBe('132546');
  });

  it('gives the lead head of cambridge surprise minor', () => {
    expect(cambridgeMinor().leadHead().toString()).toBe('156342');
    expect(cambridgeMinor().leadLength).toBe(24);
  });

  it('gives the lead head of grandsire doubles', () => {
    expect(grandsireDoubles().leadHead().toString()).toBe('12534');
    expect(grandsireDoubles().leadLength).toBe(10);
  });

  it('names the change a call would replace', () => {
    expect(plainBobMinor().leadEndChange.toString()).toBe('12');
    expect(cambridgeMinor().leadEndChange.toString()).toBe('12');
  });

  it('refuses a starting row at the wrong stage', () => {
    expect(() => plainBobMinor().plainLead(rounds(8))).toThrow(
      'a method on 6 cannot start from a row of 8',
    );
  });
});

describe('ringsTheSameAs', () => {
  it('sees through a different name', () => {
    expect(plainBobMinor().ringsTheSameAs(method('Anything', 6, 'x1x1x1,2'))).toBe(true);
  });

  it('sees through a different spelling of the same notation', () => {
    expect(plainBobMinor().ringsTheSameAs(method('Plain Bob', 6, '-16-16-16-16-16-12'))).toBe(true);
  });

  it('tells two different methods apart', () => {
    expect(plainBobMinor().ringsTheSameAs(cambridgeMinor())).toBe(false);
  });

  it('tells two stages apart', () => {
    expect(plainBobMinor().ringsTheSameAs(method('Plain Bob', 8, 'x18x18x18x18,12'))).toBe(false);
  });

  it('is false for anything that is not a method', () => {
    expect(plainBobMinor().ringsTheSameAs('x1x1x1,2')).toBe(false);
  });
});
