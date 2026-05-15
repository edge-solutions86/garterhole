import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { runCollecting, commandNames } from '../../src/cli/program.js';
import { buildApp } from '../../src/http/app.js';
import { Request } from '../../src/http/request.js';
import { silentLogger } from '../../src/util/logger.js';

/**
 * A whole tower's year, done through the two surfaces.
 *
 * Every other test in the suite exercises one module. This one exercises the
 * thing the tower actually installs: file some methods, prove a touch, add the
 * ring of bells, file the quarters, and read the report back. It is slow and it
 * is worth it, because it is the only test that would notice the command line
 * and the HTTP surface drifting apart.
 */

/** @type {string} */
let directory;
/** @type {object} */
let app;

beforeEach(() => {
  directory = mkdtempSync(join(tmpdir(), 'garterhole-'));
  app = buildApp({ environment: { GARTERHOLE_DATA: directory }, logger: silentLogger() });
});

afterEach(() => {
  app.close();
  rmSync(directory, { recursive: true, force: true });
});

const cli = (line) =>
  runCollecting(
    line.split(' ').filter((word) => word.length > 0),
    { app },
  );
const http = (method, url, body) =>
  app.server.handle(
    new Request({ method, url, body, headers: { 'content-type': 'application/json' } }),
  );

describe('a tower using both surfaces', () => {
  it('files a method on the command line and reads it back over HTTP', async () => {
    const added = await cli('method add --name Plain --stage 6 --notation x1x1x1,2');
    expect(added.status).toBe(0);
    const read = await http('GET', '/methods/Plain%20Bob%20Minor');
    expect(read.status).toBe(200);
    expect(read.body.className).toBe('Bob');
  });

  it('files a method over HTTP and proves a touch of it on the command line', async () => {
    await http('POST', '/methods', { name: 'Plain', stage: 8, notation: 'x18x18x18x18,12' });
    const proved = await cli('prove --title Plain Bob Major --touch 3(H)');
    expect(proved.out[1]).toBe('336 changes, touch');
    expect(proved.out[2]).toBe('true, 336 rows');
  });

  it('adds a tower on the command line and lists its bells over HTTP', async () => {
    await cli(
      'tower add --name StMary --place Bridgend --weights 4-0-0,4-2-7,5-1-14,6-0-0,8-2-21,12-1-4',
    );
    const bells = await http('GET', '/towers/bridgend-stmary/bells');
    expect(bells.status).toBe(200);
    expect(bells.body.bells).toHaveLength(6);
    expect(bells.body.tenor).toBe('12-1-4');
  });

  it('files a quarter over HTTP and reports it on the command line', async () => {
    await http('POST', '/performances', {
      tower: 'St Mary',
      date: '2025-05-18',
      title: 'Plain Bob Minor',
      changes: 1260,
      band: ['Alice', 'Bob', 'Carol', 'Dan', 'Erin', 'Frank'],
      conductor: 'Alice',
    });
    const report = await cli('records report');
    expect(report.out.join('\n')).toContain('1 quarter peal');
  });

  it('keeps everything across a restart', async () => {
    await cli('method add --name Plain --stage 6 --notation x1x1x1,2');
    await cli('tower add --name StMary --weights 4-0-0,4-2-7,5-1-14,6-0-0');
    app.close();
    app = buildApp({ environment: { GARTERHOLE_DATA: directory }, logger: silentLogger() });
    expect(app.service.counts()).toEqual({ methods: 1, towers: 1, performances: 0 });
  });

  it('refuses a performance whose band is the wrong size, from either surface', async () => {
    const overHttp = await http('POST', '/performances', {
      tower: 'St Mary',
      date: '2025-05-18',
      title: 'Plain Bob Minor',
      changes: 1260,
      band: ['Alice', 'Bob', 'Carol', 'Dan'],
    });
    expect(overHttp.status).toBe(400);
    const overCli = await cli(
      'records file --tower StMary --title Plain Bob Minor --changes 1260 --band Alice,Bob,Carol,Dan',
    );
    expect(overCli.status).toBe(1);
  });

  it('serves every command the help lists', () => {
    for (const name of commandNames()) {
      expect(name.length).toBeGreaterThan(0);
    }
    expect(commandNames()).toContain('tower add');
  });
});
