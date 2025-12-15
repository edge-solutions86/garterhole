import { describe, it, expect } from 'vitest';
import { Logger, collectingLogger, silentLogger } from '../../src/util/logger.js';
import { ValidationError } from '../../src/errors.js';

describe('writing lines', () => {
  it('writes one JSON object per line', () => {
    const { logger, records } = collectingLogger();
    logger.info('rang a quarter', { changes: 1260 });
    expect(records()).toEqual([
      {
        at: '1970-01-01T00:00:00.000Z',
        level: 'info',
        message: 'rang a quarter',
        changes: 1260,
      },
    ]);
  });

  it('says whether the line was written', () => {
    const { logger } = collectingLogger({ level: 'warn' });
    expect(logger.warn('careful')).toBe(true);
    expect(logger.info('ordinary')).toBe(false);
  });

  it('has a shortcut for each level', () => {
    const { logger, records } = collectingLogger();
    logger.debug('a');
    logger.info('b');
    logger.warn('c');
    expect(records().map((line) => line.level)).toEqual(['debug', 'info', 'warn']);
  });
});

describe('levels', () => {
  it('writes everything at or above the level', () => {
    const logger = new Logger({ level: 'warn' });
    expect(logger.writes('warn')).toBe(true);
    expect(logger.writes('error')).toBe(true);
    expect(logger.writes('info')).toBe(false);
  });

  it('writes nothing when silent', () => {
    const logger = silentLogger();
    expect(logger.writes('error')).toBe(false);
    expect(logger.error('boom')).toBe(false);
  });

  it('refuses a level nobody uses', () => {
    expect(() => new Logger({ level: 'chatty' })).toThrow(/is not a log level/);
    expect(() => new Logger().writes('chatty')).toThrow(ValidationError);
  });
});

describe('fields', () => {
  it('carries the fields it was built with', () => {
    const { logger, records } = collectingLogger();
    const tower = logger.with({ tower: 'St Mary' });
    tower.info('opened');
    expect(records()[0].tower).toBe('St Mary');
  });

  it('adds to the fields rather than replacing them', () => {
    const { logger, records } = collectingLogger();
    logger.with({ tower: 'St Mary' }).with({ request: 'abc' }).info('handled');
    expect(records()[0]).toMatchObject({ tower: 'St Mary', request: 'abc' });
  });

  it('lets a line override a carried field', () => {
    const { logger, records } = collectingLogger();
    logger.with({ tower: 'St Mary' }).info('moved', { tower: 'All Saints' });
    expect(records()[0].tower).toBe('All Saints');
  });

  it('keeps the level and the sink when it adds fields', () => {
    const { logger, records } = collectingLogger({ level: 'warn' });
    const child = logger.with({ a: 1 });
    expect(child.info('quiet')).toBe(false);
    expect(child.warn('loud')).toBe(true);
    expect(records()).toHaveLength(1);
  });
});

describe('errors', () => {
  it('unpacks an Error into fields', () => {
    const { logger, records } = collectingLogger();
    logger.error('could not read', new RangeError('bell 9 is outside 1..8'));
    expect(records()[0]).toMatchObject({
      level: 'error',
      message: 'could not read',
      error: 'bell 9 is outside 1..8',
      errorName: 'RangeError',
    });
  });

  it('keeps the code of a library error', () => {
    const { logger, records } = collectingLogger();
    const error = Object.assign(new Error('nope'), { code: 'validation_failed' });
    logger.error('refused', error);
    expect(records()[0].code).toBe('validation_failed');
  });

  it('takes something that is not an Error', () => {
    const { logger, records } = collectingLogger();
    logger.error('odd', 'a string');
    expect(records()[0].error).toBe('a string');
  });

  it('takes no error at all', () => {
    const { logger, records } = collectingLogger();
    logger.error('just a message');
    expect(records()[0].error).toBeUndefined();
  });
});
