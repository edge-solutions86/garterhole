import { describe, it, expect } from 'vitest';
import { boardLines, summaryLine } from '../../src/records/report.js';
import { performance } from '../../src/records/performance.js';
import { ValidationError } from '../../src/errors.js';

const six = ['Alice', 'Bob', 'Carol', 'Dan', 'Erin', 'Frank'];

const quarter = (overrides = {}) =>
  performance({
    tower: 'St Mary',
    date: new Date(Date.UTC(2025, 4, 18)),
    title: 'Plain Bob Minor',
    changes: 1260,
    band: six,
    conductor: 'Alice',
    ...overrides,
  });

describe('boardLines', () => {
  it('opens with the tower, the date and the length', () => {
    const lines = boardLines(quarter());
    expect(lines[0]).toBe('St Mary');
    expect(lines[1]).toBe('2025-05-18');
    expect(lines[2]).toBe('1260 Plain Bob Minor');
  });

  it('lists the band one bell to a line', () => {
    const lines = boardLines(quarter());
    expect(lines).toContain('1  Alice (c)');
    expect(lines).toContain('6  Frank');
  });

  it('marks the conductor', () => {
    expect(boardLines(quarter()).filter((line) => line.includes('(c)'))).toHaveLength(1);
  });

  it('marks nobody when there is no conductor', () => {
    const lines = boardLines(quarter({ conductor: undefined }));
    expect(lines.some((line) => line.includes('(c)'))).toBe(false);
  });

  it('names the composer when there is one', () => {
    expect(boardLines(quarter({ composer: 'A Composer' }))).toContain('composed by A Composer');
  });

  it('gives the time when it was recorded', () => {
    expect(boardLines(quarter({ duration: 45 }))).toContain('45 minutes');
  });

  it('prints the footnotes at the end', () => {
    const lines = boardLines(quarter({ footnotes: ['First quarter on six.'] }));
    expect(lines[lines.length - 1]).toBe('First quarter on six.');
  });

  it('refuses something that is not a performance', () => {
    expect(() => boardLines({ tower: 'St Mary' })).toThrow(ValidationError);
  });
});

describe('summaryLine', () => {
  it('writes one line for a report', () => {
    expect(summaryLine(quarter())).toBe('2025-05-18  St Mary  1260 Plain Bob Minor (c: Alice)');
  });

  it('leaves the conductor out when there is none', () => {
    expect(summaryLine(quarter({ conductor: undefined }))).toBe(
      '2025-05-18  St Mary  1260 Plain Bob Minor',
    );
  });
});
