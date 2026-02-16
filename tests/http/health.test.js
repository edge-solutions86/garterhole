import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { addHealthRoutes } from '../../src/http/routes/health.js';
import { Router } from '../../src/http/router.js';
import { Server } from '../../src/http/server.js';
import { Request } from '../../src/http/request.js';
import { Service } from '../../src/service.js';
import { Repository } from '../../src/store/repository.js';
import { Journal } from '../../src/store/journal.js';
import { FixedClock } from '../../src/util/clock.js';
import { VERSION } from '../../src/version.js';

/** @type {string} */
let directory;
/** @type {Service} */
let service;
/** @type {Server} */
let server;
/** @type {FixedClock} */
let clock;

beforeEach(() => {
  directory = mkdtempSync(join(tmpdir(), 'garterhole-'));
  const repository = new Repository({ journal: new Journal(join(directory, 'journal.jsonl')) });
  service = new Service({ repository });
  clock = new FixedClock(1000);
  const router = addHealthRoutes(new Router(), { clock });
  server = new Server({ router, context: { service, router } });
});

afterEach(() => {
  rmSync(directory, { recursive: true, force: true });
});

const ask = (path) => server.handle(new Request({ url: path }));

describe('/health', () => {
  it('says the service is up', async () => {
    const response = await ask('/health');
    expect(response.status).toBe(200);
    expect(response.body.status).toBe('up');
  });

  it('names the product and the version', async () => {
    const response = await ask('/health');
    expect(response.body.product).toBe('garterhole');
    expect(response.body.version).toBe(VERSION);
  });

  it('reports how long it has been up', async () => {
    clock.advance(5000);
    expect((await ask('/health')).body.uptimeMs).toBe(5000);
  });

  it('does no work, so it answers with nothing in the store', async () => {
    expect((await ask('/health')).status).toBe(200);
  });
});

describe('/ready', () => {
  it('says the service can do its job', async () => {
    const response = await ask('/ready');
    expect(response.status).toBe(200);
    expect(response.body.status).toBe('ready');
  });

  it('says what the store is holding', async () => {
    service.addMethod({ name: 'Plain', stage: 8, notation: 'x18x18x18x18,12' });
    expect((await ask('/ready')).body.holding.methods).toBe(1);
  });

  it('says it is not ready when the store cannot answer', async () => {
    const broken = new Server({
      router: addHealthRoutes(new Router(), { clock }),
      context: {
        service: {
          counts() {
            throw new Error('the disk has gone');
          },
        },
      },
    });
    const response = await broken.handle(new Request({ url: '/ready' }));
    expect(response.status).toBe(503);
    expect(response.body.reason).toBe('the disk has gone');
  });
});

describe('/routes', () => {
  it('lists what the service serves', async () => {
    const response = await ask('/routes');
    expect(response.body).toContain('GET /health');
    expect(response.body).toContain('GET /ready');
  });

  it('answers as plain text', async () => {
    expect((await ask('/routes')).headers['content-type']).toContain('text/plain');
  });
});
