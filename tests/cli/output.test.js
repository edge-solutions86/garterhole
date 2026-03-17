import { describe, it, expect } from 'vitest';
import { Output, collectingOutput, consoleOutput } from '../../src/cli/output.js';
import { ValidationError } from '../../src/errors.js';

describe('answering', () => {
  it('prints JSON when that was asked for', () => {
    const { output, out } = collectingOutput({ json: true });
    output.answer({ a: 1 });
    expect(JSON.parse(out.join('\n'))).toEqual({ a: 1 });
  });

  it('uses the writer it was given when it is not printing JSON', () => {
    const { output, out } = collectingOutput();
    output.answer({ a: 1 }, (value) => [`a is ${value.a}`]);
    expect(out).toEqual(['a is 1']);
  });

  it('prints a string as it is when there is no writer', () => {
    const { output, out } = collectingOutput();
    output.answer('hello');
    expect(out).toEqual(['hello']);
  });

  it('falls back to JSON on one line for a value with no writer', () => {
    const { output, out } = collectingOutput();
    output.answer({ a: 1 });
    expect(out).toEqual(['{"a":1}']);
  });

  it('ignores the writer when printing JSON', () => {
    const { output, out } = collectingOutput({ json: true });
    output.answer({ a: 1 }, () => ['never printed']);
    expect(out.join('')).toContain('"a": 1');
  });
});

describe('notes and problems', () => {
  it('sends a note to the error stream', () => {
    const { output, out, err } = collectingOutput();
    output.note('filed');
    expect(err).toEqual(['filed']);
    expect(out).toEqual([]);
  });

  it('leaves notes out of a quiet run', () => {
    const { output, err } = collectingOutput({ quiet: true });
    output.note('filed');
    expect(err).toEqual([]);
  });

  it('prints a problem however quiet the run is', () => {
    const { output, err } = collectingOutput({ quiet: true });
    output.problem('that did not work');
    expect(err).toEqual(['that did not work']);
  });
});

describe('tables', () => {
  it('lines the columns up', () => {
    const { output, out } = collectingOutput();
    output.table([
      ['title', 'stage'],
      ['Plain Bob Major', '8'],
    ]);
    expect(out[0]).toBe('title            stage');
  });

  it('prints the rows as JSON when that was asked for', () => {
    const { output, out } = collectingOutput({ json: true });
    output.table([['a', 'b']]);
    expect(JSON.parse(out[0])).toEqual([['a', 'b']]);
  });

  it('right aligns the columns it is told to', () => {
    const { output, out } = collectingOutput();
    output.table(
      [
        ['a', '1'],
        ['bb', '22'],
      ],
      { align: ['left', 'right'] },
    );
    expect(out[1]).toBe('bb  22');
  });

  it('refuses something that is not rows', () => {
    expect(() => collectingOutput().output.table('rows')).toThrow(ValidationError);
  });
});

describe('the sinks', () => {
  it('writes nowhere by default', () => {
    expect(() => new Output().answer('hello')).not.toThrow();
  });

  it('writes to the streams it is given', () => {
    const written = [];
    const output = consoleOutput({
      stdout: { write: (text) => written.push(text) },
      stderr: { write: (text) => written.push(text) },
    });
    output.answer('hello');
    output.note('a note');
    expect(written).toEqual(['hello\n', 'a note\n']);
  });
});
