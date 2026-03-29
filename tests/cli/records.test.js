import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import {
  fileRecord,
  listRecords,
  reportRecords,
  printBoard,
  RECORD_FLAGS,
} from '../../src/cli/commands/records.js';
import { parseArgs } from '../../src/cli/args.js';
import { collectingOutput } from '../../src/cli/output.js';
import { Service } from '../../src/service.js';
import { Repository } from '../../src/store/repository.js';
import { Journal } from '../../src/store/journal.js';
import { ValidationError } from '../../src/errors.js';

/** @type {string} */
let directory;
/** @type {Service} */
let service;
const today = new Date(Date.UTC(2025, 4, 18));

beforeEach(() => {
  directory = mkdtempSync(join(tmpdir(), 'garterhole-'));
  const repository = new Repository({ journal: new Journal(join(directory, 'journal.jsonl')) });
  service = new Service({ repository });
});

afterEach(() => {
  rmSync(directory, { recursive: true, force: true });
});

const args = (line) => parseArgs(line.split(' '), { known: RECORD_FLAGS });
const quarterLine =
  '--tower StMary --title Plain Bob Minor --changes 1260 --band Alice,Bob,Carol,Dan,Erin,Frank';

describe('records file', () => {
  it('files a quarter and says where it went', () => {
    const { output, err } = collectingOutput();
    fileRecord(args(quarterLine), { service, output, today });
    expect(err[err.length - 1]).toContain('filed as');
    expect(service.listPerformances()).toHaveLength(1);
  });

  it('uses today when no date is given', () => {
    const { output } = collectingOutput();
    fileRecord(args(quarterLine), { service, output, today });
    expect(service.listPerformances()[0].date).toBe('2025-05-18');
  });

  it('takes a date when one is given', () => {
    const { output } = collectingOutput();
    fileRecord(args(`${quarterLine} --date 2025-03-09`), { service, output, today });
    expect(service.listPerformances()[0].date).toBe('2025-03-09');
  });

  it('makes the first of the band the conductor by default', () => {
    const { output } = collectingOutput();
    fileRecord(args(quarterLine), { service, output, today });
    expect(service.listPerformances()[0].conductor).toBe('Alice');
  });

  it('takes a conductor when one is named', () => {
    const { output } = collectingOutput();
    fileRecord(args(`${quarterLine} --conductor Carol`), { service, output, today });
    expect(service.listPerformances()[0].conductor).toBe('Carol');
  });

  it('refuses a band that is the wrong size for the method', () => {
    const { output } = collectingOutput();
    expect(() =>
      fileRecord(args('--tower StMary --title Plain Bob Minor --changes 1260 --band Alice,Bob'), {
        service,
        output,
        today,
      }),
    ).toThrow(/needs a band of 6/);
  });

  it('refuses a date written any other way', () => {
    const { output } = collectingOutput();
    expect(() => fileRecord(args(`${quarterLine} --date 18/05/2025`), { service, output })).toThrow(
      /must be a date like/,
    );
  });

  it('refuses a command with no band', () => {
    const { output } = collectingOutput();
    expect(() =>
      fileRecord(args('--tower StMary --title Plain Bob Minor --changes 1260'), {
        service,
        output,
        today,
      }),
    ).toThrow(/needs --band/);
  });
});

describe('records list', () => {
  beforeEach(() => {
    const { output } = collectingOutput();
    fileRecord(args(quarterLine), { service, output, today });
    fileRecord(args(`${quarterLine} --changes 5040 --date 2025-06-01`), { service, output, today });
  });

  it('prints a table with a heading', () => {
    const { output, out } = collectingOutput();
    listRecords(args('list'), { service, output });
    expect(out[0]).toContain('date');
    expect(out).toHaveLength(3);
  });

  it('filters by who rang', () => {
    const { output, out } = collectingOutput();
    listRecords(args('list --ringer Nobody'), { service, output });
    expect(out).toEqual([]);
  });

  it('says so when nothing is filed', () => {
    const empty = new Service({
      repository: new Repository({ journal: new Journal(join(directory, 'other.jsonl')) }),
    });
    const { output, err } = collectingOutput();
    listRecords(args('list'), { service: empty, output });
    expect(err[0]).toBe('nothing filed yet');
  });

  it('prints JSON when that was asked for', () => {
    const { output, out } = collectingOutput({ json: true });
    listRecords(args('list'), { service, output });
    expect(JSON.parse(out.join('\n'))).toHaveLength(2);
  });
});

describe('records report', () => {
  beforeEach(() => {
    const { output } = collectingOutput();
    fileRecord(args(quarterLine), { service, output, today });
  });

  it('writes a text report by default', () => {
    const { output, out } = collectingOutput();
    reportRecords(args('report'), { service, output });
    expect(out.join('\n')).toContain('By tower');
  });

  it('writes a spreadsheet when asked', () => {
    const { output, out } = collectingOutput();
    reportRecords(args('report --format csv'), { service, output });
    expect(out.join('\n')).toContain('date,tower,title');
  });

  it('refuses a format nobody wrote', () => {
    const { output } = collectingOutput();
    expect(() => reportRecords(args('report --format pdf'), { service, output })).toThrow(
      /is not a format/,
    );
  });
});

describe('records board', () => {
  beforeEach(() => {
    const { output } = collectingOutput();
    fileRecord(args(quarterLine), { service, output, today });
  });

  it('prints one the way it goes on a board', () => {
    const { output, out } = collectingOutput();
    printBoard(args('board'), { service, output });
    expect(out[0]).toBe('StMary');
    expect(out).toContain('1  Alice (c)');
  });

  it('complains when there is nothing to print', () => {
    const empty = new Service({
      repository: new Repository({ journal: new Journal(join(directory, 'other.jsonl')) }),
    });
    const { output } = collectingOutput();
    expect(() => printBoard(args('board'), { service: empty, output })).toThrow(ValidationError);
  });
});
