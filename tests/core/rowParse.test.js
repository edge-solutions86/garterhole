import { describe, it, expect } from 'vitest';
import { parseRow, parseRows, tryParseRow } from '../../src/core/rowParse.js';

describe('parseRow', () => {
  it('reads a row of digits', () => {
    const row = parseRow('13527486');
    expect(row.stage).toBe(8);
    expect(row.toString()).toBe('13527486');
  });

  it('reads the letters used above nine bells', () => {
    expect(parseRow('1234567890ET').stage).toBe(12);
    expect(parseRow('1234567890et').toString()).toBe('1234567890ET');
  });

  it('ignores surrounding whitespace', () => {
    expect(parseRow('  1324  ').toString()).toBe('1324');
  });

  it('names the offending character and its position', () => {
    expect(() => parseRow('132z')).toThrow('"z" at position 4 of "132z" is not a bell');
  });

  it('refuses a row that is not a permutation', () => {
    expect(() => parseRow('1123')).toThrow(/appears more than once/);
    expect(() => parseRow('1235')).toThrow(/outside 1\.\.4/);
  });

  it('refuses blank input', () => {
    expect(() => parseRow('')).toThrow(/a row must be a non empty string/);
    expect(() => parseRow('   ')).toThrow(/must not be blank/);
  });

  it('checks the stage when one is given', () => {
    expect(parseRow('123456', { stage: 6 }).stage).toBe(6);
    expect(() => parseRow('123456', { stage: 8 })).toThrow(
      'row "123456" has 6 bells but the stage is 8',
    );
  });

  it('refuses an unsupported stage', () => {
    expect(() => parseRow('12', { stage: 2 })).toThrow(/stage 2 is not supported/);
  });
});

describe('parseRows', () => {
  it('reads rows separated by whitespace', () => {
    const rows = parseRows('1234 2143 2413');
    expect(rows.map(String)).toEqual(['1234', '2143', '2413']);
  });

  it('reads rows separated by commas and newlines', () => {
    const rows = parseRows('1234,\n2143;2413');
    expect(rows).toHaveLength(3);
  });

  it('gives an empty array for blank input', () => {
    expect(parseRows('')).toEqual([]);
    expect(parseRows('   \n ')).toEqual([]);
  });

  it('takes the stage from the first row when none is given', () => {
    expect(() => parseRows('123456 1234')).toThrow(/has 4 bells but the stage is 6/);
  });

  it('checks every row against an explicit stage', () => {
    expect(() => parseRows('1234 2143', { stage: 6 })).toThrow(/the stage is 6/);
  });

  it('refuses input that is not a string', () => {
    expect(() => parseRows(null)).toThrow(/must be a string/);
  });
});

describe('tryParseRow', () => {
  it('reports success with the row', () => {
    const result = tryParseRow('2143');
    expect(result.ok).toBe(true);
    expect(result.row.toString()).toBe('2143');
  });

  it('reports failure with the message instead of throwing', () => {
    const result = tryParseRow('21q3');
    expect(result.ok).toBe(false);
    expect(result.message).toMatch(/is not a bell/);
  });

  it('passes the stage through', () => {
    expect(tryParseRow('2143', { stage: 6 }).ok).toBe(false);
  });
});
