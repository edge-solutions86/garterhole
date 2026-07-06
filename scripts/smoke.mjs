#!/usr/bin/env node
/**
 * Start the service, ask it a few things, and stop it.
 *
 * The unit tests exercise the handler without a socket, which is the right
 * trade for two thousand of them. This asks over a real port instead, because
 * the last time something broke in a way the tests could not see, it was the
 * wiring between the runtime and the handler rather than anything in a module.
 *
 * It runs against a temporary data directory and leaves nothing behind.
 */
import { mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import process from 'node:process';
import { start } from '../src/http/app.js';

const CHECKS = [
  { name: 'health', path: '/health', expect: (body) => body.status === 'up' },
  { name: 'ready', path: '/ready', expect: (body) => body.status === 'ready' },
  { name: 'routes', path: '/routes', expect: (body) => String(body).includes('GET /health') },
  { name: 'methods', path: '/methods', expect: (body) => body.total === 0 },
];

const directory = mkdtempSync(join(tmpdir(), 'garterhole-smoke-'));
let failures = 0;

const running = await start({
  environment: {
    GARTERHOLE_DATA: directory,
    GARTERHOLE_PORT: '0',
    GARTERHOLE_LOG_LEVEL: 'silent',
  },
});

try {
  for (const check of CHECKS) {
    const answer = await fetch(`http://127.0.0.1:${running.port}${check.path}`);
    const type = answer.headers.get('content-type') ?? '';
    const body = type.includes('json') ? await answer.json() : await answer.text();
    const passed = answer.status === 200 && check.expect(body);
    process.stdout.write(`${passed ? 'ok  ' : 'FAIL'}  ${check.name}\n`);
    if (!passed) {
      failures += 1;
    }
  }
} finally {
  await running.close();
  rmSync(directory, { recursive: true, force: true });
}

if (failures > 0) {
  process.stderr.write(`${failures} smoke checks failed\n`);
  process.exitCode = 1;
}
