import { describe, it, expect } from 'vitest';
import {
  splitRows,
  splitRowsLeniently,
  trailingBlows,
  rowDurations,
  handstrokes,
} from '../../src/striking/rowSplitter.js';
import { StrikeStream } from '../../src/striking/stream.js';
import { plainCourse } from '../../src/method/plainCourse.js';
import { method } from '../../src/method/method.js';
import { ValidationError } from '../../src/errors.js';

/**
 * Turn a block of rows into a perfectly struck stream.
 *
 * @param {import('../../src/core/rowBlock.js').RowBlock} block
 * @param {number} [gap]
 */
function struck(block, gap = 250) {
  const strikes = [];
  let at = 0;
  for (const row of block.rows) {
    for (const bell of row.bells) {
      strikes.push({ bell, at });
      at += gap;
    }
  }
  return new StrikeStream(block.stage, strikes);
}

const plainBobMinor = () => method('Plain', 6, 'x1x1x1,2');

describe('splitRows', () => {
  it('gives one row per run of blows', () => {
    const course = plainCourse(plainBobMinor());
    const rows = splitRows(struck(course));
    expect(rows).toHaveLength(61);
    expect(rows[0].row.toString()).toBe('123456');
    expect(rows[1].row.toString()).toBe('214365');
  });

  it('records where each row started and finished', () => {
    const rows = splitRows(struck(plainBobMinor().plainLead()));
    expect(rows[0].start).toBe(0);
    expect(rows[0].end).toBe(1250);
    expect(rows[1].start).toBe(1500);
  });

  it('numbers the rows from zero', () => {
    const rows = splitRows(struck(plainBobMinor().plainLead()));
    expect(rows.map((row) => row.index).slice(0, 3)).toEqual([0, 1, 2]);
  });

  it('ignores a part row at the end', () => {
    const stream = struck(plainBobMinor().plainLead());
    const short = new StrikeStream(6, stream.strikes.slice(0, -3));
    expect(splitRows(short)).toHaveLength(12);
  });

  it('refuses a run of blows that is not a permutation', () => {
    const stream = new StrikeStream(4, [
      { bell: 1, at: 0 },
      { bell: 2, at: 250 },
      { bell: 1, at: 500 },
      { bell: 4, at: 750 },
    ]);
    expect(() => splitRows(stream)).toThrow(
      'row 0 is not a permutation: bell 1 appears more than once',
    );
  });

  it('refuses something that is not a stream', () => {
    expect(() => splitRows([])).toThrow(ValidationError);
  });
});

describe('splitRowsLeniently', () => {
  it('reports nothing wrong with a clean stream', () => {
    const result = splitRowsLeniently(struck(plainBobMinor().plainLead()));
    expect(result.stoppedAt).toBeNull();
    expect(result.reason).toBeNull();
    expect(result.rows).toHaveLength(13);
  });

  it('keeps the rows before the bad one', () => {
    const good = struck(plainBobMinor().plainLead()).strikes.slice(0, 12);
    const stream = new StrikeStream(6, [
      ...good,
      { bell: 1, at: 9000 },
      { bell: 1, at: 9250 },
      { bell: 3, at: 9500 },
      { bell: 4, at: 9750 },
      { bell: 5, at: 10000 },
      { bell: 6, at: 10250 },
    ]);
    const result = splitRowsLeniently(stream);
    expect(result.rows).toHaveLength(2);
    expect(result.stoppedAt).toBe(2);
    expect(result.reason).toMatch(/not a permutation/);
  });

  it('refuses something that is not a stream', () => {
    expect(() => splitRowsLeniently(null)).toThrow(ValidationError);
  });
});

describe('trailingBlows', () => {
  it('counts nothing when the stream divides evenly', () => {
    expect(trailingBlows(struck(plainBobMinor().plainLead()))).toBe(0);
  });

  it('counts the blows of a part row', () => {
    const stream = struck(plainBobMinor().plainLead());
    expect(trailingBlows(new StrikeStream(6, stream.strikes.slice(0, -2)))).toBe(4);
  });
});

describe('rowDurations', () => {
  it('measures from one row to the next', () => {
    const rows = splitRows(struck(plainBobMinor().plainLead()));
    expect(rowDurations(rows)).toHaveLength(12);
    expect(new Set(rowDurations(rows))).toEqual(new Set([1500]));
  });

  it('gives nothing for a single row', () => {
    expect(rowDurations([{ start: 0 }])).toEqual([]);
  });
});

describe('handstrokes', () => {
  it('alternates from the first row', () => {
    const rows = splitRows(struck(plainBobMinor().plainLead()));
    expect(handstrokes(rows).slice(0, 4)).toEqual([true, false, true, false]);
  });
});
