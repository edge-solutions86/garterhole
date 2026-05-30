import { describe, it, expect } from 'vitest';
import {
  KINDS,
  kindOf,
  readFootnote,
  namesIn,
  readAll,
  firstFootnote,
  groupByKind,
} from '../../src/records/footnote.js';
import { ValidationError } from '../../src/errors.js';

describe('kindOf', () => {
  it('recognises a first', () => {
    expect(kindOf('First quarter peal of Minor for Alice Vane.')).toBe('first');
    expect(kindOf('1st on eight for Bob Reed.')).toBe('first');
  });

  it('recognises a birthday', () => {
    expect(kindOf('Rung for the 80th birthday of Carol Sims.')).toBe('birthday');
  });

  it('recognises a memorial', () => {
    expect(kindOf('Rung in memory of Dan Ellis.')).toBe('memorial');
    expect(kindOf('In memoriam.')).toBe('memorial');
  });

  it('recognises a wedding', () => {
    expect(kindOf('Rung for the wedding of Erin and Frank.')).toBe('wedding');
  });

  it('calls anything else other', () => {
    expect(kindOf('Rung half muffled.')).toBe('other');
  });

  it('lists the kinds it knows', () => {
    expect(KINDS).toContain('first');
    expect(Object.isFrozen(KINDS)).toBe(true);
  });
});

describe('readFootnote', () => {
  it('keeps the words and adds the structure', () => {
    const read = readFootnote('  First quarter peal for Alice Vane.  ');
    expect(read.text).toBe('First quarter peal for Alice Vane.');
    expect(read.kind).toBe('first');
    expect(read.names).toContain('Alice Vane');
  });

  it('refuses a blank footnote', () => {
    expect(() => readFootnote('')).toThrow(ValidationError);
    expect(() => readFootnote('   ')).toThrow(/cannot be blank/);
  });
});

describe('namesIn', () => {
  it('finds a name in the middle of a sentence', () => {
    expect(namesIn('Rung in memory of Dan Ellis.')).toEqual(['Dan Ellis']);
  });

  it('finds two names', () => {
    expect(namesIn('Rung for the wedding of Erin Fox and Frank Gale.')).toEqual([
      'Erin Fox',
      'Frank Gale',
    ]);
  });

  it('does not take the first word of a sentence for a name', () => {
    expect(namesIn('Rung half muffled.')).toEqual([]);
  });

  it('finds nothing where there is nothing', () => {
    expect(namesIn('')).toEqual([]);
  });

  it('keeps an apostrophe in a name', () => {
    expect(namesIn("Rung for Sarah O'Brien.")).toEqual(["Sarah O'Brien"]);
  });
});

describe('readAll', () => {
  it('reads every footnote', () => {
    const read = readAll(['First quarter for Alice.', 'Rung half muffled.']);
    expect(read).toHaveLength(2);
    expect(read.map((entry) => entry.kind)).toEqual(['first', 'other']);
  });

  it('reads nothing from nothing', () => {
    expect(readAll([])).toEqual([]);
  });
});

describe('firstFootnote', () => {
  it('writes the sentence a form would offer', () => {
    expect(firstFootnote('Alice Vane', 'quarter peal of Minor')).toBe(
      'First quarter peal of Minor for Alice Vane.',
    );
  });

  it('is recognised by the reader it was written for', () => {
    expect(kindOf(firstFootnote('Alice', 'peal'))).toBe('first');
  });

  it('refuses a first with something missing', () => {
    expect(() => firstFootnote('', 'peal')).toThrow(/needs somebody and something/);
    expect(() => firstFootnote('Alice', '')).toThrow(ValidationError);
  });
});

describe('groupByKind', () => {
  it('puts the firsts together', () => {
    const groups = groupByKind([
      'First quarter for Alice.',
      'First peal for Bob.',
      'Rung half muffled.',
    ]);
    expect(groups.get('first')).toHaveLength(2);
    expect(groups.get('other')).toHaveLength(1);
  });

  it('gives an empty map for nothing', () => {
    expect(groupByKind([]).size).toBe(0);
  });
});
