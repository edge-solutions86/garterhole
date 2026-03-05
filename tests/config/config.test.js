import { describe, it, expect } from 'vitest';
import { loadConfig, describeConfig, settingsList, LOG_LEVELS } from '../../src/config/config.js';
import { ConfigError } from '../../src/errors.js';

describe('defaults', () => {
  it('starts with settings that work', () => {
    const config = loadConfig();
    expect(config.port).toBe(8080);
    expect(config.host).toBe('127.0.0.1');
    expect(config.logLevel).toBe('info');
    expect(config.readOnly).toBe(false);
  });

  it('builds the paths from the data directory', () => {
    const config = loadConfig({ GARTERHOLE_DATA: '/var/garterhole' });
    expect(config.journalPath).toContain('journal.jsonl');
    expect(config.snapshotPath).toContain('snapshot.json');
    expect(config.lockPath).toContain('lock');
  });

  it('treats an empty variable as unset', () => {
    expect(loadConfig({ GARTERHOLE_PORT: '' }).port).toBe(8080);
  });
});

describe('reading the environment', () => {
  it('reads a port', () => {
    expect(loadConfig({ GARTERHOLE_PORT: '9000' }).port).toBe(9000);
  });

  it('reads a flag', () => {
    expect(loadConfig({ GARTERHOLE_READ_ONLY: 'yes' }).readOnly).toBe(true);
    expect(loadConfig({ GARTERHOLE_READ_ONLY: 'off' }).readOnly).toBe(false);
  });

  it('reads a log level whatever case it is written in', () => {
    expect(loadConfig({ GARTERHOLE_LOG_LEVEL: 'WARN' }).logLevel).toBe('warn');
  });

  it('reads a rate limit', () => {
    expect(loadConfig({ GARTERHOLE_RATE_LIMIT: '30' }).requestsPerMinute).toBe(30);
  });
});

describe('refusing bad settings', () => {
  it('names the variable that is wrong', () => {
    expect(() => loadConfig({ GARTERHOLE_PORT: 'eight thousand' })).toThrow(
      /GARTERHOLE_PORT is wrong/,
    );
  });

  it('refuses a port outside the range', () => {
    expect(() => loadConfig({ GARTERHOLE_PORT: '-1' })).toThrow(/must be a port number, not -1/);
    expect(() => loadConfig({ GARTERHOLE_PORT: '70000' })).toThrow(ConfigError);
  });

  it('allows port zero, which means let the machine choose', () => {
    expect(loadConfig({ GARTERHOLE_PORT: '0' }).port).toBe(0);
  });

  it('refuses a rate limit that allows nothing', () => {
    expect(() => loadConfig({ GARTERHOLE_RATE_LIMIT: '0' })).toThrow(/at least one request/);
  });

  it('refuses a log level nobody uses', () => {
    expect(() => loadConfig({ GARTERHOLE_LOG_LEVEL: 'chatty' })).toThrow(/must be one of/);
  });

  it('refuses a flag that is not one', () => {
    expect(() => loadConfig({ GARTERHOLE_READ_ONLY: 'maybe' })).toThrow(ConfigError);
  });
});

describe('describeConfig', () => {
  it('says what the service is doing', () => {
    const line = describeConfig(loadConfig());
    expect(line).toContain('listening on 127.0.0.1:8080');
    expect(line).toContain('accepting writes');
  });

  it('says when it is read only', () => {
    expect(describeConfig(loadConfig({ GARTERHOLE_READ_ONLY: 'true' }))).toContain('read only');
  });
});

describe('settingsList', () => {
  it('names every setting and its variable', () => {
    const settings = settingsList();
    expect(settings.map((setting) => setting.name)).toContain('port');
    expect(settings.every((setting) => setting.variable.startsWith('GARTERHOLE_'))).toBe(true);
  });

  it('lists the log levels quietest last', () => {
    expect(LOG_LEVELS[LOG_LEVELS.length - 1]).toBe('silent');
  });
});
