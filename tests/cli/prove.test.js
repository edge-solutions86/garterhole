import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import {
  prove,
  printLead,
  methodFrom,
  wantsLead,
  PROVE_FLAGS,
} from '../../src/cli/commands/prove.js';
import { parseArgs } from '../../src/cli/args.js';
import { collectingOutput } from '../../src/cli/output.js';
import { Service } from '../../src/service.js';
import { Repository } from '../../src/store/repository.js';
import { Journal } from '../../src/store/journal.js';
import { NotFoundError } from '../../src/errors.js';

/** @type {string} */
let directory;
/** @type {Service} */
let service;

beforeEach(() => {
  directory = mkdtempSync(join(tmpdir(), 'garterhole-'));
  const repository = new Repository({ journal: new Journal(join(directory, 'journal.jsonl')) });
  service = new Service({ repository });
  service.addMethod({ name: 'Plain', stage: 8, notation: 'x18x18x18x18,12' });
});

afterEach(() => {
  rmSync(directory, { recursive: true, force: true });
});

const args = (line) => parseArgs(line.split(' '), { known: PROVE_FLAGS });

describe('prove', () => {
  it('rings a touch of a method in the library', () => {
    const { output, out } = collectingOutput();
    prove(args('--title Plain Bob Major --touch 3(H)'), { service, output });
    expect(out[0]).toBe('Plain Bob Major');
    expect(out[1]).toBe('336 changes, touch');
    expect(out[2]).toBe('true, 336 rows');
  });

  it('rings a touch of notation given on the line', () => {
    const { output, out } = collectingOutput();
    prove(args('--notation x1x1x1,2 --stage 6 --name Plain --touch 3(H)'), { service, output });
    expect(out[1]).toBe('180 changes, touch');
  });

  it('prints JSON when that was asked for', () => {
    const { output, out } = collectingOutput({ json: true });
    prove(args('--title Plain Bob Major --touch 3(H)'), { service, output });
    const answer = JSON.parse(out.join('\n'));
    expect(answer.length).toBe(336);
    expect(answer.true).toBe(true);
  });

  it('says so when the touch is false', () => {
    const { output, err } = collectingOutput();
    service.addMethod({ name: 'Plain', stage: 6, notation: 'x1x1x1,2' });
    prove(args('--title Plain Bob Minor --touch 3(-..-..)'), { service, output });
    expect(err).toContain('this touch is false');
  });

  it('complains about a method it does not hold', () => {
    const { output } = collectingOutput();
    expect(() =>
      prove(args('--title Bristol Surprise Major --touch 3(H)'), { service, output }),
    ).toThrow(NotFoundError);
  });

  it('complains when there is no touch', () => {
    const { output } = collectingOutput();
    expect(() => prove(args('--title Plain Bob Major'), { service, output })).toThrow(
      /needs --touch/,
    );
  });
});

describe('printLead', () => {
  it('prints a lead with the notation beside the rows', () => {
    const { output, out } = collectingOutput();
    printLead(args('--title Plain Bob Major'), { service, output });
    expect(out).toHaveLength(17);
    expect(out[0]).toContain('12345678');
    expect(out[1]).toContain('21436587');
  });

  it('prints the rows as JSON when that was asked for', () => {
    const { output, out } = collectingOutput({ json: true });
    printLead(args('--title Plain Bob Major'), { service, output });
    expect(JSON.parse(out.join('\n')).rows).toHaveLength(17);
  });
});

describe('methodFrom', () => {
  it('takes one from the library by title', () => {
    expect(methodFrom(args('--title Plain Bob Major'), { service }).stage).toBe(8);
  });

  it('builds one from notation', () => {
    const method = methodFrom(args('--notation x1x1x1,2 --stage 6 --name Plain'), { service });
    expect(method.leadLength).toBe(12);
    expect(method.name).toBe('Plain');
  });

  it('names an unnamed method something rather than refusing', () => {
    expect(methodFrom(args('--notation x1x1x1,2 --stage 6'), { service }).name).toBe('Unnamed');
  });

  it('complains about notation with no stage', () => {
    expect(() => methodFrom(args('--notation x1x1x1,2'), { service })).toThrow(/needs --stage/);
  });

  it('complains about notation it cannot read', () => {
    expect(() => methodFrom(args('--notation x1?x1 --stage 6'), { service })).toThrow(
      /not part of place notation/,
    );
  });
});

describe('wantsLead', () => {
  it('is true when the flag is given', () => {
    expect(wantsLead(args('--lead --title Plain Bob Major'))).toBe(true);
  });

  it('is false when it is not', () => {
    expect(wantsLead(args('--title Plain Bob Major'))).toBe(false);
  });
});
