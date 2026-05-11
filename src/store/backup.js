import { copyFileSync, existsSync, mkdirSync, readdirSync, statSync, unlinkSync } from 'node:fs';
import { join, basename } from 'node:path';
import { Journal } from './journal.js';
import { decodeAll } from './codec.js';
import { readFileSync } from 'node:fs';
import { StorageError, ValidationError } from '../errors.js';

/**
 * Copying the journal somewhere safe.
 *
 * A tower's records are irreplaceable and live on one machine in a vestry. A
 * backup is a copy of the journal with the time in its name, and restoring one
 * is copying it back, which is as complicated as this needs to be.
 *
 * Every backup is read before it is kept. A backup that turns out to be
 * unreadable at the moment it is needed is worse than no backup, because
 * somebody stopped worrying about it years ago.
 */

/**
 * Take a backup.
 *
 * @param {{
 *   journal: Journal,
 *   directory: string,
 *   now?: () => Date,
 *   keep?: number
 * }} options
 * @returns {{ path: string, records: number, removed: string[] }}
 */
export function backup(options) {
  if (options === null || typeof options !== 'object') {
    throw new ValidationError('a backup needs a journal and somewhere to put it');
  }
  if (!(options.journal instanceof Journal)) {
    throw new ValidationError('a backup needs a Journal');
  }
  if (typeof options.directory !== 'string' || options.directory.length === 0) {
    throw new ValidationError('a backup needs a directory');
  }
  if (!options.journal.exists()) {
    throw new StorageError('there is no journal to back up');
  }
  const now = options.now ?? (() => new Date());
  const stamp = now().toISOString().replaceAll(/[:.]/g, '-');
  if (!existsSync(options.directory)) {
    mkdirSync(options.directory, { recursive: true });
  }
  const path = join(options.directory, `journal-${stamp}.jsonl`);
  copyFileSync(options.journal.path, path);
  const records = verify(path);
  const removed = options.keep === undefined ? [] : prune(options.directory, options.keep);
  return { path, records, removed };
}

/**
 * Read a backup and count what is in it, so that an unreadable copy is found
 * now rather than when it is needed.
 *
 * @param {string} path
 * @returns {number}
 */
export function verify(path) {
  if (!existsSync(path)) {
    throw new StorageError(`there is no backup at ${path}`);
  }
  try {
    return decodeAll(readFileSync(path, 'utf8')).length;
  } catch (error) {
    unlinkSync(path);
    throw new StorageError(`the backup at ${path} could not be read back: ${error.message}`, {
      cause: error,
    });
  }
}

/**
 * The backups in a directory, newest first.
 *
 * @param {string} directory
 * @returns {{ name: string, path: string, bytes: number }[]}
 */
export function listBackups(directory) {
  if (!existsSync(directory)) {
    return [];
  }
  return readdirSync(directory)
    .filter((name) => name.startsWith('journal-') && name.endsWith('.jsonl'))
    .sort()
    .reverse()
    .map((name) => {
      const path = join(directory, name);
      return { name, path, bytes: statSync(path).size };
    });
}

/**
 * Keep only the newest few backups.
 *
 * @param {string} directory
 * @param {number} keep
 * @returns {string[]} the names removed
 */
export function prune(directory, keep) {
  if (!Number.isInteger(keep) || keep < 1) {
    throw new ValidationError(`keeping ${keep} backups is not a policy`);
  }
  const removed = [];
  for (const entry of listBackups(directory).slice(keep)) {
    unlinkSync(entry.path);
    removed.push(entry.name);
  }
  return removed;
}

/**
 * Put a backup back, over the journal.
 *
 * @param {{ journal: Journal, path: string }} options
 * @returns {number} how many records were restored
 */
export function restore(options) {
  if (!(options?.journal instanceof Journal)) {
    throw new ValidationError('a restore needs a Journal to write to');
  }
  const records = verify(options.path);
  options.journal.ensureDirectory();
  copyFileSync(options.path, options.journal.path);
  return records;
}

/**
 * The newest backup in a directory, or null.
 *
 * @param {string} directory
 * @returns {string | null}
 */
export function newestBackup(directory) {
  const backups = listBackups(directory);
  return backups.length === 0 ? null : backups[0].path;
}

/**
 * The name a backup file would have, which the tests and the log line share.
 *
 * @param {string} path
 * @returns {string}
 */
export function backupName(path) {
  return basename(path);
}
