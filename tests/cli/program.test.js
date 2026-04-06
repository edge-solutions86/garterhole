import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { run, runCollecting, findCommand, helpLines, commandNames } from '../../src/cli/program.js';
import { buildApp } from '../../src/http/app.js';
import { silentLogger } from '../../src/util/logger.js';
import { VERSION } from '../../src/version.js';

/** @type {string} */
let directory;
/** @type {object} */
let app;

beforeEach(() => {
  directory = mkdtempSync(join(tmpdir(), 'garterhole-'));
  app = buildApp({
    environment: { GARTERHOLE_DATA: directory },
    logger: silentLogger(),
  });
});

afterEach(() => {
  app.close();
  rmSync(directory, { recursive: true, force: true });
});

const call = (line) =>
  runCollecting(
    line.split(' ').filter((word) => word.length > 0),
    { app },
  );

describe('help and version', () => {
  it('prints the help when nothing is asked for', async () => {
    const result = await call('');
    expect(result.status).toBe(0);
    expect(result.out.join('\n')).toContain('usage: garterhole');
  });

  it('prints the help when it is asked for', async () => {
    expect((await call('--help')).out.join('\n')).toContain('method add');
  });

  it('lists every command in the help', async () => {
    const text = helpLines().join('\n');
    for (const name of commandNames()) {
      expect(text).toContain(name);
    }
  });

  it('prints the version', async () => {
    const result = await call('--version');
    expect(result.out[0]).toContain(VERSION);
  });
});

describe('finding a command', () => {
  it('finds a one word command', () => {
    expect(findCommand(['prove']).command.name).toBe('prove');
  });

  it('prefers the longer name', () => {
    expect(findCommand(['method', 'add']).command.name).toBe('method add');
  });

  it('leaves the flags behind for the command to read', () => {
    expect(findCommand(['method', 'add', '--name', 'Plain']).argv).toEqual(['--name', 'Plain']);
  });

  it('gives null for a command nobody wrote', () => {
    expect(findCommand(['sing'])).toBeNull();
  });
});

describe('running commands', () => {
  it('adds a method and lists it', async () => {
    const added = await call('method add --name Plain --stage 8 --notation x18x18x18x18,12');
    expect(added.status).toBe(0);
    const listed = await call('method list');
    expect(listed.out.join('\n')).toContain('Plain Bob Major');
  });

  it('proves a touch', async () => {
    await call('method add --name Plain --stage 8 --notation x18x18x18x18,12');
    const result = await call('prove --title Plain Bob Major --touch 3(H)');
    expect(result.out[1]).toBe('336 changes, touch');
  });

  it('answers in JSON when asked', async () => {
    await call('method add --name Plain --stage 8 --notation x18x18x18x18,12');
    const result = await call('method list --json');
    expect(JSON.parse(result.out.join('\n'))).toHaveLength(1);
  });

  it('reports a command that does not exist', async () => {
    const result = await call('sing loudly');
    expect(result.status).toBe(2);
    expect(result.err[0]).toContain('there is no command called');
  });

  it('reports a command that failed without a stack trace', async () => {
    const result = await call('method show --title Nothing Major');
    expect(result.status).toBe(1);
    expect(result.err[0]).toBe('no method with id "Nothing Major"');
  });

  it('reports a missing flag', async () => {
    const result = await call('method add --name Plain --stage 8');
    expect(result.status).toBe(1);
    expect(result.err[0]).toContain('needs --notation');
  });

  it('reports a flag nobody declared', async () => {
    const result = await call('method list --colour blue');
    expect(result.status).toBe(1);
    expect(result.err[0]).toContain('there is no flag called --colour');
  });
});

describe('run without a prepared application', () => {
  it('opens its own store and closes it again', async () => {
    const status = await run(['method', 'list'], {
      environment: { GARTERHOLE_DATA: join(directory, 'own') },
      output: { answer() {}, note() {}, problem() {}, table() {}, json: false },
    });
    expect(status).toBe(0);
  });
});
