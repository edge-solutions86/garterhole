import { describe, it, expect } from 'vitest';
import {
  statusFor,
  responseForError,
  catchingErrors,
  notFound,
  methodNotAllowed,
} from '../../src/http/middleware/errorHandler.js';
import { collectingLogger } from '../../src/util/logger.js';
import {
  ValidationError,
  NotationError,
  NotFoundError,
  ConflictError,
  StorageError,
  ConfigError,
} from '../../src/errors.js';

describe('statusFor', () => {
  it('maps the taxonomy onto status codes', () => {
    expect(statusFor(new NotFoundError('tower', 'x'))).toBe(404);
    expect(statusFor(new ConflictError('already there'))).toBe(409);
    expect(statusFor(new ValidationError('wrong'))).toBe(400);
    expect(statusFor(new NotationError('bad symbol'))).toBe(400);
  });

  it('treats a storage or config failure as ours', () => {
    expect(statusFor(new StorageError('disk'))).toBe(500);
    expect(statusFor(new ConfigError('port'))).toBe(500);
  });

  it('treats a guard failure as the caller sending nonsense', () => {
    expect(statusFor(new RangeError('bell 9 is outside 1..8'))).toBe(400);
    expect(statusFor(new TypeError('must be a Row'))).toBe(400);
  });

  it('treats anything else as a bug', () => {
    expect(statusFor(new Error('boom'))).toBe(500);
    expect(statusFor('boom')).toBe(500);
  });
});

describe('responseForError', () => {
  it('sends the message back for a rejected request', () => {
    const response = responseForError(new NotFoundError('tower', 'x'));
    expect(response.status).toBe(404);
    expect(response.body.error).toEqual({ code: 'not_found', message: 'no tower with id "x"' });
  });

  it('carries the issues of a validation failure', () => {
    const error = new ValidationError('the request is wrong', {
      issues: [{ path: 'name', message: 'must be a string' }],
    });
    expect(responseForError(error).body.error.issues).toHaveLength(1);
  });

  it('hides the details of a bug', () => {
    const response = responseForError(new Error('the database password is hunter2'));
    expect(response.status).toBe(500);
    expect(response.body.error.message).toBe('something went wrong inside the service');
  });

  it('logs a bug with the error attached', () => {
    const { logger, records } = collectingLogger();
    responseForError(new Error('boom'), { logger, requestId: 'req_1' });
    expect(records()[0]).toMatchObject({ level: 'error', error: 'boom', requestId: 'req_1' });
  });

  it('logs a refusal quietly', () => {
    const { logger, records } = collectingLogger();
    responseForError(new ValidationError('wrong'), { logger, requestId: 'req_1' });
    expect(records()[0].level).toBe('debug');
  });

  it('takes something that is not an Error', () => {
    expect(responseForError(new RangeError('outside 1..8')).body.error.code).toBe('bad_request');
  });
});

describe('catchingErrors', () => {
  it('lets a good answer through', async () => {
    const wrapped = catchingErrors(() => 'answered');
    await expect(wrapped({})).resolves.toBe('answered');
  });

  it('turns a throw into a response', async () => {
    const wrapped = catchingErrors(() => {
      throw new NotFoundError('tower', 'x');
    });
    const response = await wrapped({ id: 'req_1' });
    expect(response.status).toBe(404);
  });

  it('turns a rejected promise into a response', async () => {
    const wrapped = catchingErrors(async () => {
      throw new ValidationError('wrong');
    });
    expect((await wrapped({})).status).toBe(400);
  });

  it('passes the request identifier to the log', async () => {
    const { logger, records } = collectingLogger();
    const wrapped = catchingErrors(
      () => {
        throw new Error('boom');
      },
      { logger },
    );
    await wrapped({ id: 'req_9' });
    expect(records()[0].requestId).toBe('req_9');
  });
});

describe('the routing answers', () => {
  it('says nothing is served at a path', () => {
    const response = notFound('/nowhere');
    expect(response.status).toBe(404);
    expect(response.body.error.message).toBe('nothing is served at /nowhere');
  });

  it('lists the methods a path does answer to', () => {
    const response = methodNotAllowed('POST', ['GET', 'DELETE']);
    expect(response.status).toBe(405);
    expect(response.headers.allow).toBe('GET, DELETE');
  });
});
