import { StorageError, ValidationError } from '../errors.js';

/**
 * How a record is written to the journal.
 *
 * One JSON object per line, with a checksum in front of it. The checksum is
 * what makes a half written line detectable: a process killed while appending
 * leaves a line that parses as JSON often enough to be dangerous, and a
 * mismatched checksum turns that into an error instead of a silently truncated
 * record.
 *
 * The checksum is a Fletcher style sum rather than a cryptographic hash. It is
 * not defending against an attacker, only against a power cut, and it has to be
 * cheap enough to run on every line of a journal at start up.
 */

/** The separator between the checksum and the payload. */
const SEPARATOR = ' ';

/**
 * The checksum of a string, as eight hexadecimal characters.
 *
 * @param {string} text
 * @returns {string}
 */
export function checksum(text) {
  let low = 0xffff;
  let high = 0xffff;
  for (let index = 0; index < text.length; index += 1) {
    low += text.charCodeAt(index);
    high += low;
    if (index % 16 === 15) {
      low = (low & 0xffff) + (low >>> 16);
      high = (high & 0xffff) + (high >>> 16);
    }
  }
  low = (low & 0xffff) + (low >>> 16);
  high = (high & 0xffff) + (high >>> 16);
  const value = ((high & 0xffff) << 16) | (low & 0xffff);
  return (value >>> 0).toString(16).padStart(8, '0');
}

/**
 * Write a record as a line, with no newline on the end.
 *
 * @param {unknown} record
 * @returns {string}
 */
export function encode(record) {
  if (record === undefined) {
    throw new ValidationError('there is nothing to write');
  }
  let payload;
  try {
    payload = JSON.stringify(record);
  } catch (error) {
    throw new ValidationError(`this record cannot be written as JSON: ${error.message}`);
  }
  if (payload === undefined) {
    throw new ValidationError('this record cannot be written as JSON');
  }
  if (payload.includes('\n')) {
    throw new ValidationError('a record cannot contain a newline once it is JSON');
  }
  return `${checksum(payload)}${SEPARATOR}${payload}`;
}

/**
 * Read a line back.
 *
 * @param {string} line
 * @param {number} [lineNumber] for the error message
 * @returns {unknown}
 */
export function decode(line, lineNumber = 0) {
  if (typeof line !== 'string') {
    throw new ValidationError('a journal line must be a string');
  }
  const at = line.indexOf(SEPARATOR);
  if (at !== 8) {
    throw new StorageError(`line ${lineNumber} does not start with a checksum`, {
      details: { lineNumber },
    });
  }
  const found = line.slice(0, at);
  const payload = line.slice(at + 1);
  const wanted = checksum(payload);
  if (found !== wanted) {
    throw new StorageError(`line ${lineNumber} is damaged: checksum ${found} should be ${wanted}`, {
      details: { lineNumber, found, wanted },
    });
  }
  try {
    return JSON.parse(payload);
  } catch (error) {
    throw new StorageError(`line ${lineNumber} is not JSON: ${error.message}`, {
      details: { lineNumber },
      cause: error,
    });
  }
}

/**
 * Read a whole file of lines, skipping blank ones. A trailing partial line is
 * reported rather than skipped, because losing the most recent record silently
 * is the one failure this format exists to prevent.
 *
 * @param {string} text
 * @returns {unknown[]}
 */
export function decodeAll(text) {
  if (typeof text !== 'string') {
    throw new ValidationError('a journal must be read from a string');
  }
  const records = [];
  const lines = text.split('\n');
  lines.forEach((line, index) => {
    if (line.length === 0) {
      return;
    }
    records.push(decode(line, index + 1));
  });
  return records;
}

/**
 * Write a run of records as the body of a journal file, with a newline after
 * every line including the last.
 *
 * @param {Iterable<unknown>} records
 * @returns {string}
 */
export function encodeAll(records) {
  let text = '';
  for (const record of records) {
    text += `${encode(record)}\n`;
  }
  return text;
}
