import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdtempSync, rmSync, existsSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { buildApp, start, routeGroups } from '../../src/http/app.js';
import { Request } from '../../src/http/request.js';
import { silentLogger } from '../../src/util/logger.js';

/** @type {string} */
let directory;

beforeEach(() => {
  directory = mkdtempSync(join(tmpdir(), 'garterhole-'));
});

afterEach(() => {
  rmSync(directory, { recursive: true, force: true });
});

const build = (extra = {}) =>
  buildApp({
    environment: { GARTERHOLE_DATA: directory, GARTERHOLE_LOG_LEVEL: 'silent' },
    logger: silentLogger(),
    ...extra,
  });

describe('building the application', () => {
  it('opens the store where the settings say', () => {
    const app = build();
    try {
      expect(app.config.journalPath).toContain(directory);
      expect(app.repository).toBeDefined();
    } finally {
      app.close();
    }
  });

  it('registers every group of routes', () => {
    const app = build();
    try {
      const patterns = app.router.list().map((route) => route.pattern);
      expect(patterns).toContain('/health');
      expect(patterns).toContain('/methods');
      expect(patterns).toContain('/towers');
      expect(patterns).toContain('/performances');
      expect(patterns).toContain('/striking');
    } finally {
      app.close();
    }
  });

  it('names the groups it registers', () => {
    expect(routeGroups()).toEqual(['health', 'methods', 'towers', 'performances', 'striking']);
  });

  it('takes the lock while it is running', () => {
    const app = build();
    try {
      expect(existsSync(app.config.lockPath)).toBe(true);
    } finally {
      app.close();
    }
  });

  it('refuses to start twice on one data directory', () => {
    const first = build();
    try {
      expect(() => build()).toThrow(/is held by process/);
    } finally {
      first.close();
    }
  });

  it('gives the lock up when it closes', () => {
    const app = build();
    app.close();
    expect(existsSync(app.config.lockPath)).toBe(false);
  });

  it('can be built without taking the lock', () => {
    const app = build({ lock: false });
    try {
      expect(existsSync(app.config.lockPath)).toBe(false);
    } finally {
      app.close();
    }
  });
});

describe('the application answering', () => {
  it('answers health', async () => {
    const app = build();
    try {
      const response = await app.server.handle(new Request({ url: '/health' }));
      expect(response.status).toBe(200);
    } finally {
      app.close();
    }
  });

  it('files a method and reads it back', async () => {
    const app = build();
    try {
      const filed = await app.server.handle(
        new Request({
          method: 'POST',
          url: '/methods',
          headers: { 'content-type': 'application/json' },
          body: { name: 'Plain', stage: 8, notation: 'x18x18x18x18,12' },
        }),
      );
      expect(filed.status).toBe(201);
      const read = await app.server.handle(new Request({ url: '/methods' }));
      expect(read.body.total).toBe(1);
    } finally {
      app.close();
    }
  });

  it('keeps what was filed across a restart', async () => {
    const first = build();
    await first.server.handle(
      new Request({
        method: 'POST',
        url: '/methods',
        headers: { 'content-type': 'application/json' },
        body: { name: 'Plain', stage: 8, notation: 'x18x18x18x18,12' },
      }),
    );
    first.close();
    const second = build();
    try {
      expect(second.service.counts().methods).toBe(1);
    } finally {
      second.close();
    }
  });
});

describe('start', () => {
  it('listens and stops', async () => {
    const running = await start({
      environment: { GARTERHOLE_DATA: directory, GARTERHOLE_PORT: '0' },
      logger: silentLogger(),
    });
    try {
      const answer = await fetch(`http://127.0.0.1:${running.port}/health`);
      expect(answer.status).toBe(200);
    } finally {
      await running.close();
    }
    expect(existsSync(join(directory, 'lock'))).toBe(false);
  });
});
