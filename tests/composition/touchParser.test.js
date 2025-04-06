import { describe, it, expect } from 'vitest';
import {
  parseTouch,
  leadCount,
  callsIn,
  DEFAULT_CALL_SYMBOL,
  PLAIN_SYMBOL,
} from '../../src/composition/touchParser.js';
import { NotationError } from '../../src/errors.js';

const shape = (text) =>
  parseTouch(text).instructions.map((instruction) =>
    instruction.position === undefined
      ? instruction.symbol
      : `${instruction.symbol}${instruction.position}`,
  );

describe('touches written as one symbol per lead', () => {
  it('reads a symbol for every lead', () => {
    const touch = parseTouch('-.-.s-');
    expect(touch.style).toBe('symbols');
    expect(shape('-.-.s-')).toEqual(['-', '.', '-', '.', 's', '-']);
  });

  it('counts the leads', () => {
    expect(leadCount(parseTouch('-.-.s-'))).toBe(6);
  });

  it('leaves the plain leads out of the calls', () => {
    expect(callsIn(parseTouch('-.-.s-'))).toHaveLength(4);
  });

  it('keeps the source', () => {
    expect(parseTouch('-.-').source).toBe('-.-');
  });

  it('reads an empty touch', () => {
    const touch = parseTouch('');
    expect(touch.instructions).toEqual([]);
    expect(leadCount(touch)).toBe(0);
  });
});

describe('touches written as calling positions', () => {
  it('reads a position with no symbol as a bob', () => {
    expect(shape('WMH')).toEqual([`${DEFAULT_CALL_SYMBOL}W`, '-M', '-H']);
  });

  it('reads a symbol in front of a position', () => {
    expect(shape('WsH')).toEqual(['-W', 'sH']);
  });

  it('says the style is positions', () => {
    expect(parseTouch('WsH').style).toBe('positions');
  });

  it('has no lead count until it is rung', () => {
    expect(leadCount(parseTouch('WsH'))).toBeNull();
  });

  it('refuses a call with no position after it', () => {
    expect(() => parseTouch('Ws')).toThrow(/has no calling position after it/);
    expect(() => parseTouch('s-H')).toThrow(NotationError);
  });
});

describe('repeats', () => {
  it('expands a count in front of a group', () => {
    expect(shape('3(WH)')).toEqual(['-W', '-H', '-W', '-H', '-W', '-H']);
  });

  it('expands a count after a group', () => {
    expect(shape('(WH)2')).toEqual(['-W', '-H', '-W', '-H']);
  });

  it('expands a group with no count once', () => {
    expect(shape('(WH)')).toEqual(['-W', '-H']);
  });

  it('expands nested groups', () => {
    expect(shape('2(W2(H))')).toEqual(['-W', '-H', '-H', '-W', '-H', '-H']);
  });

  it('expands repeats in the symbol style too', () => {
    expect(shape('3(-.)')).toEqual(['-', '.', '-', '.', '-', '.']);
  });

  it('copies rather than sharing the repeated instructions', () => {
    const touch = parseTouch('2(W)');
    expect(touch.instructions[0]).not.toBe(touch.instructions[1]);
  });

  it('refuses a repeat with nothing to repeat', () => {
    expect(() => parseTouch('3W')).toThrow(/has nothing to repeat/);
  });

  it('refuses a repeat count of zero', () => {
    expect(() => parseTouch('0(W)')).toThrow(/rings nothing/);
  });
});

describe('parts and brackets', () => {
  it('treats a comma as a separator and nothing more', () => {
    expect(shape('W,H')).toEqual(['-W', '-H']);
  });

  it('allows a comma inside a group', () => {
    expect(shape('2(W,H)')).toEqual(['-W', '-H', '-W', '-H']);
  });

  it('refuses a stray closing bracket', () => {
    expect(() => parseTouch('W)')).toThrow(/without opening/);
  });

  it('refuses a bracket that never closes', () => {
    expect(() => parseTouch('2(WH')).toThrow(/never closed/);
  });
});

describe('the plain symbol', () => {
  it('is the full stop', () => {
    expect(PLAIN_SYMBOL).toBe('.');
    expect(callsIn(parseTouch('..-..')).map((call) => call.symbol)).toEqual(['-']);
  });
});
