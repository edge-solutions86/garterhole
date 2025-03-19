import { describe, it, expect } from 'vitest';
import { Call, call } from '../../src/composition/call.js';
import { method } from '../../src/method/method.js';
import { writeChanges } from '../../src/notation/format.js';
import { ValidationError } from '../../src/errors.js';

const plainBobMajor = () => method('Plain', 8, 'x18x18x18x18,12');
const cambridgeMajor = () => method('Cambridge', 8, 'x38x14x1258x36x14x58x16x78,12');
const bob = () => call('Bob', '-', 8, '14');
const single = () => call('Single', 's', 8, '1234');

describe('Call construction', () => {
  it('keeps the name, symbol and changes', () => {
    const called = bob();
    expect(called.name).toBe('Bob');
    expect(called.symbol).toBe('-');
    expect(called.notation).toBe('14');
    expect(called.replaces).toBe(1);
  });

  it('is frozen', () => {
    expect(Object.isFrozen(bob())).toBe(true);
  });

  it('takes a call that replaces more than one change', () => {
    const long = call('Long', 'L', 8, '14.16', 2);
    expect(long.changes).toHaveLength(2);
    expect(long.replaces).toBe(2);
  });

  it('defaults the replaced count to the number of changes it substitutes', () => {
    expect(call('Long', 'L', 8, '14.16').replaces).toBe(2);
  });

  it('refuses a definition that is not an object', () => {
    expect(() => new Call('bob')).toThrow('a call needs a definition object');
  });

  it('refuses a missing name, symbol or notation', () => {
    expect(() => call('', '-', 8, '14')).toThrow(/a call name/);
    expect(() => call('Bob', '', 8, '14')).toThrow(/a call symbol/);
    expect(() => call('Bob', '-', 8, '')).toThrow(/a call notation/);
  });

  it('refuses a replaced count that is not a positive whole number', () => {
    expect(() => call('Bob', '-', 8, '14', 0)).toThrow(/cannot replace 0 changes/);
    expect(() => call('Bob', '-', 8, '14', 1.5)).toThrow(ValidationError);
  });

  it('passes a bad notation on', () => {
    expect(() => call('Bob', '-', 8, '13')).toThrow(/would leave/);
  });

  it('prints itself', () => {
    expect(bob().toString()).toBe('Bob (-) 14');
  });
});

describe('applying a call', () => {
  it('swaps the last change of the lead', () => {
    const changes = bob().applyToLead(plainBobMajor());
    expect(changes).toHaveLength(16);
    expect(writeChanges(changes.slice(-2))).toBe('x14');
  });

  it('leaves the rest of the lead alone', () => {
    const plainChanges = plainBobMajor().changes;
    const changes = bob().applyToLead(plainBobMajor());
    expect(writeChanges(changes.slice(0, 15))).toBe(writeChanges(plainChanges.slice(0, 15)));
  });

  it('works in a surprise method too', () => {
    const changes = single().applyToLead(cambridgeMajor());
    expect(changes).toHaveLength(32);
    expect(changes[31].toString()).toBe('1234');
  });

  it('refuses a method at another stage', () => {
    expect(() => bob().applyToLead(method('Plain', 6, 'x1x1x1,2'))).toThrow(
      'a call on 8 cannot be made in a method on 6',
    );
  });

  it('refuses to replace more changes than the lead has', () => {
    const greedy = call('Greedy', 'g', 8, '14.16.14.16.14.16.14.16.14', 9);
    expect(() => greedy.applyToLead(method('Short', 8, 'x18'))).toThrow(
      /cannot be made in a lead of 2/,
    );
  });

  it('refuses something that is not a method', () => {
    expect(() => bob().applyToLead('Plain Bob')).toThrow('a call is applied to a Method');
  });
});

describe('the plain call', () => {
  it('leaves the lead exactly as it was', () => {
    const plain = Call.plain(8);
    expect(plain.isPlain()).toBe(true);
    expect(writeChanges(plain.applyToLead(cambridgeMajor()))).toBe(cambridgeMajor().fullNotation);
  });

  it('is not the same as a bob', () => {
    expect(bob().isPlain()).toBe(false);
  });

  it('has a name and a symbol like any other call', () => {
    expect(Call.plain(8).name).toBe('Plain');
    expect(Call.plain(8).symbol).toBe('.');
  });

  it('still refuses something that is not a method', () => {
    expect(() => Call.plain(8).applyToLead(null)).toThrow('a call is applied to a Method');
  });
});
