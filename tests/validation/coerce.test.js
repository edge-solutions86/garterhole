import { describe, it, expect } from 'vitest';
import {
  toNumber,
  toInteger,
  toBoolean,
  toDate,
  toList,
  convert,
} from '../../src/validation/coerce.js';
import { ValidationError } from '../../src/errors.js';

describe('toNumber', () => {
  it('reads a number written as text', () => {
    expect(toNumber('42')).toBe(42);
    expect(toNumber(' 4.5 ')).toBe(4.5);
    expect(toNumber('-3')).toBe(-3);
  });

  it('lets a number through', () => {
    expect(toNumber(42)).toBe(42);
  });

  it('refuses the empty string rather than reading it as zero', () => {
    expect(() => toNumber('')).toThrow(/must be a number/);
    expect(() => toNumber('   ')).toThrow(ValidationError);
  });

  it('refuses text that is not a number', () => {
    expect(() => toNumber('eight', 'the stage')).toThrow('the stage must be a number, not "eight"');
  });

  it('refuses infinity and NaN', () => {
    expect(() => toNumber(Number.POSITIVE_INFINITY)).toThrow(ValidationError);
    expect(() => toNumber(Number.NaN)).toThrow(ValidationError);
  });
});

describe('toInteger', () => {
  it('reads a whole number', () => {
    expect(toInteger('8')).toBe(8);
  });

  it('refuses a fraction', () => {
    expect(() => toInteger('4.5', 'the stage')).toThrow(
      'the stage must be a whole number, not 4.5',
    );
  });
});

describe('toBoolean', () => {
  it('reads the words that mean true', () => {
    for (const word of ['true', 'YES', ' on ', '1']) {
      expect(toBoolean(word)).toBe(true);
    }
  });

  it('reads the words that mean false', () => {
    for (const word of ['false', 'no', 'OFF', '0']) {
      expect(toBoolean(word)).toBe(false);
    }
  });

  it('lets a boolean through', () => {
    expect(toBoolean(false)).toBe(false);
  });

  it('refuses anything else rather than calling it true', () => {
    expect(() => toBoolean('maybe')).toThrow(/must be one of/);
    expect(() => toBoolean('')).toThrow(ValidationError);
  });
});

describe('toDate', () => {
  it('reads a date a form sends', () => {
    expect(toDate('2025-05-18').toISOString()).toBe('2025-05-18T00:00:00.000Z');
  });

  it('copies a date it is given', () => {
    const original = new Date(Date.UTC(2025, 4, 18));
    const copy = toDate(original);
    original.setFullYear(1999);
    expect(copy.getUTCFullYear()).toBe(2025);
  });

  it('refuses a date written any other way', () => {
    expect(() => toDate('18/05/2025', 'the date')).toThrow(
      'the date must be a date like 2025-05-18',
    );
  });

  it('refuses a day that does not exist', () => {
    expect(() => toDate('2025-02-30')).toThrow(/is not a real date/);
  });

  it('refuses a date that is not a date', () => {
    expect(() => toDate(new Date('nonsense'))).toThrow(ValidationError);
  });
});

describe('toList', () => {
  it('splits a comma separated string', () => {
    expect(toList('a,b,c')).toEqual(['a', 'b', 'c']);
  });

  it('trims and drops the blanks', () => {
    expect(toList(' a , , b ')).toEqual(['a', 'b']);
  });

  it('lets a list through', () => {
    expect(toList(['a', 'b'])).toEqual(['a', 'b']);
    expect(toList([1, 2])).toEqual(['1', '2']);
  });

  it('gives an empty list for an empty string', () => {
    expect(toList('')).toEqual([]);
  });

  it('refuses something that is neither', () => {
    expect(() => toList(7)).toThrow(/must be a list or a comma separated string/);
  });
});

describe('convert', () => {
  const conversions = { stage: toInteger, cover: toBoolean, from: toDate };

  it('converts what was sent', () => {
    expect(convert({ stage: '8', cover: 'yes' }, conversions)).toEqual({ stage: 8, cover: true });
  });

  it('leaves out what was not sent', () => {
    expect(convert({}, conversions)).toEqual({});
  });

  it('treats an empty value as not sent', () => {
    expect(convert({ stage: '' }, conversions)).toEqual({});
  });

  it('names the field that could not be converted', () => {
    expect(() => convert({ stage: 'eight' }, conversions)).toThrow('stage must be a number');
  });

  it('refuses something that is not a bag of parameters', () => {
    expect(() => convert(null, conversions)).toThrow(/nothing to convert/);
  });
});
