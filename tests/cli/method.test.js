import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import {
  addMethod,
  listMethods,
  showMethod,
  removeMethod,
  titleFrom,
  METHOD_FLAGS,
} from '../../src/cli/commands/method.js';
import { parseArgs } from '../../src/cli/args.js';
import { collectingOutput } from '../../src/cli/output.js';
import { Service } from '../../src/service.js';
import { Repository } from '../../src/store/repository.js';
import { Journal } from '../../src/store/journal.js';
import { NotFoundError, ValidationError } from '../../src/errors.js';

/** @type {string} */
let directory;
/** @type {Service} */
let service;

beforeEach(() => {
  directory = mkdtempSync(join(tmpdir(), 'garterhole-'));
  const repository = new Repository({ journal: new Journal(join(directory, 'journal.jsonl')) });
  service = new Service({ repository });
});

afterEach(() => {
  rmSync(directory, { recursive: true, force: true });
});

const args = (line) => parseArgs(line.split(' '), { known: METHOD_FLAGS });

describe('method add', () => {
  it('files a method and prints what it worked out', () => {
    const { output, out, err } = collectingOutput();
    addMethod(args('--name Plain --stage 8 --notation x18x18x18x18,12'), { service, output });
    expect(err[0]).toBe('filed as Plain Bob Major');
    expect(out[0]).toBe('Plain Bob Major');
    expect(out.join('\n')).toContain('lead      16 changes');
  });

  it('prints JSON when that was asked for', () => {
    const { output, out } = collectingOutput({ json: true });
    addMethod(args('--name Plain --stage 8 --notation x18x18x18x18,12'), { service, output });
    expect(JSON.parse(out.join('\n')).title).toBe('Plain Bob Major');
  });

  it('complains when a flag is missing', () => {
    const { output } = collectingOutput();
    expect(() => addMethod(args('--name Plain --stage 8'), { service, output })).toThrow(
      /needs --notation/,
    );
  });

  it('complains about a stage that is not a number', () => {
    const { output } = collectingOutput();
    expect(() =>
      addMethod(args('--name Plain --stage eight --notation x1x1'), { service, output }),
    ).toThrow(/must be a whole number/);
  });
});

describe('method list', () => {
  beforeEach(() => {
    service.addMethod({ name: 'Plain', stage: 8, notation: 'x18x18x18x18,12' });
    service.addMethod({ name: 'Cambridge', stage: 8, notation: 'x38x14x1258x36x14x58x16x78,12' });
    service.addMethod({ name: 'Plain', stage: 6, notation: 'x1x1x1,2' });
  });

  it('prints a table with a heading', () => {
    const { output, out } = collectingOutput();
    listMethods(args('list'), { service, output });
    expect(out[0]).toContain('title');
    expect(out).toHaveLength(4);
  });

  it('filters by stage', () => {
    const { output, out } = collectingOutput();
    listMethods(args('list --stage 6'), { service, output });
    expect(out).toHaveLength(2);
  });

  it('filters by class', () => {
    const { output, out } = collectingOutput();
    listMethods(args('list --class Surprise'), { service, output });
    expect(out).toHaveLength(2);
  });

  it('says so when the library is empty', () => {
    const empty = new Service({
      repository: new Repository({ journal: new Journal(join(directory, 'other.jsonl')) }),
    });
    const { output, out, err } = collectingOutput();
    listMethods(args('list'), { service: empty, output });
    expect(out).toEqual([]);
    expect(err[0]).toContain('nothing in the library yet');
  });

  it('prints JSON when that was asked for', () => {
    const { output, out } = collectingOutput({ json: true });
    listMethods(args('list'), { service, output });
    expect(JSON.parse(out.join('\n'))).toHaveLength(3);
  });
});

describe('method show', () => {
  beforeEach(() => {
    service.addMethod({ name: 'Cambridge', stage: 8, notation: 'x38x14x1258x36x14x58x16x78,12' });
  });

  it('describes a method', () => {
    const { output, out } = collectingOutput();
    showMethod(args('show --title Cambridge Surprise Major'), { service, output });
    expect(out[0]).toBe('Cambridge Surprise Major');
    expect(out.join('\n')).toContain('class     Surprise');
  });

  it('takes the title from what is left on the line', () => {
    const { output, out } = collectingOutput();
    showMethod(parseArgs(['show', '--json=false', 'Cambridge', 'Surprise', 'Major']), {
      service,
      output,
    });
    expect(out[0]).toBe('Cambridge Surprise Major');
  });

  it('complains about a method it does not hold', () => {
    const { output } = collectingOutput();
    expect(() =>
      showMethod(args('show --title Bristol Surprise Major'), { service, output }),
    ).toThrow(NotFoundError);
  });

  it('complains when there is no title at all', () => {
    const { output } = collectingOutput();
    expect(() => showMethod(args('show'), { service, output })).toThrow(/needs --title/);
  });
});

describe('method remove', () => {
  beforeEach(() => {
    service.addMethod({ name: 'Plain', stage: 8, notation: 'x18x18x18x18,12' });
  });

  it('removes one and says so', () => {
    const { output, err } = collectingOutput();
    removeMethod(args('remove --title Plain Bob Major'), { service, output });
    expect(err[0]).toBe('removed Plain Bob Major');
    expect(service.listMethods()).toHaveLength(0);
  });

  it('complains about one it does not hold', () => {
    const { output } = collectingOutput();
    expect(() =>
      removeMethod(args('remove --title Bristol Surprise Major'), { service, output }),
    ).toThrow(ValidationError);
  });
});

describe('titleFrom', () => {
  it('prefers the flag', () => {
    expect(titleFrom(args('show --title Plain Bob Major'))).toBe('Plain Bob Major');
  });

  it('falls back to the rest of the line', () => {
    expect(titleFrom({ flags: {}, rest: ['Plain', 'Bob', 'Major'], command: [] })).toBe(
      'Plain Bob Major',
    );
  });

  it('complains when there is neither', () => {
    expect(() => titleFrom({ flags: {}, rest: [], command: [] })).toThrow(/needs --title/);
  });
});
