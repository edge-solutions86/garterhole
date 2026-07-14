/**
 * Run with `node scripts/smoke.mjs`.
 *
 * Start the service, ask it a few things, and stop it.
 *
 * The unit tests exercise the handler without a socket, which is the right
 * trade for two thousand of them. This asks over a real port instead, because
 * the last time something broke in a way the tests could not see, it was the
 * wiring between the runtime and the handler rather than anything in a module.
 *
 * It runs against a temporary data directory and leaves nothing behind. There
 * is no shebang, and the work happens under a guard, so that the tests can
 * import this file without starting a service.
 */
import { mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import process from 'node:process';
import { start } from '../src/http/app.js';

/** What a running service is asked, and what counts as a good answer. */
export const CHECKS = Object.freeze([
  { name: 'health', path: '/health', expect: (body) => body.status === 'up' },
  { name: 'ready', path: '/ready', expect: (body) => body.status === 'ready' },
  { name: 'routes', path: '/routes', expect: (body) => String(body).includes('GET /health') },
  { name: 'methods', path: '/methods', expect: (body) => body.total === 0 },
]);

/**
 * Ask a running service one question.
 *
 * @param {number} port
 * @param {{ name: string, path: string, expect: (body: unknown) => boolean }} check
 * @returns {Promise<{ name: string, passed: boolean }>}
 */
export async function ask(port, check) {
  const answer = await fetch(`http://127.0.0.1:${port}${check.path}`);
  const type = answer.headers.get('content-type') ?? '';
  const body = type.includes('json') ? await answer.json() : await answer.text();
  return { name: check.name, passed: answer.status === 200 && check.expect(body) };
}

/**
 * Start a service, run every check against it, and stop it again.
 *
 * @returns {Promise<{ name: string, passed: boolean }[]>}
 */
export async function smoke() {
  const directory = mkdtempSync(join(tmpdir(), 'garterhole-smoke-'));
  const running = await start({
    environment: {
      GARTERHOLE_DATA: directory,
      GARTERHOLE_PORT: '0',
      GARTERHOLE_LOG_LEVEL: 'silent',
    },
  });
  try {
    const results = [];
    for (const check of CHECKS) {
      results.push(await ask(running.port, check));
    }
    return results;
  } finally {
    await running.close();
    rmSync(directory, { recursive: true, force: true });
  }
}

if (process.argv[1]?.endsWith('smoke.mjs')) {
  const results = await smoke();
  for (const result of results) {
    process.stdout.write(`${result.passed ? 'ok  ' : 'FAIL'}  ${result.name}\n`);
  }
  const failures = results.filter((result) => !result.passed).length;
  if (failures > 0) {
    process.stderr.write(`${failures} smoke checks failed\n`);
    process.exitCode = 1;
  }
}
