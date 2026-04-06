import { parseArgs, flagIsSet } from './args.js';
import { collectingOutput, consoleOutput } from './output.js';
import {
  addMethod,
  listMethods,
  showMethod,
  removeMethod,
  METHOD_FLAGS,
} from './commands/method.js';
import { prove, printLead, PROVE_FLAGS } from './commands/prove.js';
import {
  fileRecord,
  listRecords,
  reportRecords,
  printBoard,
  RECORD_FLAGS,
} from './commands/records.js';
import { buildApp, start } from '../http/app.js';
import { buildLabel } from '../version.js';
import { isGarterholeError } from '../errors.js';

/**
 * The command line.
 *
 * Every command is a name, a line of help and a function. The dispatcher does
 * nothing but find the function and hand it the parsed arguments, which means a
 * new command is one entry in the table and a test.
 *
 * Nothing here calls `process.exit`. `run` gives back a status and the entry
 * point decides what to do with it, which is what lets the tests run every
 * command in the same process.
 */

/** Every command, in the order the help lists them. */
const COMMANDS = Object.freeze([
  {
    name: 'method add',
    help: 'add a method from its name, stage and notation',
    flags: METHOD_FLAGS,
    run: addMethod,
  },
  { name: 'method list', help: 'list the method library', flags: METHOD_FLAGS, run: listMethods },
  { name: 'method show', help: 'describe one method', flags: METHOD_FLAGS, run: showMethod },
  {
    name: 'method remove',
    help: 'take a method out of the library',
    flags: METHOD_FLAGS,
    run: removeMethod,
  },
  {
    name: 'prove',
    help: 'ring a touch and say whether it is true',
    flags: PROVE_FLAGS,
    run: prove,
  },
  { name: 'lead', help: 'print one lead of a method', flags: PROVE_FLAGS, run: printLead },
  {
    name: 'records file',
    help: 'file a performance',
    flags: RECORD_FLAGS,
    run: fileRecord,
  },
  { name: 'records list', help: 'list the performances', flags: RECORD_FLAGS, run: listRecords },
  {
    name: 'records report',
    help: 'write a report of the performances',
    flags: RECORD_FLAGS,
    run: reportRecords,
  },
  {
    name: 'records board',
    help: 'print a performance as a board',
    flags: RECORD_FLAGS,
    run: printBoard,
  },
]);

/** The flags every command understands. */
const GLOBAL_FLAGS = Object.freeze({ json: {}, quiet: {}, help: {}, version: {} });

/**
 * Find the command a line names, longest name first so that `method add` beats
 * `method`.
 *
 * @param {readonly string[]} argv
 * @returns {{ command: object, argv: string[] } | null}
 */
export function findCommand(argv) {
  const words = argv.filter((word) => !String(word).startsWith('-'));
  for (const command of [...COMMANDS].sort((left, right) => right.name.length - left.name.length)) {
    const parts = command.name.split(' ');
    if (parts.every((part, index) => words[index] === part)) {
      const remaining = [...argv];
      for (const part of parts) {
        remaining.splice(remaining.indexOf(part), 1);
      }
      return { command, argv: remaining };
    }
  }
  return null;
}

/**
 * The help text.
 *
 * @returns {string[]}
 */
export function helpLines() {
  const width = Math.max(...COMMANDS.map((command) => command.name.length));
  return [
    `${buildLabel()} - tooling for change ringing bands`,
    '',
    'usage: garterhole <command> [flags]',
    '',
    ...COMMANDS.map((command) => `  ${command.name.padEnd(width)}  ${command.help}`),
    '',
    '  serve                 start the HTTP service',
    '',
    'flags: --json for machine readable output, --quiet to say less',
  ];
}

/**
 * Run a command line.
 *
 * @param {readonly string[]} argv
 * @param {{
 *   output?: import('./output.js').Output,
 *   environment?: Record<string, string | undefined>,
 *   app?: object
 * }} [options]
 * @returns {Promise<number>} the status to exit with
 */
export async function run(argv, options = {}) {
  const output =
    options.output ??
    consoleOutput({ json: argv.includes('--json'), quiet: argv.includes('--quiet') });
  if (argv.length === 0 || argv.includes('--help')) {
    for (const line of helpLines()) {
      output.answer(line);
    }
    return 0;
  }
  if (argv.includes('--version')) {
    output.answer(buildLabel());
    return 0;
  }
  if (argv[0] === 'serve') {
    const running = await start({ environment: options.environment });
    output.note(`listening on port ${running.port}`);
    return 0;
  }
  const found = findCommand(argv);
  if (found === null) {
    output.problem(`there is no command called ${JSON.stringify(argv.join(' '))}`);
    return 2;
  }
  const app = options.app ?? buildApp({ environment: options.environment, logger: undefined });
  try {
    const parsed = parseArgs(found.argv, {
      known: { ...GLOBAL_FLAGS, ...found.command.flags },
    });
    await found.command.run(parsed, { service: app.service, output });
    return 0;
  } catch (error) {
    output.problem(isGarterholeError(error) ? error.message : String(error?.message ?? error));
    return 1;
  } finally {
    if (options.app === undefined) {
      app.close();
    }
  }
}

/**
 * Run a command and collect what it printed, which is what the tests use.
 *
 * @param {readonly string[]} argv
 * @param {object} [options]
 * @returns {Promise<{ status: number, out: string[], err: string[] }>}
 */
export async function runCollecting(argv, options = {}) {
  const { output, out, err } = collectingOutput({
    json: argv.includes('--json'),
    quiet: flagIsSet(parseArgs(argv.filter((word) => word.startsWith('--'))), 'quiet'),
  });
  const status = await run(argv, { ...options, output });
  return { status, out, err };
}

/**
 * Every command name, for the documentation.
 *
 * @returns {string[]}
 */
export function commandNames() {
  return COMMANDS.map((command) => command.name);
}
