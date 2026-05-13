import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { addTower, listTowers, showTower, TOWER_FLAGS } from '../../src/cli/commands/tower.js';
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

const args = (line) => parseArgs(line.split(' '), { known: TOWER_FLAGS });
const sixBells = '--weights 4-0-0,4-2-7,5-1-14,6-0-0,8-2-21,12-1-4';

describe('tower add', () => {
  it('files a tower and numbers the bells from the order', () => {
    const { output, err } = collectingOutput();
    addTower(args(`--name StMary --place Bridgend ${sixBells}`), { service, output });
    expect(err[0]).toBe('filed as bridgend-stmary');
    expect(service.getTower('bridgend-stmary').bells[0]).toEqual({ number: 1, weight: '4-0-0' });
  });

  it('works out what the tower can ring', () => {
    const { output } = collectingOutput();
    addTower(args(`--name StMary ${sixBells}`), { service, output });
    expect(service.getTower('stmary').stages).toEqual([6, 5, 4, 3]);
  });

  it('describes it after filing', () => {
    const { output, out } = collectingOutput();
    addTower(args(`--name StMary ${sixBells}`), { service, output });
    expect(out[0]).toContain('6 bells, tenor 12-1-4');
    expect(out).toContain('  6  12-1-4');
  });

  it('prints JSON when that was asked for', () => {
    const { output, out } = collectingOutput({ json: true });
    addTower(args(`--name StMary ${sixBells}`), { service, output });
    expect(JSON.parse(out.join('\n')).size).toBe(6);
  });

  it('refuses a weight written wrongly', () => {
    const { output } = collectingOutput();
    expect(() => addTower(args('--name StMary --weights 4-9-0'), { service, output })).toThrow(
      /is more than a hundredweight/,
    );
  });

  it('refuses a command with no weights', () => {
    const { output } = collectingOutput();
    expect(() => addTower(args('--name StMary'), { service, output })).toThrow(/needs --weights/);
  });

  it('refuses a command with no name', () => {
    const { output } = collectingOutput();
    expect(() => addTower(args(sixBells), { service, output })).toThrow(/needs --name/);
  });
});

describe('tower list', () => {
  beforeEach(() => {
    const { output } = collectingOutput();
    addTower(args(`--name StMary --place Bridgend ${sixBells}`), { service, output });
    addTower(args('--name AllSaints --weights 4-0-0,4-2-7,5-1-14,6-0-0'), { service, output });
  });

  it('prints a table with a heading', () => {
    const { output, out } = collectingOutput();
    listTowers(args('list'), { service, output });
    expect(out[0]).toContain('id');
    expect(out).toHaveLength(3);
  });

  it('says what each rings up to', () => {
    const { output, out } = collectingOutput();
    listTowers(args('list'), { service, output });
    expect(out.join('\n')).toContain('Minor');
    expect(out.join('\n')).toContain('Minimus');
  });

  it('says so when there are none', () => {
    const empty = new Service({
      repository: new Repository({ journal: new Journal(join(directory, 'other.jsonl')) }),
    });
    const { output, err } = collectingOutput();
    listTowers(args('list'), { service: empty, output });
    expect(err[0]).toBe('no towers yet');
  });

  it('prints JSON when that was asked for', () => {
    const { output, out } = collectingOutput({ json: true });
    listTowers(args('list'), { service, output });
    expect(JSON.parse(out.join('\n'))).toHaveLength(2);
  });
});

describe('tower show', () => {
  beforeEach(() => {
    const { output } = collectingOutput();
    addTower(args(`--name StMary --place Bridgend ${sixBells}`), { service, output });
  });

  it('describes one', () => {
    const { output, out } = collectingOutput();
    showTower(args('show --id bridgend-stmary'), { service, output });
    expect(out[0]).toContain('StMary, Bridgend');
  });

  it('complains about one it does not hold', () => {
    const { output } = collectingOutput();
    expect(() => showTower(args('show --id nowhere'), { service, output })).toThrow(NotFoundError);
  });

  it('complains when no tower is named', () => {
    const { output } = collectingOutput();
    expect(() => showTower(args('show'), { service, output })).toThrow(ValidationError);
  });
});
