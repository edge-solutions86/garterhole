import { describe, it, expect } from 'vitest';
import { boardLines, summaryLine, reportLines } from '../../src/records/report.js';
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

describe('reportLines', () => {
  const records = () => [
    quarter(),
    quarter({ changes: 5040, conductor: 'Bob' }),
    quarter({ tower: 'All Saints', title: 'Cambridge Surprise Minor', changes: 720 }),
  ];

  it('opens with a heading it was given', () => {
    const lines = reportLines(records(), { title: 'Ringing in 2025' });
    expect(lines[0]).toBe('Ringing in 2025');
    expect(lines[1]).toBe('===============');
  });

  it('has a default heading', () => {
    expect(reportLines(records())[0]).toBe('Performances');
  });

  it('counts the performances by kind', () => {
    const lines = reportLines(records());
    expect(lines[3]).toBe('3 performances: 1 peal, 1 quarter peal and 1 touch');
  });

  it('adds the changes up', () => {
    expect(reportLines(records())[4]).toBe(`${1260 + 5040 + 720} changes altogether`);
  });

  it('has a table for each of the towers and the methods', () => {
    const lines = reportLines(records());
    expect(lines).toContain('By tower');
    expect(lines).toContain('By method');
    expect(lines).toContain('Most rung by');
  });

  it('stops the ringer table at the number asked for', () => {
    const lines = reportLines(records(), { topRingers: 2 });
    const start = lines.indexOf('Most rung by');
    expect(lines.slice(start + 1)).toHaveLength(2);
  });

  it('writes a report for nothing at all', () => {
    const lines = reportLines([]);
    expect(lines[3]).toBe('0 performances: 0 peals, 0 quarter peals and 0 touches');
  });
});
