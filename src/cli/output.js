import { tabulate } from '../util/text.js';
import { ValidationError } from '../errors.js';

/**
 * What the command line prints.
 *
 * Two shapes, chosen by a flag. A person gets a table; a script gets JSON. Both
 * come out of the same command, so nobody has to parse the table.
 *
 * The writer takes its sinks rather than reaching for the process, which is
 * what lets the tests read what a command printed without capturing the
 * standard output of the whole run.
 */
export class Output {
  /**
   * @param {{
   *   out?: (line: string) => void,
   *   err?: (line: string) => void,
   *   json?: boolean,
   *   quiet?: boolean
   * }} [options]
   */
  constructor(options = {}) {
    this.out = options.out ?? (() => {});
    this.err = options.err ?? (() => {});
    this.json = options.json === true;
    this.quiet = options.quiet === true;
  }

  /**
   * Print an answer: as JSON when that was asked for, and otherwise by handing
   * the value to the writer the caller supplied.
   *
   * @param {unknown} value
   * @param {(value: unknown) => string[]} [asLines]
   */
  answer(value, asLines) {
    if (this.json) {
      this.out(JSON.stringify(value, null, 2));
      return;
    }
    if (asLines === undefined) {
      this.out(typeof value === 'string' ? value : JSON.stringify(value));
      return;
    }
    for (const line of asLines(value)) {
      this.out(line);
    }
  }

  /**
   * Print a note, which a quiet run leaves out. Notes go to the error stream so
   * that a script reading the answer is not given them by accident.
   *
   * @param {string} message
   */
  note(message) {
    if (!this.quiet) {
      this.err(message);
    }
  }

  /**
   * Print a problem. A problem is printed however quiet the run is.
   *
   * @param {string} message
   */
  problem(message) {
    this.err(message);
  }

  /**
   * Print a table.
   *
   * @param {readonly (readonly string[])[]} rows
   * @param {{ align?: readonly ('left' | 'right')[] }} [options]
   */
  table(rows, options = {}) {
    if (!Array.isArray(rows)) {
      throw new ValidationError('a table needs rows');
    }
    if (this.json) {
      this.out(JSON.stringify(rows));
      return;
    }
    for (const line of tabulate(rows, options)) {
      this.out(line);
    }
  }
}

/**
 * An output that collects what was printed, which is what the tests use.
 *
 * @param {{ json?: boolean, quiet?: boolean }} [options]
 * @returns {{ output: Output, out: string[], err: string[] }}
 */
export function collectingOutput(options = {}) {
  const out = [];
  const err = [];
  const output = new Output({
    out: (line) => out.push(line),
    err: (line) => err.push(line),
    json: options.json,
    quiet: options.quiet,
  });
  return { output, out, err };
}

/**
 * The output a real run uses.
 *
 * @param {{ json?: boolean, quiet?: boolean, stdout?: object, stderr?: object }} [options]
 * @returns {Output}
 */
export function consoleOutput(options = {}) {
  const stdout = options.stdout ?? process.stdout;
  const stderr = options.stderr ?? process.stderr;
  return new Output({
    out: (line) => stdout.write(`${line}\n`),
    err: (line) => stderr.write(`${line}\n`),
    json: options.json,
    quiet: options.quiet,
  });
}
