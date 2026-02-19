import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { addMethodRoutes } from '../../src/http/routes/methods.js';
import { Router } from '../../src/http/router.js';
import { Server } from '../../src/http/server.js';
import { Request } from '../../src/http/request.js';
import { Service } from '../../src/service.js';
import { Repository } from '../../src/store/repository.js';
import { Journal } from '../../src/store/journal.js';

/** @type {string} */
let directory;
/** @type {Service} */
let service;
/** @type {Server} */
let server;

beforeEach(() => {
  directory = mkdtempSync(join(tmpdir(), 'garterhole-'));
  const repository = new Repository({ journal: new Journal(join(directory, 'journal.jsonl')) });
  service = new Service({ repository });
  const router = addMethodRoutes(new Router());
  server = new Server({ router, context: { service, router } });
});

afterEach(() => {
  rmSync(directory, { recursive: true, force: true });
});

const ask = (method, url, body) =>
  server.handle(
    new Request({ method, url, body, headers: { 'content-type': 'application/json' } }),
  );

const plainBobMajor = { name: 'Plain', stage: 8, notation: 'x18x18x18x18,12' };
const cambridge = { name: 'Cambridge', stage: 8, notation: 'x38x14x1258x36x14x58x16x78,12' };

describe('adding a method', () => {
  it('files it and says where it went', async () => {
    const response = await ask('POST', '/methods', plainBobMajor);
    expect(response.status).toBe(201);
    expect(response.headers.location).toBe('/methods/Plain%20Bob%20Major');
    expect(response.body.title).toBe('Plain Bob Major');
  });

  it('works the class out rather than taking one', async () => {
    const response = await ask('POST', '/methods', { ...cambridge, class: 'Treble Bob' });
    expect(response.body.class).toBe('Surprise');
  });

  it('refuses a body with a field missing', async () => {
    const response = await ask('POST', '/methods', { name: 'Plain', stage: 8 });
    expect(response.status).toBe(400);
    expect(response.body.error.message).toContain('notation');
  });

  it('refuses notation that does not work at the stage', async () => {
    const response = await ask('POST', '/methods', { name: 'Bad', stage: 6, notation: 'x18x18' });
    expect(response.status).toBe(400);
    expect(response.body.error.message).toContain('notation');
  });

  it('refuses a stage nobody rings', async () => {
    const response = await ask('POST', '/methods', { ...plainBobMajor, stage: 2 });
    expect(response.status).toBe(400);
  });

  it('refuses the same method twice', async () => {
    await ask('POST', '/methods', plainBobMajor);
    expect((await ask('POST', '/methods', plainBobMajor)).status).toBe(409);
  });
});

describe('listing methods', () => {
  beforeEach(async () => {
    await ask('POST', '/methods', plainBobMajor);
    await ask('POST', '/methods', cambridge);
    await ask('POST', '/methods', { name: 'Plain', stage: 6, notation: 'x1x1x1,2' });
  });

  it('lists everything with a total', async () => {
    const response = await ask('GET', '/methods');
    expect(response.body.total).toBe(3);
  });

  it('filters by stage', async () => {
    expect((await ask('GET', '/methods?stage=6')).body.total).toBe(1);
  });

  it('filters by class', async () => {
    expect((await ask('GET', '/methods?class=Surprise')).body.total).toBe(1);
  });

  it('gives nothing when nothing matches', async () => {
    expect((await ask('GET', '/methods?stage=12')).body.methods).toEqual([]);
  });
});

describe('reading one method', () => {
  beforeEach(async () => {
    await ask('POST', '/methods', cambridge);
  });

  it('describes it with the derived facts', async () => {
    const response = await ask('GET', '/methods/Cambridge%20Surprise%20Major');
    expect(response.status).toBe(200);
    expect(response.body.className).toBe('Surprise');
    expect(response.body.leadLength).toBe(32);
  });

  it('answers 404 for one it does not hold', async () => {
    expect((await ask('GET', '/methods/Bristol%20Surprise%20Major')).status).toBe(404);
  });

  it('refuses a title that is not one', async () => {
    expect((await ask('GET', '/methods/nonsense')).status).toBe(400);
  });

  it('removes one', async () => {
    expect((await ask('DELETE', '/methods/Cambridge%20Surprise%20Major')).status).toBe(204);
    expect((await ask('DELETE', '/methods/Cambridge%20Surprise%20Major')).status).toBe(404);
  });
});

describe('analysing a touch', () => {
  beforeEach(async () => {
    await ask('POST', '/methods', plainBobMajor);
  });

  it('rings it and reports', async () => {
    const response = await ask('POST', '/methods/Plain%20Bob%20Major/analyse', { touch: '3(H)' });
    expect(response.status).toBe(200);
    expect(response.body.length).toBe(336);
    expect(response.body.true).toBe(true);
  });

  it('refuses a touch it cannot read', async () => {
    const response = await ask('POST', '/methods/Plain%20Bob%20Major/analyse', { touch: 'W)' });
    expect(response.status).toBe(400);
  });

  it('refuses a request with no touch in it', async () => {
    expect((await ask('POST', '/methods/Plain%20Bob%20Major/analyse', {})).status).toBe(400);
  });

  it('answers 404 for a method it does not hold', async () => {
    const response = await ask('POST', '/methods/Nothing%20Major/analyse', { touch: '3(H)' });
    expect(response.status).toBe(404);
  });
});
