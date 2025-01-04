import { describe, it, expect } from 'vitest';
import { RowBlock, changeBetween } from '../../src/core/rowBlock.js';
import { rounds } from '../../src/core/row.js';
import { parseRow } from '../../src/core/rowParse.js';
import { expandNotation } from '../../src/notation/expand.js';
import { ValidationError } from '../../src/errors.js';

const PLAIN_BOB_MINOR = expandNotation('x1x1x1,2', 6);

describe('RowBlock construction', () => {
  it('rings a lead from a starting row', () => {
    const block = RowBlock.fromChanges(rounds(6), PLAIN_BOB_MINOR);
    expect(block.length).toBe(13);
    expect(block.changeCount).toBe(12);
    expect(block.first().toString()).toBe('123456');
    expect(block.last().toString()).toBe('135264');
  });

  it('is frozen', () => {
    const block = RowBlock.fromChanges(rounds(6), PLAIN_BOB_MINOR);
    expect(Object.isFrozen(block)).toBe(true);
    expect(Object.isFrozen(block.rows)).toBe(true);
  });

  it('accepts a single row', () => {
    const block = new RowBlock([rounds(8)]);
    expect(block.length).toBe(1);
    expect(block.changeCount).toBe(0);
  });

  it('refuses an empty block', () => {
    expect(() => new RowBlock([])).toThrow(ValidationError);
    expect(() => new RowBlock([])).toThrow('a block must contain at least one row');
  });

  it('refuses rows at mixed stages', () => {
    expect(() => new RowBlock([rounds(6), rounds(8)])).toThrow(
      'row 1 is on 8 bells but the block is on 6',
    );
  });

  it('refuses an entry that is not a row', () => {
    expect(() => new RowBlock([rounds(6), '214365'])).toThrow('entry 1 of the block is not a row');
  });

  it('refuses to start from something that is not a row', () => {
    expect(() => RowBlock.fromChanges('123456', PLAIN_BOB_MINOR)).toThrow(
      'a block starts from a row',
    );
  });
});

describe('reading a block', () => {
  const block = RowBlock.fromChanges(rounds(6), PLAIN_BOB_MINOR);

  it('reads a row by index', () => {
    expect(block.at(0).toString()).toBe('123456');
    expect(block.at(1).toString()).toBe('214365');
    expect(block.at(12).toString()).toBe('135264');
  });

  it('refuses an index outside the block', () => {
    expect(() => block.at(13)).toThrow(/outside 0\.\.12/);
    expect(() => block.at(-1)).toThrow(RangeError);
  });

  it('iterates', () => {
    expect([...block].map(String)[1]).toBe('214365');
    expect([...block]).toHaveLength(13);
  });

  it('says whether it holds a row', () => {
    expect(block.contains(parseRow('214365'))).toBe(true);
    expect(block.contains(parseRow('123465'))).toBe(false);
  });

  it('slices into a smaller block', () => {
    const half = block.slice(0, 7);
    expect(half.length).toBe(7);
    expect(half.first().toString()).toBe('123456');
  });
});

describe('block properties', () => {
  it('finds a plain lead true', () => {
    expect(RowBlock.fromChanges(rounds(6), PLAIN_BOB_MINOR).isTrue()).toBe(true);
  });

  it('finds a repeated row', () => {
    const block = new RowBlock([rounds(4), parseRow('2143'), rounds(4)]);
    expect(block.isTrue()).toBe(false);
  });

  it('says whether the block comes round', () => {
    let block = RowBlock.fromChanges(rounds(6), PLAIN_BOB_MINOR);
    expect(block.comesRound()).toBe(false);
    for (let lead = 1; lead < 5; lead += 1) {
      block = block.concat(RowBlock.fromChanges(block.last(), PLAIN_BOB_MINOR));
    }
    expect(block.comesRound()).toBe(true);
    expect(block.length).toBe(61);
    expect(block.isTrue()).toBe(false);
  });

  it('recovers the changes it was rung from', () => {
    const block = RowBlock.fromChanges(rounds(6), PLAIN_BOB_MINOR);
    expect(block.changes().map(String)).toEqual(PLAIN_BOB_MINOR.map(String));
  });
});

describe('joining blocks', () => {
  it('joins on the shared row', () => {
    const first = RowBlock.fromChanges(rounds(6), PLAIN_BOB_MINOR);
    const second = RowBlock.fromChanges(first.last(), PLAIN_BOB_MINOR);
    const joined = first.concat(second);
    expect(joined.length).toBe(25);
    expect(joined.at(12).equals(first.last())).toBe(true);
  });

  it('refuses to join blocks that do not meet', () => {
    const first = RowBlock.fromChanges(rounds(6), PLAIN_BOB_MINOR);
    const second = RowBlock.fromChanges(rounds(6), PLAIN_BOB_MINOR);
    expect(() => first.concat(second)).toThrow(/block ends on 135264 but the next one starts on/);
  });

  it('refuses something that is not a block', () => {
    const first = RowBlock.fromChanges(rounds(6), PLAIN_BOB_MINOR);
    expect(() => first.concat([rounds(6)])).toThrow('a block is joined to another block');
  });
});

describe('changeBetween', () => {
  it('finds a cross change', () => {
    expect(changeBetween(rounds(6), parseRow('214365')).toString()).toBe('x');
  });

  it('finds the places made', () => {
    expect(changeBetween(parseRow('214365'), parseRow('241635')).toString()).toBe('16');
  });

  it('gives every place for two identical rows', () => {
    expect(changeBetween(rounds(6), rounds(6)).toString()).toBe('123456');
  });

  it('refuses two rows that are not one change apart', () => {
    expect(() => changeBetween(rounds(6), parseRow('231456'))).toThrow(/are not one change apart/);
  });

  it('refuses rows at different stages', () => {
    expect(() => changeBetween(rounds(6), rounds(8))).toThrow('cannot change from 6 bells to 8');
  });
});
