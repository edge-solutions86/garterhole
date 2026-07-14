import { describe, it, expect } from 'vitest';
import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * The scripts, checked as artifacts rather than as behaviour.
 *
 * A test that imports a script proves the script works. It does not prove the
 * file is the shape a loader will accept, and the difference between those two
 * cost a red build: three scripts carried a shebang, which every loader on this
 * machine treated as a comment and one loader on Linux treated as the first
 * line of the program.
 *
 * So these check the bytes. A shebang belongs on a file nothing imports, and
 * nothing else in here has any business being unusual.
 */

const SCRIPTS = readdirSync('scripts')
  .filter((name) => name.endsWith('.mjs'))
  .map((name) => ({ name, path: join('scripts', name) }));

const ENTRY_POINTS = [{ name: 'garterhole.js', path: join('bin', 'garterhole.js') }];

describe('the scripts directory', () => {
  it('has scripts in it, so this test is measuring something', () => {
    expect(SCRIPTS.length).toBeGreaterThanOrEqual(3);
  });

  it('names them all', () => {
    expect(SCRIPTS.map((script) => script.name).sort()).toEqual([
      'bench.mjs',
      'release.mjs',
      'routes.mjs',
      'smoke.mjs',
      'verify.mjs',
    ]);
  });
});

describe('every script', () => {
  it.each(SCRIPTS)('$name has no shebang, since the tests import it', ({ path }) => {
    expect(readFileSync(path, 'utf8').startsWith('#!')).toBe(false);
  });

  it.each(SCRIPTS)('$name has no byte order mark', ({ path }) => {
    const bytes = readFileSync(path);
    expect(bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf).toBe(false);
  });

  it.each(SCRIPTS)('$name has unix line endings', ({ path }) => {
    expect(readFileSync(path, 'utf8')).not.toContain('\r');
  });

  it.each(SCRIPTS)('$name has no stray control bytes', ({ path }) => {
    const text = readFileSync(path, 'utf8');
    const strays = [...text].filter((character) => {
      const code = character.codePointAt(0);
      return (code < 32 && character !== '\n' && character !== '\t') || code === 127;
    });
    expect(strays).toEqual([]);
  });

  it.each(SCRIPTS)('$name can be imported', async ({ name }) => {
    const loaded = await import(`../scripts/${name}`);
    expect(loaded).toBeDefined();
  });
});

describe('the entry point', () => {
  it.each(ENTRY_POINTS)('$name keeps its shebang, since nothing imports it', ({ path }) => {
    expect(readFileSync(path, 'utf8').startsWith('#!/usr/bin/env node\n')).toBe(true);
  });

  it.each(ENTRY_POINTS)('$name has unix line endings', ({ path }) => {
    expect(readFileSync(path, 'utf8')).not.toContain('\r');
  });
});
