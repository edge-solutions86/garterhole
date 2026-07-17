/**
 * Run with `node scripts/routes.mjs`.
 *
 * There is no shebang here on purpose. These scripts are imported by the tests
 * as well as run from the command line, and a shebang on a module that gets
 * imported is read as the first line of the program rather than as a comment
 * once a checkout has converted the line endings. The entry point in `bin/` keeps its shebang, because nothing
 * imports it.
 *
 * Write the list of routes and commands out.
 *
 * The documentation of a service's HTTP surface goes stale the day somebody
 * adds a route and forgets the file. This reads the router and the command
 * table instead, so the document is a report rather than a promise.
 *
 * Run with `--check` in CI: it writes nothing and fails when the file on disk
 * disagrees with what the code would produce.
 */
import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import process from 'node:process';
import { Router } from '../src/http/router.js';
import { addHealthRoutes } from '../src/http/routes/health.js';
import { addMethodRoutes } from '../src/http/routes/methods.js';
import { addTowerRoutes } from '../src/http/routes/towers.js';
import { addPerformanceRoutes } from '../src/http/routes/performances.js';
import { addStrikingRoutes } from '../src/http/routes/striking.js';
import { commandNames } from '../src/cli/program.js';
import { settingsList } from '../src/config/config.js';
import { buildLabel } from '../src/version.js';

const OUTPUT = join('docs', 'surface.md');

/**
 * Every route the service serves.
 *
 * @returns {{ method: string, pattern: string }[]}
 */
export function everyRoute() {
  const router = new Router();
  addHealthRoutes(router);
  addMethodRoutes(router);
  addTowerRoutes(router);
  addPerformanceRoutes(router);
  addStrikingRoutes(router);
  return router.list();
}

/**
 * The document.
 *
 * @returns {string}
 */
export function document() {
  const lines = [
    '# The surface',
    '',
    `Written by \`npm run surface\` from the code itself, for ${buildLabel()}.`,
    '',
    '## HTTP routes',
    '',
    '| method | path |',
    '| --- | --- |',
    ...everyRoute().map((route) => `| ${route.method} | \`${route.pattern}\` |`),
    '',
    '## Commands',
    '',
    ...commandNames().map((name) => `- \`garterhole ${name}\``),
    '- `garterhole serve`',
    '',
    '## Settings',
    '',
    '| variable | default |',
    '| --- | --- |',
    ...settingsList().map((setting) => `| \`${setting.variable}\` | \`${setting.fallback}\` |`),
    '',
  ];
  return lines.join('\n');
}

/**
 * Compare what is on disk against what the code says it should be.
 *
 * @returns {boolean} true when the file is already right
 */
export function upToDate() {
  const found = existsSync(OUTPUT) ? readFileSync(OUTPUT, 'utf8') : '';
  return found === document();
}

/**
 * Write the document out, making the directory if it is not there yet.
 *
 * @returns {string} the path written
 */
export function write() {
  const directory = dirname(OUTPUT);
  if (!existsSync(directory)) {
    mkdirSync(directory, { recursive: true });
  }
  writeFileSync(OUTPUT, document(), 'utf8');
  return OUTPUT;
}

/**
 * Run from the command line. This is guarded, because the tests import this
 * file, and without the guard importing it rewrote the very file that the
 * surface check then compared against.
 */
if (process.argv[1]?.endsWith('routes.mjs')) {
  if (process.argv.includes('--check')) {
    if (!upToDate()) {
      process.stderr.write(`${OUTPUT} is out of date, so run npm run surface\n`);
      process.exit(1);
    }
    process.stdout.write(`${OUTPUT} is up to date\n`);
  } else {
    process.stdout.write(`wrote ${write()}\n`);
  }
}
