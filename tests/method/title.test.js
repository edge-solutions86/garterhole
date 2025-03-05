import { describe, it, expect } from 'vitest';
import { buildTitle, parseTitle, titleOf, isWellFormedTitle } from '../../src/method/title.js';
import { method } from '../../src/method/method.js';
import { ValidationError } from '../../src/errors.js';

describe('buildTitle', () => {
  it('puts the name, the class and the stage together', () => {
    expect(buildTitle({ name: 'Cambridge', className: 'Surprise', stage: 8 })).toBe(
      'Cambridge Surprise Major',
    );
  });

  it('leaves the class out for a principle', () => {
    expect(buildTitle({ name: 'Stedman', className: 'Principle', stage: 7 })).toBe(
      'Stedman Triples',
    );
    expect(buildTitle({ name: 'Stedman', stage: 7 })).toBe('Stedman Triples');
  });

  it('allows a title with a class but no name', () => {
    expect(buildTitle({ name: '', className: 'Little Bob', stage: 8 })).toBe('Little Bob Major');
  });

  it('squashes stray whitespace', () => {
    expect(buildTitle({ name: '  Plain  ', className: ' Bob ', stage: 6 })).toBe('Plain Bob Minor');
  });

  it('refuses a title with neither a name nor a class', () => {
    expect(() => buildTitle({ name: '', stage: 8 })).toThrow(ValidationError);
    expect(() => buildTitle({ name: '  ', className: 'Principle', stage: 8 })).toThrow(
      /needs a name or a class/,
    );
  });

  it('refuses a stage that has no name', () => {
    expect(() => buildTitle({ name: 'Nonsense', stage: 2 })).toThrow(/no stage name for 2/);
  });
});

describe('parseTitle', () => {
  it('reads an ordinary title', () => {
    expect(parseTitle('Cambridge Surprise Major')).toEqual({
      name: 'Cambridge',
      className: 'Surprise',
      stage: 8,
    });
  });

  it('reads a class word that appears in the middle of the name', () => {
    expect(parseTitle('Double Norwich Court Bob Major')).toEqual({
      name: 'Double Norwich Court',
      className: 'Bob',
      stage: 8,
    });
  });

  it('reads a two word class', () => {
    expect(parseTitle('Kent Treble Bob Major')).toEqual({
      name: 'Kent',
      className: 'Treble Bob',
      stage: 8,
    });
  });

  it('reads the modifiers in front of the class', () => {
    expect(parseTitle('Little Bob Major')).toEqual({
      name: '',
      className: 'Little Bob',
      stage: 8,
    });
    expect(parseTitle('Fenchurch Differential Little Place Royal').className).toBe(
      'Differential Little Place',
    );
  });

  it('calls a title with no class a principle', () => {
    expect(parseTitle('Stedman Triples')).toEqual({
      name: 'Stedman',
      className: 'Principle',
      stage: 7,
    });
  });

  it('squashes whitespace before reading', () => {
    expect(parseTitle('  Cambridge   Surprise  Major ').name).toBe('Cambridge');
  });

  it('refuses a blank title', () => {
    expect(() => parseTitle('')).toThrow('a title cannot be blank');
    expect(() => parseTitle('   ')).toThrow(ValidationError);
  });

  it('refuses a title that does not end with a stage', () => {
    expect(() => parseTitle('Cambridge Surprise')).toThrow(/does not end with the name of a stage/);
  });

  it('refuses a title that is only a stage name', () => {
    expect(() => parseTitle('Major')).toThrow(/nothing but a stage name/);
  });
});

describe('isWellFormedTitle', () => {
  it('accepts the titles it can read', () => {
    expect(isWellFormedTitle('Cambridge Surprise Major')).toBe(true);
    expect(isWellFormedTitle('Stedman Triples')).toBe(true);
  });

  it('rejects the ones it cannot', () => {
    expect(isWellFormedTitle('Cambridge Surprise')).toBe(false);
    expect(isWellFormedTitle('')).toBe(false);
  });
});

describe('titleOf', () => {
  it('works the class out from the changes rather than the name', () => {
    expect(titleOf(method('Cambridge', 8, 'x38x14x1258x36x14x58x16x78,12'))).toBe(
      'Cambridge Surprise Major',
    );
    expect(titleOf(method('Plain', 6, 'x1x1x1,2'))).toBe('Plain Bob Minor');
    expect(titleOf(method('Bristol', 8, 'x58x14.58x58.36.14x14.58x14x18,18'))).toBe(
      'Bristol Surprise Major',
    );
  });

  it('leaves the class out of a principle title', () => {
    expect(titleOf(method('Test', 5, '3.1.5.3.1.3.5.1.3.1'))).toBe('Test Doubles');
  });

  it('round trips through the reader', () => {
    const title = titleOf(method('Yorkshire', 8, 'x38x14x58x16x12x38x14x78,12'));
    expect(parseTitle(title)).toEqual({ name: 'Yorkshire', className: 'Surprise', stage: 8 });
  });
});
