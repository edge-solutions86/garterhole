import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { serve, environmentFor, SERVE_FLAGS } from '../../src/cli/commands/serve.js';
import { parseArgs } from '../../src/cli/args.js';
import { collectingOutput } from '../../src/cli/output.js';
import { runCollecting } from '../../src/cli/program.js';
import { buildApp } from '../../src/http/app.js';
import { silentLogger } from '../../src/util/logger.js';
import { ValidationError } from '../../src/errors.js';

/** @type {string} */
let directory;

beforeEach(() => {
  directory = mkdtempSync(join(tmpdir(), 'garterhole-'));
});

afterEach(() => {
  rmSync(directory, { recursive: true, force: true });
});

const args = (line) =>
  parseArgs(
    line.split(' ').filter((word) => word.length > 0),
    {
      known: SERVE_FLAGS,
    },
  );

describe('environmentFor', () => {
  it('lays the flags over the environment', () => {
    const environment = environmentFor(args('--port 9000 --host 0.0.0.0 --data /var/g'), {
      GARTERHOLE_LOG_LEVEL: 'warn',
    });
    expect(environment).toEqual({
      GARTERHOLE_LOG_LEVEL: 'warn',
      GARTERHOLE_PORT: '9000',
      GARTERHOLE_HOST: '0.0.0.0',
      GARTERHOLE_DATA: '/var/g',
    });
  });

  it('leaves the environment alone when no flags are given', () => {
    expect(environmentFor(args(''), { GARTERHOLE_PORT: '1234' })).toEqual({
      GARTERHOLE_PORT: '1234',
    });
  });

  it('ignores a flag written with no value', () => {
    expect(environmentFor(parseArgs(['--port']), {})).toEqual({});
  });
});

describe('serve', () => {
  it('starts the service and says where it is listening', async () => {
    const { output, out, err } = collectingOutput();
    const running = await serve(args(`--port 0 --data ${directory}`), { output });
    try {
      expect(running.port).toBeGreaterThan(0);
      expect(JSON.parse(out[0] ?? '{}').port).toBe(running.port);
      expect(err.join('\n')).toContain('listening on');
    } finally {
      await running.close();
    }
  });

  it('answers over the port it opened', async () => {
    const { output } = collectingOutput({ quiet: true });
    const running = await serve(args(`--port 0 --data ${directory}`), { output });
    try {
      const answer = await fetch(`http://127.0.0.1:${running.port}/health`);
      expect(answer.status).toBe(200);
    } finally {
      await running.close();
    }
  });

  it('takes a listener of its own, so a test need not open a port', async () => {
    const { output } = collectingOutput();
    let seen;
    const running = await serve(args(`--data ${directory}`), {
      output,
      listen: async (options) => {
        seen = options.environment;
        return { port: 4321, close: async () => {} };
      },
    });
    expect(running.port).toBe(4321);
    expect(seen.GARTERHOLE_DATA).toBe(directory);
  });

  it('refuses a port that is not one before opening anything', async () => {
    const { output } = collectingOutput();
    await expect(serve(args(`--port 70000 --data ${directory}`), { output })).rejects.toThrow(
      /must be a port number/,
    );
  });

  it('refuses to serve with nowhere to write to', async () => {
    await expect(serve(args(''), {})).rejects.toThrow(ValidationError);
  });
});

describe('serve as a command', () => {
  it('is listed in the help', async () => {
    const app = buildApp({ environment: { GARTERHOLE_DATA: directory }, logger: silentLogger() });
    try {
      const result = await runCollecting(['--help'], { app });
      expect(result.out.join('\n')).toContain('serve');
    } finally {
      app.close();
    }
  });
});
