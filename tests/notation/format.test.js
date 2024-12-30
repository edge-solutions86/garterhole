import { describe, it, expect } from 'vitest';
import {
  writeChanges,
  writeFolded,
  foldChanges,
  writeLeadLines,
} from '../../src/notation/format.js';
import { expandNotation, symmetryPoints } from '../../src/notation/expand.js';
import { parseChanges } from '../../src/notation/parser.js';
import { rounds } from '../../src/core/row.js';

/**
 * @param {readonly import('../../src/core/change.js').Change[]} changes
 * @param {number} stage
 */
function ring(changes, stage) {
  const rows = [rounds(stage)];
  for (const change of changes) {
    rows.push(change.apply(rows[rows.length - 1]));
  }
  return rows;
}

describe('writeChanges', () => {
  it('puts no separator next to a cross change', () => {
    expect(writeChanges(parseChanges('x18x18', 8))).toBe('x18x18');
  });

  it('separates two runs of places with a full stop', () => {
    expect(writeChanges(parseChanges('5.1.5.1', 5))).toBe('5.1.5.1');
  });

  it('writes places in full even where the reader would have implied them', () => {
    expect(writeChanges(parseChanges('x1x1', 6))).toBe('x16x16');
  });

  it('gives an empty string for no changes', () => {
    expect(writeChanges([])).toBe('');
  });

  it('round trips through the reader', () => {
    for (const [source, stage] of [
      ['x18x18x18x18', 8],
      ['5.1.5.1.5.1', 5],
      ['x36x14x12x36', 6],
    ]) {
      const changes = parseChanges(source, stage);
      expect(writeChanges(parseChanges(writeChanges(changes), stage))).toBe(writeChanges(changes));
    }
  });
});

describe('foldChanges', () => {
  it('splits plain bob minor at its two axes', () => {
    const changes = expandNotation('x1x1x1,2', 6);
    expect(symmetryPoints(changes)).toEqual([5, 11]);
    const folded = foldChanges(changes);
    expect(writeChanges(folded.first)).toBe('x16x16x16');
    expect(writeChanges(folded.second)).toBe('12');
  });

  it('splits grandsire doubles where the short section comes first', () => {
    const changes = expandNotation('3,1.5.1.5.1', 5);
    const folded = foldChanges(changes);
    expect(writeChanges(folded.first)).toBe('3');
    expect(writeChanges(folded.second)).toBe('1.5.1.5.1');
  });

  it('gives null for a lead with no axis', () => {
    expect(foldChanges(parseChanges('16.14.12', 6))).toBeNull();
  });

  it('gives null for an empty lead', () => {
    expect(foldChanges([])).toBeNull();
  });
});

describe('writeFolded', () => {
  it('reproduces the notation a method collection prints', () => {
    expect(writeFolded(expandNotation('x1x1x1,2', 6))).toBe('x16x16x16,12');
    expect(writeFolded(expandNotation('3,1.5.1.5.1', 5))).toBe('3,1.5.1.5.1');
    expect(writeFolded(expandNotation('x3x4x2x3x4x5,2', 6))).toBe('x36x14x12x36x14x56,12');
  });

  it('falls back to the long form when there is no fold', () => {
    expect(writeFolded(parseChanges('16.14.12', 6))).toBe('16.14.12');
  });

  it('survives a round trip back through the expander', () => {
    for (const [source, stage] of [
      ['x1x1x1,2', 6],
      ['3,1.5.1.5.1', 5],
      ['x3x4x2x3x4x5,2', 6],
    ]) {
      const first = expandNotation(source, stage);
      const second = expandNotation(writeFolded(first), stage);
      expect(writeChanges(second)).toBe(writeChanges(first));
    }
  });
});

describe('writeLeadLines', () => {
  it('prints the starting row and then one line per change', () => {
    const changes = parseChanges('x16x16', 6);
    const lines = writeLeadLines(changes, ring(changes, 6));
    expect(lines).toEqual(['    123456', ' x  214365', '16  241635', ' x  426153', '16  462513']);
  });

  it('pads the notation column to the widest change', () => {
    const changes = parseChanges('x1258x', 8);
    const lines = writeLeadLines(changes, ring(changes, 8));
    expect(lines[0].startsWith('     ')).toBe(true);
    expect(lines[1].startsWith('   x')).toBe(true);
  });

  it('refuses a row count that does not match the changes', () => {
    const changes = parseChanges('x16x16', 6);
    expect(() => writeLeadLines(changes, [rounds(6)])).toThrow(
      'expected 5 rows for 4 changes but got 1',
    );
  });
});
