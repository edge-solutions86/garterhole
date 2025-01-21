import { describe, it, expect } from 'vitest';
import {
  padLeft,
  padRight,
  tabulate,
  plural,
  joinWords,
  truncate,
  slugify,
  squash,
  wrap,
} from '../../src/util/text.js';

describe('padding', () => {
  it('pads to the width', () => {
    expect(padRight('ab', 5)).toBe('ab   ');
    expect(padLeft('ab', 5)).toBe('   ab');
  });

  it('leaves text that is already wide enough', () => {
    expect(padRight('abcdef', 3)).toBe('abcdef');
    expect(padLeft('abcdef', 3)).toBe('abcdef');
  });
});

describe('tabulate', () => {
  it('lines the columns up', () => {
    const lines = tabulate([
      ['Bell', 'Weight'],
      ['Treble', '4'],
      ['Tenor', '19'],
    ]);
    expect(lines).toEqual(['Bell    Weight', 'Treble  4', 'Tenor   19']);
  });

  it('right aligns the columns it is told to', () => {
    const lines = tabulate(
      [
        ['a', '1'],
        ['bb', '22'],
      ],
      { align: ['left', 'right'] },
    );
    expect(lines).toEqual(['a    1', 'bb  22']);
  });

  it('pads out a short row', () => {
    const lines = tabulate([['a', 'b'], ['c']]);
    expect(lines).toEqual(['a  b', 'c']);
  });

  it('takes a different gap', () => {
    expect(tabulate([['a', 'b']], { gap: ' | ' })).toEqual(['a | b']);
  });

  it('gives nothing for no rows', () => {
    expect(tabulate([])).toEqual([]);
  });
});

describe('plural', () => {
  it('uses the singular for one', () => {
    expect(plural(1, 'bell')).toBe('1 bell');
  });

  it('adds an s for anything else', () => {
    expect(plural(0, 'bell')).toBe('0 bells');
    expect(plural(8, 'bell')).toBe('8 bells');
  });

  it('takes an irregular plural', () => {
    expect(plural(2, 'belfry', 'belfries')).toBe('2 belfries');
  });
});

describe('joinWords', () => {
  it('joins with commas and a final and', () => {
    expect(joinWords(['a', 'b', 'c'])).toBe('a, b and c');
  });

  it('joins two with just an and', () => {
    expect(joinWords(['a', 'b'])).toBe('a and b');
  });

  it('leaves one alone and gives nothing for none', () => {
    expect(joinWords(['a'])).toBe('a');
    expect(joinWords([])).toBe('');
  });
});

describe('truncate', () => {
  it('leaves short text alone', () => {
    expect(truncate('abc', 5)).toBe('abc');
    expect(truncate('abcde', 5)).toBe('abcde');
  });

  it('cuts and marks longer text', () => {
    expect(truncate('abcdef', 5)).toBe('abcd.');
  });

  it('handles a width of one', () => {
    expect(truncate('abc', 1)).toBe('.');
  });

  it('refuses a width below one', () => {
    expect(() => truncate('abc', 0)).toThrow(/at least 1/);
  });
});

describe('slugify', () => {
  it('lower cases and joins with hyphens', () => {
    expect(slugify('Cambridge Surprise Major')).toBe('cambridge-surprise-major');
  });

  it('drops punctuation and leading or trailing hyphens', () => {
    expect(slugify("St Martin's Bob Doubles!")).toBe('st-martin-s-bob-doubles');
    expect(slugify('  spaced  ')).toBe('spaced');
  });

  it('gives an empty string when nothing survives', () => {
    expect(slugify('!!!')).toBe('');
  });
});

describe('squash', () => {
  it('collapses runs of whitespace', () => {
    expect(squash('  a \n b\tc  ')).toBe('a b c');
  });

  it('gives an empty string for whitespace only', () => {
    expect(squash(' \n ')).toBe('');
  });
});

describe('wrap', () => {
  it('wraps at the width without breaking words', () => {
    expect(wrap('the quick brown fox jumps', 10)).toEqual(['the quick', 'brown fox', 'jumps']);
  });

  it('gives a long word a line of its own', () => {
    expect(wrap('a supercalifragilistic b', 5)).toEqual(['a', 'supercalifragilistic', 'b']);
  });

  it('gives nothing for empty text', () => {
    expect(wrap('   ', 10)).toEqual([]);
  });

  it('refuses a width below one', () => {
    expect(() => wrap('abc', 0)).toThrow(RangeError);
  });
});
