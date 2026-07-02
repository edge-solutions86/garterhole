import { describe, it, expect, beforeEach } from 'vitest';
import { addStrikingRoutes, windowRowsOf } from '../../src/http/routes/striking.js';
import { Router } from '../../src/http/router.js';
import { Server } from '../../src/http/server.js';
import { Request } from '../../src/http/request.js';
import { TimingModel } from '../../src/striking/timingModel.js';
import { plainCourse } from '../../src/method/plainCourse.js';
import { method } from '../../src/method/method.js';
import { ValidationError } from '../../src/errors.js';

/** @type {Server} */
let server;

beforeEach(() => {
  const router = addStrikingRoutes(new Router());
  server = new Server({ router, context: { router } });
});

const ask = (url, body) =>
  server.handle(
    new Request({ method: 'POST', url, body, headers: { 'content-type': 'application/json' } }),
  );

function strikes(shift = () => 0) {
  const block = plainCourse(method('Plain', 6, 'x1x1x1,2'));
  const model = new TimingModel({ stage: 6, interval: 250 });
  const made = [];
  block.rows.forEach((row, rowIndex) => {
    row.bells.forEach((bell, place) => {
      made.push({ bell, at: model.expectedAt(rowIndex, place + 1) + shift(bell, rowIndex) });
    });
  });
  return made;
}

const recording = (shift) => ({ stage: 6, strikes: strikes(shift) });

describe('analysing a recording', () => {
  it('answers with the whole analysis', async () => {
    const response = await ask('/striking', recording());
    expect(response.status).toBe(200);
    expect(response.body.format).toBe('garterhole.striking');
    expect(response.body.rows).toBe(61);
  });

  it('reports the accuracy and every bell', async () => {
    const response = await ask('/striking', recording());
    expect(response.body.accuracy.rms).toBeCloseTo(0, 1);
    expect(response.body.bells).toHaveLength(6);
  });

  it('finds the bell that struck badly', async () => {
    const response = await ask(
      '/striking',
      recording((bell) => (bell === 4 ? 120 : 0)),
    );
    expect(response.body.faults.join(' ')).toContain('bell 4');
  });

  it('takes a window size', async () => {
    const response = await ask('/striking?window=10', recording());
    expect(response.body.windows.length).toBeGreaterThan(3);
  });

  it('refuses a window it will not measure', async () => {
    const response = await ask('/striking?window=0', recording());
    expect(response.status).toBe(400);
  });

  it('refuses a recording with a bell that is not on the stage', async () => {
    const response = await ask('/striking', {
      stage: 6,
      strikes: [
        { bell: 9, at: 0 },
        { bell: 1, at: 1 },
      ],
    });
    expect(response.status).toBe(400);
  });

  it('refuses a recording with nothing in it', async () => {
    expect((await ask('/striking', { stage: 6, strikes: [] })).status).toBe(400);
  });

  it('refuses a body that is not a recording', async () => {
    expect((await ask('/striking', { stage: 6 })).status).toBe(400);
  });
});

describe('a recording that stops making sense', () => {
  const broken = () => ({
    stage: 6,
    strikes: [
      ...strikes().slice(0, 12),
      ...[1, 1, 3, 4, 5, 6].map((bell, index) => ({ bell, at: 90000 + index * 250 })),
    ],
  });

  it('analyses what it can by default', async () => {
    const response = await ask('/striking', broken());
    expect(response.status).toBe(200);
    expect(response.body.stoppedAt).toBe(2);
  });

  it('refuses the whole thing when told not to be lenient', async () => {
    expect((await ask('/striking?lenient=false', broken())).status).toBe(400);
  });
});

describe('the other shapes', () => {
  it('writes a report a band can read', async () => {
    const response = await ask('/striking/report', recording());
    expect(response.headers['content-type']).toContain('text/plain');
    expect(response.body).toContain('rows rung');
  });

  it('writes the blows as a spreadsheet', async () => {
    const response = await ask('/striking/blows.csv', recording());
    expect(response.headers['content-type']).toContain('text/csv');
    expect(response.body.split('\n')[0]).toBe('row,place,bell,at,expected,error');
  });

  it('writes the bells as a spreadsheet', async () => {
    const response = await ask('/striking/bells.csv', recording());
    expect(response.body.split('\n')).toHaveLength(7);
  });
});

describe('windowRowsOf', () => {
  it('defaults to twenty rows', () => {
    expect(windowRowsOf(new Request({ url: '/striking' }))).toBe(20);
  });

  it('takes a size from the query', () => {
    expect(windowRowsOf(new Request({ url: '/striking?window=40' }))).toBe(40);
  });

  it('refuses a size it will not measure', () => {
    expect(() => windowRowsOf(new Request({ url: '/striking?window=0' }))).toThrow(ValidationError);
    expect(() => windowRowsOf(new Request({ url: '/striking?window=lots' }))).toThrow(
      /is not one this will measure/,
    );
  });
});
