import { describe, it, expect } from 'vitest';
import {
  lexTouch,
  usesCallingPositions,
  assertBracketsBalance,
  renderTouchTokens,
  CALL,
  POSITION,
  NUMBER,
  OPEN,
  CLOSE,
  PART,
} from '../../src/composition/touchLexer.js';
import { NotationError } from '../../src/errors.js';

const types = (text) => lexTouch(text).map((token) => token.type);

describe('lexTouch', () => {
  it('reads call symbols', () => {
    expect(types('-s.')).toEqual([CALL, CALL, CALL]);
  });

  it('reads calling positions', () => {
    expect(types('WMH')).toEqual([POSITION, POSITION, POSITION]);
  });

  it('reads a single at a calling position as two tokens', () => {
    const tokens = lexTouch('sH');
    expect(tokens.map((token) => token.type)).toEqual([CALL, POSITION]);
    expect(tokens.map((token) => token.text)).toEqual(['s', 'H']);
  });

  it('reads repeat counts as whole numbers', () => {
    const tokens = lexTouch('12(WH)');
    expect(tokens[0]).toEqual({ type: NUMBER, text: '12', position: 1 });
    expect(tokens[1].type).toBe(OPEN);
  });

  it('reads brackets and commas', () => {
    expect(types('(W),(H)')).toEqual([OPEN, POSITION, CLOSE, PART, OPEN, POSITION, CLOSE]);
  });

  it('skips whitespace but keeps the positions honest', () => {
    const tokens = lexTouch('W  H');
    expect(tokens.map((token) => token.position)).toEqual([1, 4]);
  });

  it('gives nothing for empty input', () => {
    expect(lexTouch('')).toEqual([]);
    expect(lexTouch('   ')).toEqual([]);
  });

  it('refuses a character it does not know', () => {
    expect(() => lexTouch('W*H')).toThrow(NotationError);
    expect(() => lexTouch('W*H')).toThrow('"*" at position 2 is not part of a touch');
  });

  it('refuses input that is not a string', () => {
    expect(() => lexTouch(null)).toThrow(/must be a string/);
  });
});

describe('usesCallingPositions', () => {
  it('is true when an upper case letter appears', () => {
    expect(usesCallingPositions(lexTouch('WsH'))).toBe(true);
  });

  it('is false for a touch written as one symbol per lead', () => {
    expect(usesCallingPositions(lexTouch('-.-.s-'))).toBe(false);
  });

  it('is false for an empty touch', () => {
    expect(usesCallingPositions([])).toBe(false);
  });
});

describe('assertBracketsBalance', () => {
  it('accepts balanced brackets', () => {
    expect(() => assertBracketsBalance(lexTouch('3(W(H))'))).not.toThrow();
  });

  it('accepts a touch with no brackets', () => {
    expect(() => assertBracketsBalance(lexTouch('WH'))).not.toThrow();
  });

  it('names the position of a stray closing bracket', () => {
    expect(() => assertBracketsBalance(lexTouch('W)H'))).toThrow(
      'a bracket closes at position 2 without opening',
    );
  });

  it('names the position of a bracket that never closes', () => {
    expect(() => assertBracketsBalance(lexTouch('3(WH'))).toThrow(
      'the bracket at position 2 is never closed',
    );
  });
});

describe('renderTouchTokens', () => {
  it('writes the tokens back without the whitespace', () => {
    expect(renderTouchTokens(lexTouch(' 3 (W s H) '))).toBe('3(WsH)');
  });

  it('gives an empty string for no tokens', () => {
    expect(renderTouchTokens([])).toBe('');
  });
});
