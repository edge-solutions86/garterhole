import { describe, it, expect } from 'vitest';
import {
  tokenize,
  hasFold,
  splitAtFolds,
  renderTokens,
  CROSS,
  PLACES,
  SEPARATOR,
  FOLD,
} from '../../src/notation/tokenizer.js';
import { NotationError } from '../../src/errors.js';

describe('tokenize', () => {
  it('reads a cross change written any of the three ways', () => {
    expect(tokenize('x').map((token) => token.type)).toEqual([CROSS]);
    expect(tokenize('X').map((token) => token.type)).toEqual([CROSS]);
    expect(tokenize('-').map((token) => token.type)).toEqual([CROSS]);
  });

  it('reads a run of places as one token', () => {
    const tokens = tokenize('1258');
    expect(tokens).toHaveLength(1);
    expect(tokens[0]).toEqual({ type: PLACES, text: '1258', position: 1 });
  });

  it('upper cases the letters used for the higher bells', () => {
    expect(tokenize('1et')[0].text).toBe('1ET');
  });

  it('reads separators and folds', () => {
    const types = tokenize('3,1.5.1').map((token) => token.type);
    expect(types).toEqual([PLACES, FOLD, PLACES, SEPARATOR, PLACES, SEPARATOR, PLACES]);
  });

  it('records where each token started', () => {
    const tokens = tokenize('x18x18');
    expect(tokens.map((token) => token.position)).toEqual([1, 2, 4, 5]);
  });

  it('skips whitespace without disturbing the positions', () => {
    const tokens = tokenize('x 18\tx');
    expect(tokens.map((token) => token.type)).toEqual([CROSS, PLACES, CROSS]);
    expect(tokens[1].position).toBe(3);
  });

  it('gives an empty stream for empty input', () => {
    expect(tokenize('')).toEqual([]);
    expect(tokenize('   ')).toEqual([]);
  });

  it('refuses a character that is not notation', () => {
    expect(() => tokenize('x18/x')).toThrow(NotationError);
    expect(() => tokenize('x18/x')).toThrow('"/" at position 4 is not part of place notation');
  });

  it('reports the position of the first bad character only', () => {
    try {
      tokenize('12?34?');
      throw new Error('should have thrown');
    } catch (error) {
      expect(error.position).toBe(3);
      expect(error.source).toBe('12?34?');
    }
  });

  it('refuses input that is not a string', () => {
    expect(() => tokenize(null)).toThrow(/must be a string/);
    expect(() => tokenize(18)).toThrow(TypeError);
  });
});

describe('hasFold', () => {
  it('finds a comma', () => {
    expect(hasFold(tokenize('x18x18,12'))).toBe(true);
  });

  it('reports none when the notation is written out in full', () => {
    expect(hasFold(tokenize('x18x18x18x12'))).toBe(false);
  });
});

describe('splitAtFolds', () => {
  it('gives one section when there is no fold', () => {
    const sections = splitAtFolds(tokenize('x1x1'));
    expect(sections).toHaveLength(1);
    expect(sections[0]).toHaveLength(4);
  });

  it('splits either side of the comma', () => {
    const sections = splitAtFolds(tokenize('x18x18,12'));
    expect(sections).toHaveLength(2);
    expect(renderTokens(sections[0])).toBe('x18x18');
    expect(renderTokens(sections[1])).toBe('12');
  });

  it('keeps an empty section when a fold is at the end', () => {
    const sections = splitAtFolds(tokenize('x1,'));
    expect(sections).toHaveLength(2);
    expect(sections[1]).toEqual([]);
  });
});

describe('renderTokens', () => {
  it('round trips notation that was already canonical', () => {
    for (const source of ['x18x18x18x12', '3,1.5.1.5.1', 'x1x1x1,2', '-38-14-1258,16']) {
      const canonical = source.replaceAll('-', 'x');
      expect(renderTokens(tokenize(source))).toBe(canonical);
    }
  });

  it('drops whitespace', () => {
    expect(renderTokens(tokenize(' x 18 x '))).toBe('x18x');
  });

  it('refuses a token it does not know', () => {
    expect(() => renderTokens([{ type: 'wat', text: '?', position: 1 }])).toThrow(
      /unknown token type wat/,
    );
  });
});
