import { describe, it, expect } from 'vitest';
import { parseArgs, requireFlag, requireNumber, flagIsSet } from '../../src/cli/args.js';
import { ValidationError } from '../../src/errors.js';

const known = {
  stage: { takesValue: true, short: 's' },
  name: { takesValue: true },
  notation: { takesValue: true },
  quiet: {},
};

describe('reading a command', () => {
  it('takes the words before the first flag as the command', () => {
    expect(parseArgs(['method', 'add', '--name', 'Plain'], { known }).command).toEqual([
      'method',
      'add',
    ]);
  });

  it('gives an empty command when there is none', () => {
    expect(parseArgs([]).command).toEqual([]);
  });

  it('puts words after a flag in the rest', () => {
    const parsed = parseArgs(['prove', '--quiet', 'extra'], { known });
    expect(parsed.command).toEqual(['prove']);
    expect(parsed.rest).toEqual(['extra']);
  });

  it('puts everything after a bare double dash in the rest', () => {
    const parsed = parseArgs(['run', '--', '--not-a-flag', 'x'], { known });
    expect(parsed.rest).toEqual(['--not-a-flag', 'x']);
  });

  it('refuses something that is not a list', () => {
    expect(() => parseArgs('method add')).toThrow(/must be a list of arguments/);
  });
});

describe('reading flags', () => {
  it('reads a flag and its value', () => {
    expect(parseArgs(['--stage', '8'], { known }).flags).toEqual({ stage: '8' });
  });

  it('reads a flag written with an equals sign', () => {
    expect(parseArgs(['--stage=8'], { known }).flags).toEqual({ stage: '8' });
  });

  it('reads a short name', () => {
    expect(parseArgs(['-s', '8'], { known }).flags).toEqual({ stage: '8' });
  });

  it('reads a flag with no value as true', () => {
    expect(parseArgs(['--quiet'], { known }).flags).toEqual({ quiet: true });
  });

  it('reads several flags', () => {
    const parsed = parseArgs(['--name', 'Plain', '--stage', '8', '--quiet'], { known });
    expect(parsed.flags).toEqual({ name: 'Plain', stage: '8', quiet: true });
  });

  it('refuses a flag nobody declared', () => {
    expect(() => parseArgs(['--colour', 'blue'], { known })).toThrow(
      'there is no flag called --colour',
    );
  });

  it('refuses a flag that needs a value and has none', () => {
    expect(() => parseArgs(['--stage'], { known })).toThrow('--stage needs a value');
    expect(() => parseArgs(['--stage', '--quiet'], { known })).toThrow(ValidationError);
  });

  it('takes any flag when none are declared', () => {
    expect(parseArgs(['--anything=1']).flags).toEqual({ anything: '1' });
  });

  it('keeps a value that looks like a negative number', () => {
    expect(parseArgs(['--stage', '-1'], { known }).flags.stage).toBe('-1');
  });
});

describe('requireFlag', () => {
  const parsed = () => parseArgs(['--name', 'Plain', '--quiet'], { known });

  it('gives the value', () => {
    expect(requireFlag(parsed(), 'name')).toBe('Plain');
  });

  it('falls back when there is one', () => {
    expect(requireFlag(parsed(), 'stage', '8')).toBe('8');
  });

  it('complains when there is neither', () => {
    expect(() => requireFlag(parsed(), 'stage')).toThrow('this command needs --stage');
  });

  it('treats a flag with no value as missing', () => {
    expect(() => requireFlag(parsed(), 'quiet')).toThrow(ValidationError);
  });
});

describe('requireNumber', () => {
  it('reads a whole number', () => {
    expect(requireNumber(parseArgs(['--stage', '8'], { known }), 'stage')).toBe(8);
  });

  it('falls back when there is one', () => {
    expect(requireNumber(parseArgs([], { known }), 'stage', 6)).toBe(6);
  });

  it('complains about a value that is not a whole number', () => {
    expect(() => requireNumber(parseArgs(['--stage', 'eight'], { known }), 'stage')).toThrow(
      /--stage must be a whole number/,
    );
  });

  it('complains when it is missing and there is no fallback', () => {
    expect(() => requireNumber(parseArgs([], { known }), 'stage')).toThrow(/needs --stage/);
  });
});

describe('flagIsSet', () => {
  it('is true for a flag written on its own', () => {
    expect(flagIsSet(parseArgs(['--quiet'], { known }), 'quiet')).toBe(true);
  });

  it('is true for a flag written as true', () => {
    expect(flagIsSet(parseArgs(['--quiet=true'], { known }), 'quiet')).toBe(true);
  });

  it('is false for one that was not given', () => {
    expect(flagIsSet(parseArgs([], { known }), 'quiet')).toBe(false);
  });
});
