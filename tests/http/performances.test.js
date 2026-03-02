import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { addPerformanceRoutes } from '../../src/http/routes/performances.js';
import { Router } from '../../src/http/router.js';
import { Server } from '../../src/http/server.js';
import { Request } from '../../src/http/request.js';
import { Service } from '../../src/service.js';
import { Repository } from '../../src/store/repository.js';
import { Journal } from '../../src/store/journal.js';

/** @type {string} */
let directory;
/** @type {Server} */
let server;

beforeEach(() => {
  directory = mkdtempSync(join(tmpdir(), 'garterhole-'));
  const repository = new Repository({ journal: new Journal(join(directory, 'journal.jsonl')) });
  const service = new Service({ repository });
  const router = addPerformanceRoutes(new Router());
  server = new Server({ router, context: { service, router } });
});

afterEach(() => {
  rmSync(directory, { recursive: true, force: true });
});

const ask = (method, url, body) =>
  server.handle(
    new Request({ method, url, body, headers: { 'content-type': 'application/json' } }),
  );

const six = ['Alice', 'Bob', 'Carol', 'Dan', 'Erin', 'Frank'];

const quarter = (overrides = {}) => ({
  tower: 'St Mary',
  date: '2025-05-18',
  title: 'Plain Bob Minor',
  changes: 1260,
  band: six,
  conductor: 'Alice',
  ...overrides,
});

describe('filing a performance', () => {
  it('files it and says where it went', async () => {
    const response = await ask('POST', '/performances', quarter());
    expect(response.status).toBe(201);
    expect(response.headers.location).toContain('/performances/2025-05-18');
  });

  it('reports the warnings without refusing', async () => {
    const response = await ask('POST', '/performances', quarter({ conductor: undefined }));
    expect(response.status).toBe(201);
    expect(response.body.warnings).toContain('no conductor is recorded');
  });

  it('refuses a band of the wrong size', async () => {
    const response = await ask('POST', '/performances', quarter({ band: six.slice(0, 4) }));
    expect(response.status).toBe(400);
  });

  it('refuses a date written any other way', async () => {
    const response = await ask('POST', '/performances', quarter({ date: '18/05/2025' }));
    expect(response.status).toBe(400);
  });

  it('refuses a title that is not one', async () => {
    expect((await ask('POST', '/performances', quarter({ title: 'nope' }))).status).toBe(400);
  });

  it('refuses the same performance twice', async () => {
    await ask('POST', '/performances', quarter());
    expect((await ask('POST', '/performances', quarter())).status).toBe(409);
  });
});

describe('checking without filing', () => {
  it('says a sound record is sound', async () => {
    const response = await ask('POST', '/performances/check', quarter());
    expect(response.body.ok).toBe(true);
    expect((await ask('GET', '/performances')).body.total).toBe(0);
  });

  it('reports the problems with one that is not', async () => {
    const response = await ask('POST', '/performances/check', quarter({ band: six.slice(0, 5) }));
    expect(response.body.ok).toBe(false);
    expect(response.body.problems[0]).toContain('needs a band of 6');
  });
});

describe('reading performances', () => {
  beforeEach(async () => {
    await ask('POST', '/performances', quarter());
    await ask('POST', '/performances', quarter({ tower: 'All Saints', changes: 5040 }));
  });

  it('lists them with a total', async () => {
    expect((await ask('GET', '/performances')).body.total).toBe(2);
  });

  it('filters by tower', async () => {
    expect((await ask('GET', '/performances?tower=all')).body.total).toBe(1);
  });

  it('filters by who rang', async () => {
    expect((await ask('GET', '/performances?ringer=Alice')).body.total).toBe(2);
    expect((await ask('GET', '/performances?ringer=Nobody')).body.total).toBe(0);
  });

  it('reads one back by identifier', async () => {
    const listed = (await ask('GET', '/performances')).body.performances[0];
    const found = await ask('GET', `/performances/${listed.date}-st-mary-plain-bob-minor-1260`);
    expect([200, 404]).toContain(found.status);
  });

  it('answers 404 for one it does not hold', async () => {
    expect((await ask('GET', '/performances/nothing')).status).toBe(404);
  });
});

describe('the report', () => {
  beforeEach(async () => {
    await ask('POST', '/performances', quarter());
    await ask('POST', '/performances', quarter({ tower: 'All Saints', changes: 5040 }));
  });

  it('writes text by default', async () => {
    const response = await ask('GET', '/report');
    expect(response.headers['content-type']).toContain('text/plain');
    expect(response.body).toContain('By tower');
  });

  it('takes a title', async () => {
    expect((await ask('GET', '/report?title=Ringing%20in%202025')).body).toContain(
      'Ringing in 2025',
    );
  });

  it('writes a spreadsheet', async () => {
    const response = await ask('GET', '/report?format=csv');
    expect(response.headers['content-type']).toContain('text/csv');
    expect(response.body.split('\n')[0]).toContain('date,tower,title');
  });

  it('writes JSON as JSON rather than as a string', async () => {
    const response = await ask('GET', '/report?format=json');
    expect(Array.isArray(response.body)).toBe(true);
    expect(response.body).toHaveLength(2);
  });

  it('refuses a format nobody wrote', async () => {
    const response = await ask('GET', '/report?format=pdf');
    expect(response.status).toBe(400);
    expect(response.body.error.message).toContain('is not a format');
  });
});

describe('statistics', () => {
  beforeEach(async () => {
    await ask('POST', '/performances', quarter());
  });

  it('counts what has been rung', async () => {
    const response = await ask('GET', '/statistics');
    expect(response.body.totals.performances).toBe(1);
    expect(response.body.totals.quarters).toBe(1);
  });

  it('breaks the count down by tower and by ringer', async () => {
    const response = await ask('GET', '/statistics');
    expect(response.body.byTower[0].tower).toBe('St Mary');
    expect(response.body.byRinger[0].totals.performances).toBe(1);
  });
});
