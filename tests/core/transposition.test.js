import { describe, it, expect } from 'vitest';
import { Row, rounds } from '../../src/core/row.js';
import { parseRow } from '../../src/core/rowParse.js';
import {
  transpose,
  inverse,
  permutationBetween,
  power,
  order,
  cyclicGroup,
} from '../../src/core/transposition.js';

describe('transpose', () => {
  it('leaves a row alone when transposed by rounds', () => {
    const row = parseRow('13527486');
    expect(transpose(row, rounds(8)).toString()).toBe('13527486');
  });

  it('reorders the left row by the places the right row names', () => {
    expect(transpose(parseRow('13524'), parseRow('21345')).toString()).toBe('31524');
  });

  it('applies a plain bob lead head to itself', () => {
    const leadHead = parseRow('13527486');
    expect(transpose(leadHead, leadHead).toString()).toBe('15738264');
  });

  it('refuses rows at different stages', () => {
    expect(() => transpose(rounds(6), rounds(8))).toThrow(
      'cannot combine a row of 6 with a row of 8',
    );
  });

  it('refuses something that is not a row', () => {
    expect(() => transpose('123456', rounds(6))).toThrow(/must be a Row/);
    expect(() => transpose(rounds(6), null)).toThrow(/must be a Row/);
  });
});

describe('inverse', () => {
  it('undoes a row from either side', () => {
    const row = parseRow('13527486');
    expect(transpose(row, inverse(row)).isRounds()).toBe(true);
    expect(transpose(inverse(row), row).isRounds()).toBe(true);
  });

  it('leaves rounds alone', () => {
    expect(inverse(rounds(8)).toString()).toBe('12345678');
  });

  it('is its own inverse', () => {
    const row = parseRow('42135');
    expect(inverse(inverse(row)).equals(row)).toBe(true);
  });

  it('inverts a known row', () => {
    expect(inverse(parseRow('31425')).toString()).toBe('24135');
  });
});

describe('permutationBetween', () => {
  it('finds the row that takes one row to another', () => {
    const from = parseRow('13527486');
    const to = parseRow('17583624');
    const step = permutationBetween(from, to);
    expect(transpose(from, step).equals(to)).toBe(true);
  });

  it('gives rounds when the rows are the same', () => {
    const row = parseRow('654321');
    expect(permutationBetween(row, row).isRounds()).toBe(true);
  });
});

describe('power', () => {
  it('gives rounds for exponent zero', () => {
    expect(power(parseRow('13527486'), 0).isRounds()).toBe(true);
  });

  it('gives the row itself for exponent one', () => {
    const row = parseRow('13527486');
    expect(power(row, 1).equals(row)).toBe(true);
  });

  it('agrees with repeated transposition', () => {
    const row = parseRow('134256');
    let expected = rounds(6);
    for (let i = 0; i < 5; i += 1) {
      expected = transpose(expected, row);
    }
    expect(power(row, 5).equals(expected)).toBe(true);
  });

  it('inverts for a negative exponent', () => {
    const row = parseRow('134256');
    expect(power(row, -1).equals(inverse(row))).toBe(true);
    expect(transpose(power(row, 3), power(row, -3)).isRounds()).toBe(true);
  });

  it('refuses a fractional exponent', () => {
    expect(() => power(rounds(6), 1.5)).toThrow(/whole number/);
  });
});

describe('order', () => {
  it('says rounds has order one', () => {
    expect(order(rounds(8))).toBe(1);
  });

  it('says a single swap has order two', () => {
    expect(order(new Row([2, 1, 3, 4]))).toBe(2);
  });

  it('says a three cycle has order three', () => {
    expect(order(parseRow('23145'))).toBe(3);
  });

  it('takes the least common multiple of the cycle lengths', () => {
    expect(order(parseRow('214536'))).toBe(6);
  });

  it('gives the number of leads in a plain bob major course', () => {
    expect(order(parseRow('13527486'))).toBe(7);
  });

  it('agrees with power', () => {
    const row = parseRow('13572468');
    expect(power(row, order(row)).isRounds()).toBe(true);
  });
});

describe('cyclicGroup', () => {
  it('starts at rounds and has one member per order', () => {
    const row = parseRow('13527486');
    const group = cyclicGroup(row);
    expect(group).toHaveLength(order(row));
    expect(group[0].isRounds()).toBe(true);
  });

  it('has no repeats', () => {
    const group = cyclicGroup(parseRow('214365'));
    const keys = new Set(group.map((member) => member.key()));
    expect(keys.size).toBe(group.length);
    expect(group).toHaveLength(2);
  });
});
