import { describe, it, expect } from 'vitest';
import { readdirSync, readFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';

/**
 * The source files, checked as bytes rather than as behaviour.
 *
 * A test that imports a module proves the module works on the machine that ran
 * it. It does not prove the file is the shape a loader will accept somewhere
 * else, and the gap between those two cost a red build.
 *
 * What happened: three scripts carried a shebang. Every checkout on unix left
 * the file alone and the shebang was a comment. The Windows checkout converted
 * the line endings, and the loader stopped reading the first line as a comment
 * and started reading it as the first line of the program.
 *
 * Two things came out of that. The line endings are pinned in .gitattributes,
 * so a checkout is now the same everywhere. And these tests read the bytes, so
 * the next file that arrives in an odd shape fails here instead of failing in
 * whichever job happens to run on a platform that converts something.
 */

const SOURCE_DIRECTORIES = ['src', 'tests', 'scripts', 'bin'];

/**
 * Every javascript file under a directory, however deep.
 *
 * @param {string} directory
 * @returns {{ name: string, path: string }[]}
 */
function sourcesUnder(directory) {
  const found = [];
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) {
      found.push(...sourcesUnder(path));
    } else if (entry.name.endsWith('.js') || entry.name.endsWith('.mjs')) {
      found.push({ name: path.split(/[\\/]/).join('/'), path });
    }
  }
  return found;
}

const SOURCES = SOURCE_DIRECTORIES.flatMap(sourcesUnder);
const SCRIPTS = sourcesUnder('scripts');
const ENTRY_POINTS = sourcesUnder('bin');

/**
 * The bytes that have no business being in a source file. Newline and tab are
 * ordinary. A carriage return is the one this test was written for.
 *
 * @param {string} text
 * @returns {string[]} the offending characters, named
 */
function strayControlBytes(text) {
  const strays = [];
  for (const character of text) {
    const code = character.codePointAt(0);
    if (character === '\n' || character === '\t') {
      continue;
    }
    if (code < 32 || code === 127) {
      strays.push(`0x${code.toString(16).padStart(2, '0')}`);
    }
  }
  return [...new Set(strays)];
}

describe('the file list this test works from', () => {
  it('found something, so a pass below means something', () => {
    expect(SOURCES.length).toBeGreaterThan(100);
    expect(SCRIPTS.length).toBeGreaterThanOrEqual(3);
    expect(ENTRY_POINTS.length).toBeGreaterThanOrEqual(1);
  });

  it('reaches into the nested directories rather than stopping at the top', () => {
    const names = SOURCES.map((source) => source.name);
    expect(names).toContain('src/http/routes/methods.js');
    expect(names).toContain('tests/striking/faults.test.js');
  });

  it('would notice a stray byte, since that is the whole point', () => {
    expect(strayControlBytes('a\rb')).toEqual(['0x0d']);
    expect(strayControlBytes('a\u0000b')).toEqual(['0x00']);
    expect(strayControlBytes('fine\n\tenough\n')).toEqual([]);
  });
});

describe('every source file', () => {
  it.each(SOURCES)('$name has unix line endings', ({ path }) => {
    expect(readFileSync(path, 'utf8')).not.toContain('\r');
  });

  it.each(SOURCES)('$name has no stray control bytes', ({ path }) => {
    expect(strayControlBytes(readFileSync(path, 'utf8'))).toEqual([]);
  });

  it.each(SOURCES)('$name has no byte order mark', ({ path }) => {
    const bytes = readFileSync(path);
    expect(bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf).toBe(false);
  });

  it.each(SOURCES)('$name ends with a newline', ({ path }) => {
    expect(readFileSync(path, 'utf8').endsWith('\n')).toBe(true);
  });
});

describe('the scripts', () => {
  it('are the ones the package refers to', () => {
    expect(SCRIPTS.map((script) => script.name).sort()).toEqual([
      'scripts/bench.mjs',
      'scripts/release.mjs',
      'scripts/routes.mjs',
      'scripts/smoke.mjs',
      'scripts/verify.mjs',
    ]);
  });

  it.each(SCRIPTS)('$name has no shebang, since the tests import it', ({ path }) => {
    expect(readFileSync(path, 'utf8').startsWith('#!')).toBe(false);
  });

  it.each(SCRIPTS)('$name can be imported without doing its work', async ({ name }) => {
    const loaded = await import(`../${name}`);
    expect(Object.keys(loaded).length).toBeGreaterThan(0);
  });

  it.each(SCRIPTS)('$name guards the work it does from the command line', ({ path }) => {
    const source = readFileSync(path, 'utf8');
    const base = path.split(/[\\/]/).pop();
    expect(source).toContain(`process.argv[1]?.endsWith('${base}')`);
  });

  it('leaves the surface document alone when it is imported', async () => {
    const before = readFileSync(join('docs', 'surface.md'), 'utf8');
    const routes = await import('../scripts/routes.mjs');
    expect(routes.upToDate()).toBe(true);
    expect(readFileSync(join('docs', 'surface.md'), 'utf8')).toBe(before);
  });
});

describe('the entry point', () => {
  it.each(ENTRY_POINTS)('$name keeps its shebang, since nothing imports it', ({ path }) => {
    expect(readFileSync(path, 'utf8').startsWith('#!/usr/bin/env node\n')).toBe(true);
  });
});

describe('.gitattributes', () => {
  it('is there, since the line ending tests above rely on it', () => {
    expect(existsSync('.gitattributes')).toBe(true);
  });

  it('pins the line endings for every text file', () => {
    const text = readFileSync('.gitattributes', 'utf8');
    expect(text).toMatch(/^\* text=auto eol=lf$/m);
  });
});
