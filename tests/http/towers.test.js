import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { addTowerRoutes, towerFrom, towerId } from '../../src/http/routes/towers.js';
import { Router } from '../../src/http/router.js';
import { Server } from '../../src/http/server.js';
import { Request } from '../../src/http/request.js';
import { Service } from '../../src/service.js';
import { Repository } from '../../src/store/repository.js';
import { Journal } from '../../src/store/journal.js';
import { ValidationError } from '../../src/errors.js';

/** @type {string} */
let directory;
/** @type {Server} */
let server;

beforeEach(() => {
  directory = mkdtempSync(join(tmpdir(), 'garterhole-'));
  const repository = new Repository({ journal: new Journal(join(directory, 'journal.jsonl')) });
  const service = new Service({ repository });
  const router = addTowerRoutes(new Router());
  server = new Server({ router, context: { service, router } });
});

afterEach(() => {
  rmSync(directory, { recursive: true, force: true });
});

const ask = (method, url, body) =>
  server.handle(
    new Request({ method, url, body, headers: { 'content-type': 'application/json' } }),
  );

const stMary = {
  name: 'St Mary the Virgin',
  place: 'Bridgend',
  bells: [
    { number: 1, weight: '4-0-0' },
    { number: 2, weight: '4-2-7' },
    { number: 3, weight: '5-1-14' },
    { number: 4, weight: '6-0-0' },
    { number: 5, weight: '8-2-21' },
    { number: 6, weight: '12-1-4', note: 'F' },
  ],
};

describe('adding a tower', () => {
  it('files it under an identifier made from its name', async () => {
    const response = await ask('POST', '/towers', stMary);
    expect(response.status).toBe(201);
    expect(response.body.id).toBe('bridgend-st-mary-the-virgin');
    expect(response.headers.location).toBe('/towers/bridgend-st-mary-the-virgin');
  });

  it('works out what it can ring', async () => {
    const response = await ask('POST', '/towers', stMary);
    expect(response.body.size).toBe(6);
    expect(response.body.stages).toEqual([6, 5, 4, 3]);
  });

  it('takes a weight in pounds instead', async () => {
    const response = await ask('POST', '/towers', {
      name: 'Test',
      bells: [{ number: 1, weightPounds: 400 }],
    });
    expect(response.status).toBe(201);
  });

  it('refuses a bell that does not say what it weighs', async () => {
    const response = await ask('POST', '/towers', { name: 'Test', bells: [{ number: 1 }] });
    expect(response.status).toBe(400);
    expect(response.body.error.message).toContain('does not say what it weighs');
  });

  it('refuses a ring with a gap in it', async () => {
    const response = await ask('POST', '/towers', {
      name: 'Test',
      bells: [
        { number: 1, weight: '4-0-0' },
        { number: 3, weight: '6-0-0' },
      ],
    });
    expect(response.status).toBe(400);
    expect(response.body.error.message).toContain('gap');
  });

  it('refuses a weight written wrongly', async () => {
    const response = await ask('POST', '/towers', {
      name: 'Test',
      bells: [{ number: 1, weight: '4-9-0' }],
    });
    expect(response.status).toBe(400);
  });

  it('refuses a tower with no bells', async () => {
    expect((await ask('POST', '/towers', { name: 'Test', bells: [] })).status).toBe(400);
  });
});

describe('reading towers', () => {
  beforeEach(async () => {
    await ask('POST', '/towers', stMary);
  });

  it('lists them with a total', async () => {
    const response = await ask('GET', '/towers');
    expect(response.body.total).toBe(1);
  });

  it('reads one back', async () => {
    const response = await ask('GET', '/towers/bridgend-st-mary-the-virgin');
    expect(response.body.name).toBe('St Mary the Virgin');
  });

  it('answers 404 for one it does not hold', async () => {
    expect((await ask('GET', '/towers/nowhere')).status).toBe(404);
  });

  it('lists the bells with their weights', async () => {
    const response = await ask('GET', '/towers/bridgend-st-mary-the-virgin/bells');
    expect(response.body.bells).toHaveLength(6);
    expect(response.body.tenor).toBe('12-1-4');
    expect(response.body.bells[0].weight).toBe('4-0-0');
  });

  it('adds the ring up', async () => {
    const response = await ask('GET', '/towers/bridgend-st-mary-the-virgin/bells');
    expect(response.body.totalWeightPounds).toBeGreaterThan(0);
  });

  it('removes one', async () => {
    expect((await ask('DELETE', '/towers/bridgend-st-mary-the-virgin')).status).toBe(204);
    expect((await ask('GET', '/towers/bridgend-st-mary-the-virgin')).status).toBe(404);
  });

  it('answers 404 when removing one that is not there', async () => {
    expect((await ask('DELETE', '/towers/nowhere')).status).toBe(404);
  });
});

describe('towerFrom', () => {
  it('builds the class from a document', () => {
    expect(towerFrom(stMary).size).toBe(6);
  });

  it('refuses a document whose id disagrees with its name', () => {
    expect(() => towerFrom({ ...stMary, id: 'somewhere-else' })).toThrow(ValidationError);
  });
});

describe('towerId', () => {
  it('joins the place and the name', () => {
    expect(towerId(stMary)).toBe('bridgend-st-mary-the-virgin');
  });

  it('uses the name alone when there is no place', () => {
    expect(towerId({ name: 'Test Tower' })).toBe('test-tower');
  });
});
