import { describe, it, expect } from 'vitest';
import { parseNotation, parseChanges, parseSection } from '../../src/notation/parser.js';
import { tokenize } from '../../src/notation/tokenizer.js';
import { NotationError } from '../../src/errors.js';

describe('parseChanges', () => {
  it('reads a run of cross changes and places', () => {
    const changes = parseChanges('x18x18', 8);
    expect(changes.map(String)).toEqual(['x', '18', 'x', '18']);
  });

  it('fills in the places the notation leaves out', () => {
    expect(parseChanges('x1x1', 6).map(String)).toEqual(['x', '16', 'x', '16']);
    expect(parseChanges('x2x2', 8).map(String)).toEqual(['x', '12', 'x', '12']);
  });

  it('reads places separated by full stops', () => {
    expect(parseChanges('5.1.5.1', 5).map(String)).toEqual(['5', '1', '5', '1']);
  });

  it('reads the letters used above nine bells', () => {
    expect(parseChanges('x1Tx1T', 12).map(String)).toEqual(['x', '1T', 'x', '1T']);
  });

  it('accepts a dash for a cross change', () => {
    expect(parseChanges('-18-18', 8).map(String)).toEqual(['x', '18', 'x', '18']);
  });

  it('refuses notation that still has a fold in it', () => {
    expect(() => parseChanges('x18x18,12', 8)).toThrow(/has a fold and has to be expanded/);
  });

  it('refuses an unsupported stage', () => {
    expect(() => parseChanges('x1x1', 2)).toThrow(/stage 2 is not supported/);
  });
});

describe('parseNotation', () => {
  it('keeps the sections a comma divides', () => {
    const parsed = parseNotation('x18x18,12', 8);
    expect(parsed.folded).toBe(true);
    expect(parsed.sections).toHaveLength(2);
    expect(parsed.sections[0].map(String)).toEqual(['x', '18', 'x', '18']);
    expect(parsed.sections[1].map(String)).toEqual(['12']);
  });

  it('reports one section when there is no comma', () => {
    const parsed = parseNotation('x1x1x1', 6);
    expect(parsed.folded).toBe(false);
    expect(parsed.sections).toHaveLength(1);
  });

  it('keeps the source for the error messages', () => {
    expect(parseNotation('x1x1', 6).source).toBe('x1x1');
  });

  it('refuses an empty section', () => {
    expect(() => parseNotation('x1,', 6)).toThrow('section 2 of the notation is empty');
    expect(() => parseNotation(',x1', 6)).toThrow('section 1 of the notation is empty');
    expect(() => parseNotation('', 6)).toThrow('section 1 of the notation is empty');
  });
});

describe('separator rules', () => {
  it('refuses a leading separator', () => {
    expect(() => parseChanges('.x1', 6)).toThrow('place notation cannot start with a separator');
  });

  it('refuses a trailing separator', () => {
    expect(() => parseChanges('x1.', 6)).toThrow('place notation cannot end with a separator');
  });

  it('refuses two separators in a row', () => {
    expect(() => parseChanges('5..1', 5)).toThrow('place notation has two separators in a row');
  });

  it('reports where the offending separator is', () => {
    try {
      parseChanges('5..1', 5);
      throw new Error('should have thrown');
    } catch (error) {
      expect(error).toBeInstanceOf(NotationError);
      expect(error.position).toBe(3);
    }
  });

  it('allows a separator between a cross and a run of places', () => {
    expect(parseChanges('x.18.x.18', 8).map(String)).toEqual(['x', '18', 'x', '18']);
  });
});

describe('bad places', () => {
  it('names the run that could not be made into a change', () => {
    expect(() => parseChanges('x13x', 8)).toThrow(/"13"/);
    expect(() => parseChanges('x13x', 8)).toThrow(/would leave/);
  });

  it('refuses a place above the stage', () => {
    expect(() => parseChanges('x18x', 6)).toThrow(/place 8 is outside 1\.\.6/);
  });

  it('reports the position of the bad run', () => {
    try {
      parseChanges('x18x18x13x18', 8);
      throw new Error('should have thrown');
    } catch (error) {
      expect(error.position).toBe(8);
    }
  });
});

describe('parseSection', () => {
  it('takes tokens directly', () => {
    const changes = parseSection(tokenize('x18'), 8);
    expect(changes.map(String)).toEqual(['x', '18']);
  });

  it('gives nothing for an empty token stream', () => {
    expect(parseSection([], 8)).toEqual([]);
  });
});
