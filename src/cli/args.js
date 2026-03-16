import { ValidationError } from '../errors.js';

/**
 * Reading a command line.
 *
 * The shape is the one every tool this replaces uses: a command, then flags,
 * then whatever is left over. `garterhole method add --stage 8 --name Plain`
 * reads as the command `method add` with two flags.
 *
 * A flag that takes no value is written on its own and reads as true. A flag
 * that takes one is written either as `--stage 8` or as `--stage=8`, because
 * both are what people type and refusing one of them wins nothing.
 *
 * An unknown flag is an error rather than being ignored. A typed flag that is
 * silently dropped is how somebody ends up believing they filed a peal with a
 * conductor when they did not.
 */

/**
 * @typedef {{
 *   command: string[],
 *   flags: Record<string, string | boolean>,
 *   rest: string[]
 * }} ParsedArgs
 */

/**
 * Read a command line.
 *
 * @param {readonly string[]} argv the arguments, without the runtime and script
 * @param {{ known?: Record<string, { takesValue?: boolean, short?: string }> }} [options]
 * @returns {ParsedArgs}
 */
export function parseArgs(argv, options = {}) {
  if (!Array.isArray(argv)) {
    throw new ValidationError('a command line must be a list of arguments');
  }
  const known = options.known;
  const shortNames = shortNamesOf(known);
  const command = [];
  /** @type {Record<string, string | boolean>} */
  const flags = {};
  const rest = [];
  let sawFlag = false;
  let index = 0;
  while (index < argv.length) {
    const argument = String(argv[index]);
    if (argument === '--') {
      rest.push(...argv.slice(index + 1).map((entry) => String(entry)));
      break;
    }
    if (argument.startsWith('-') && argument !== '-') {
      sawFlag = true;
      index = readFlag(argv, index, argument, flags, known, shortNames);
      continue;
    }
    if (sawFlag) {
      rest.push(argument);
    } else {
      command.push(argument);
    }
    index += 1;
  }
  return { command, flags, rest };
}

/**
 * A flag as a string, with a complaint naming it when it is missing.
 *
 * @param {ParsedArgs} parsed
 * @param {string} name
 * @param {string} [fallback]
 * @returns {string}
 */
export function requireFlag(parsed, name, fallback) {
  const value = parsed.flags[name];
  if (value === undefined || value === true) {
    if (fallback !== undefined) {
      return fallback;
    }
    throw new ValidationError(`this command needs --${name}`);
  }
  return String(value);
}

/**
 * A flag as a whole number.
 *
 * @param {ParsedArgs} parsed
 * @param {string} name
 * @param {number} [fallback]
 * @returns {number}
 */
export function requireNumber(parsed, name, fallback) {
  const raw = parsed.flags[name];
  if (raw === undefined || raw === true) {
    if (fallback !== undefined) {
      return fallback;
    }
    throw new ValidationError(`this command needs --${name}`);
  }
  const value = Number(raw);
  if (!Number.isInteger(value)) {
    throw new ValidationError(`--${name} must be a whole number, not ${JSON.stringify(raw)}`);
  }
  return value;
}

/**
 * @param {ParsedArgs} parsed
 * @param {string} name
 * @returns {boolean}
 */
export function flagIsSet(parsed, name) {
  return parsed.flags[name] === true || parsed.flags[name] === 'true';
}

/**
 * @param {readonly string[]} argv
 * @param {number} index
 * @param {string} argument
 * @param {Record<string, string | boolean>} flags
 * @param {Record<string, { takesValue?: boolean }> | undefined} known
 * @param {Map<string, string>} shortNames
 * @returns {number} the index to carry on from
 */
function readFlag(argv, index, argument, flags, known, shortNames) {
  const withoutDashes = argument.replace(/^-+/, '');
  const [rawName, inlineValue] = splitOnce(withoutDashes, '=');
  const name = shortNames.get(rawName) ?? rawName;
  if (known !== undefined && known[name] === undefined) {
    throw new ValidationError(`there is no flag called --${rawName}`);
  }
  const takesValue =
    known === undefined ? inlineValue !== undefined : known[name].takesValue === true;
  if (!takesValue) {
    flags[name] = inlineValue === undefined ? true : inlineValue;
    return index + 1;
  }
  if (inlineValue !== undefined) {
    flags[name] = inlineValue;
    return index + 1;
  }
  const next = argv[index + 1];
  if (next === undefined || String(next).startsWith('--')) {
    throw new ValidationError(`--${name} needs a value`);
  }
  flags[name] = String(next);
  return index + 2;
}

/**
 * @param {Record<string, { short?: string }> | undefined} known
 * @returns {Map<string, string>}
 */
function shortNamesOf(known) {
  const shortNames = new Map();
  for (const [name, flag] of Object.entries(known ?? {})) {
    if (flag.short !== undefined) {
      shortNames.set(flag.short, name);
    }
  }
  return shortNames;
}

/**
 * @param {string} text
 * @param {string} separator
 * @returns {[string, string | undefined]}
 */
function splitOnce(text, separator) {
  const at = text.indexOf(separator);
  return at === -1 ? [text, undefined] : [text.slice(0, at), text.slice(at + 1)];
}
