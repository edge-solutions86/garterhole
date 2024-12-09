import { describe, it, expect } from 'vitest';
import {
  GarterholeError,
  ValidationError,
  NotationError,
  NotFoundError,
  ConflictError,
  StorageError,
  ConfigError,
  isGarterholeError,
} from '../src/errors.js';

describe('GarterholeError', () => {
  it('carries a default code', () => {
    const error = new GarterholeError('something went wrong');
    expect(error.code).toBe('garterhole_error');
    expect(error.name).toBe('GarterholeError');
    expect(error.message).toBe('something went wrong');
  });

  it('takes a code and details', () => {
    const error = new GarterholeError('nope', { code: 'custom', details: { bell: 7 } });
    expect(error.code).toBe('custom');
    expect(error.details).toEqual({ bell: 7 });
  });

  it('freezes the details so a handler cannot edit them', () => {
    const error = new GarterholeError('nope', { details: { bell: 7 } });
    expect(Object.isFrozen(error.details)).toBe(true);
  });

  it('keeps a cause when one is given', () => {
    const cause = new Error('disk');
    const error = new GarterholeError('wrapped', { cause });
    expect(error.cause).toBe(cause);
  });

  it('serialises to something an HTTP layer can send', () => {
    const error = new GarterholeError('nope', { code: 'custom', details: { bell: 7 } });
    expect(error.toJSON()).toEqual({
      name: 'GarterholeError',
      code: 'custom',
      message: 'nope',
      details: { bell: 7 },
    });
  });

  it('is a real Error', () => {
    expect(new GarterholeError('x')).toBeInstanceOf(Error);
    expect(new GarterholeError('x').stack).toBeTruthy();
  });
});

describe('ValidationError', () => {
  it('collects issues', () => {
    const error = new ValidationError('two problems', { issues: ['a', 'b'] });
    expect(error.code).toBe('validation_failed');
    expect(error.issues).toEqual(['a', 'b']);
    expect(error.toJSON().issues).toEqual(['a', 'b']);
  });

  it('defaults to no issues', () => {
    expect(new ValidationError('one problem').issues).toEqual([]);
  });
});

describe('NotationError', () => {
  it('records where the reader gave up', () => {
    const error = new NotationError('bad symbol', { source: '12x4', position: 3 });
    expect(error.code).toBe('notation_error');
    expect(error.position).toBe(3);
    expect(error.source).toBe('12x4');
    expect(error.details.position).toBe(3);
  });

  it('works without a position', () => {
    const error = new NotationError('blank');
    expect(error.position).toBeUndefined();
  });
});

describe('the remaining kinds', () => {
  it('names what was not found', () => {
    const error = new NotFoundError('method', 'abc');
    expect(error.code).toBe('not_found');
    expect(error.message).toBe('no method with id "abc"');
    expect(error.kind).toBe('method');
    expect(error.id).toBe('abc');
  });

  it('reports a conflict', () => {
    expect(new ConflictError('already there').code).toBe('conflict');
  });

  it('reports a storage failure and keeps the cause', () => {
    const cause = new Error('ENOENT');
    const error = new StorageError('cannot read journal', { cause });
    expect(error.code).toBe('storage_error');
    expect(error.cause).toBe(cause);
  });

  it('reports a configuration failure', () => {
    expect(new ConfigError('no port').code).toBe('config_error');
  });
});

describe('isGarterholeError', () => {
  it('recognises every kind in the taxonomy', () => {
    const errors = [
      new GarterholeError('a'),
      new ValidationError('b'),
      new NotationError('c'),
      new NotFoundError('method', 'd'),
      new ConflictError('e'),
      new StorageError('f'),
      new ConfigError('g'),
    ];
    for (const error of errors) {
      expect(isGarterholeError(error)).toBe(true);
    }
  });

  it('does not claim errors from elsewhere', () => {
    expect(isGarterholeError(new Error('plain'))).toBe(false);
    expect(isGarterholeError(new RangeError('guard'))).toBe(false);
    expect(isGarterholeError(null)).toBe(false);
    expect(isGarterholeError('boom')).toBe(false);
  });
});
