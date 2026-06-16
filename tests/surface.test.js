import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { everyRoute, document } from '../scripts/routes.mjs';
import { commandNames } from '../src/cli/program.js';
import { settingsList } from '../src/config/config.js';

/**
 * The generated document has to match the code, and the test that checks it is
 * the same check CI runs. Keeping it here as well means a developer who adds a
 * route without regenerating the file finds out from the test run rather than
 * from a red build.
 */

describe('everyRoute', () => {
  it('includes a route from each group', () => {
    const patterns = everyRoute().map((route) => route.pattern);
    expect(patterns).toContain('/health');
    expect(patterns).toContain('/methods');
    expect(patterns).toContain('/towers');
    expect(patterns).toContain('/performances');
  });

  it('gives every route a method', () => {
    for (const route of everyRoute()) {
      expect(['GET', 'POST', 'DELETE']).toContain(route.method);
    }
  });

  it('has no two routes that would shadow each other', () => {
    const seen = new Set();
    for (const route of everyRoute()) {
      const key = `${route.method} ${route.pattern}`;
      expect(seen.has(key)).toBe(false);
      seen.add(key);
    }
  });
});

describe('the document', () => {
  it('lists every route', () => {
    const written = document();
    for (const route of everyRoute()) {
      expect(written).toContain(`\`${route.pattern}\``);
    }
  });

  it('lists every command', () => {
    const written = document();
    for (const name of commandNames()) {
      expect(written).toContain(`garterhole ${name}`);
    }
  });

  it('lists every setting', () => {
    const written = document();
    for (const setting of settingsList()) {
      expect(written).toContain(setting.variable);
    }
  });

  it('matches the file on disk', () => {
    const onDisk = readFileSync(join('docs', 'surface.md'), 'utf8');
    expect(onDisk).toBe(document());
  });
});
