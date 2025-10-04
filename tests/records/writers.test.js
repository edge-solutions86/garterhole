import { describe, it, expect } from 'vitest';
import {
  reportLines,
  textWriter,
  listWriter,
  csvWriter,
  jsonWriter,
  writerNamed,
  formats,
} from '../../src/records/writers.js';
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

const records = () => [
  quarter(),
  quarter({ changes: 5040, conductor: 'Bob' }),
  quarter({ tower: 'All Saints', title: 'Cambridge Surprise Minor', changes: 720 }),
];

describe('reportLines', () => {
  it('opens with a heading it was given', () => {
    const lines = reportLines(records(), { title: 'Ringing in 2025' });
    expect(lines[0]).toBe('Ringing in 2025');
    expect(lines[1]).toBe('===============');
  });

  it('has a default heading', () => {
    expect(reportLines(records())[0]).toBe('Performances');
  });

  it('counts the performances by kind', () => {
    expect(reportLines(records())[3]).toBe('3 performances: 1 peal, 1 quarter peal and 1 touch');
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
    expect(reportLines([])[3]).toBe('0 performances: 0 peals, 0 quarter peals and 0 touches');
  });
});

describe('textWriter', () => {
  it('joins the report into one string', () => {
    const written = textWriter.write(records());
    expect(written.split('\n')[0]).toBe('Performances');
    expect(written).toContain('By tower');
  });

  it('says what it produces', () => {
    expect(textWriter.contentType).toBe('text/plain');
  });
});

describe('listWriter', () => {
  it('writes one line per performance', () => {
    expect(listWriter.write(records()).split('\n')).toHaveLength(3);
  });

  it('writes nothing for nothing', () => {
    expect(listWriter.write([])).toBe('');
  });
});

describe('csvWriter', () => {
  it('opens with a header row', () => {
    expect(csvWriter.write(records()).split('\n')[0]).toBe(
      'date,tower,title,changes,kind,conductor,composer,band',
    );
  });

  it('writes one row per performance', () => {
    expect(csvWriter.write(records()).split('\n')).toHaveLength(4);
  });

  it('separates the band with semicolons so the commas stay out of the way', () => {
    expect(csvWriter.write([quarter()])).toContain('Alice; Bob; Carol; Dan; Erin; Frank');
  });

  it('quotes a cell with a comma in it', () => {
    const written = csvWriter.write([quarter({ tower: 'St Mary, Bridgend' })]);
    expect(written).toContain('"St Mary, Bridgend"');
  });

  it('doubles a quote inside a cell', () => {
    const written = csvWriter.write([quarter({ tower: 'The "Old" Church' })]);
    expect(written).toContain('"The ""Old"" Church"');
  });

  it('leaves a missing conductor empty', () => {
    const written = csvWriter.write([quarter({ conductor: undefined })]);
    expect(written.split('\n')[1]).toContain(',,');
  });
});

describe('jsonWriter', () => {
  it('writes something that reads back', () => {
    const parsed = JSON.parse(jsonWriter.write(records()));
    expect(parsed).toHaveLength(3);
    expect(parsed[0].title).toBe('Plain Bob Minor');
    expect(parsed[0].band).toEqual(six);
  });

  it('writes a missing conductor as null', () => {
    const parsed = JSON.parse(jsonWriter.write([quarter({ conductor: undefined })]));
    expect(parsed[0].conductor).toBeNull();
  });

  it('writes an empty list', () => {
    expect(JSON.parse(jsonWriter.write([]))).toEqual([]);
  });
});

describe('choosing a writer', () => {
  it('finds one by name', () => {
    expect(writerNamed('csv')).toBe(csvWriter);
    expect(writerNamed('json')).toBe(jsonWriter);
  });

  it('refuses a format nobody wrote', () => {
    expect(() => writerNamed('pdf')).toThrow(/is not a format/);
    expect(() => writerNamed('pdf')).toThrow(ValidationError);
  });

  it('lists the formats it knows', () => {
    expect(formats()).toEqual(['text', 'list', 'csv', 'json']);
  });
});
