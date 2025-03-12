import { MethodLibrary } from './library.js';
import { classify } from './classify.js';
import { titleOf } from './title.js';
import { leadHeadSignature } from './leadHeads.js';
import { squash } from '../util/text.js';
import { sortBy } from '../util/collections.js';
import { ValidationError } from '../errors.js';

/**
 * Searching a method library. A tower librarian asks three kinds of question:
 * what have we got on eight, what Surprise do we ring, and what else finishes
 * where Cambridge finishes. All three are the same query with different fields
 * filled in.
 *
 * The class and the lead head are worked out from the changes each time rather
 * than cached, which is slow enough to matter only on libraries far larger than
 * a tower keeps.
 */

/**
 * @typedef {{
 *   stage?: number,
 *   className?: string,
 *   name?: string,
 *   sharesLeadHeadWith?: import('./method.js').Method,
 *   little?: boolean,
 *   differential?: boolean
 * }} MethodQuery
 */

/**
 * Search a library.
 *
 * @param {MethodLibrary} library
 * @param {MethodQuery} query
 * @returns {import('./method.js').Method[]} matches, in title order
 */
export function search(library, query = {}) {
  if (!(library instanceof MethodLibrary)) {
    throw new ValidationError('a MethodLibrary is needed to search');
  }
  const wantedName = query.name === undefined ? null : squash(query.name).toLowerCase();
  const wantedLeadHead =
    query.sharesLeadHeadWith === undefined ? null : leadHeadSignature(query.sharesLeadHeadWith);
  const matches = [];
  for (const method of library) {
    if (query.stage !== undefined && method.stage !== query.stage) {
      continue;
    }
    const facts = classify(method);
    if (query.className !== undefined && facts.className !== query.className) {
      continue;
    }
    if (query.little !== undefined && facts.little !== query.little) {
      continue;
    }
    if (query.differential !== undefined && facts.differential !== query.differential) {
      continue;
    }
    if (wantedName !== null && !titleOf(method).toLowerCase().includes(wantedName)) {
      continue;
    }
    if (wantedLeadHead !== null && leadHeadSignature(method) !== wantedLeadHead) {
      continue;
    }
    matches.push(method);
  }
  return sortBy(matches, (method) => titleOf(method));
}

/**
 * How many methods the library holds at each stage.
 *
 * @param {MethodLibrary} library
 * @returns {Map<number, number>}
 */
export function countByStage(library) {
  const counts = new Map();
  for (const method of library) {
    counts.set(method.stage, (counts.get(method.stage) ?? 0) + 1);
  }
  return new Map([...counts.entries()].sort((left, right) => left[0] - right[0]));
}

/**
 * How many methods the library holds in each class.
 *
 * @param {MethodLibrary} library
 * @returns {Map<string, number>}
 */
export function countByClass(library) {
  const counts = new Map();
  for (const method of library) {
    const { className } = classify(method);
    counts.set(className, (counts.get(className) ?? 0) + 1);
  }
  return new Map([...counts.entries()].sort((left, right) => left[0].localeCompare(right[0])));
}

/**
 * The methods that can be spliced with each other, grouped by lead head. Groups
 * with only one method in them are left out, since a group of one splices with
 * nothing.
 *
 * @param {MethodLibrary} library
 * @returns {Map<string, import('./method.js').Method[]>}
 */
export function spliceGroups(library) {
  const groups = new Map();
  for (const method of library) {
    const key = leadHeadSignature(method);
    const bucket = groups.get(key);
    if (bucket === undefined) {
      groups.set(key, [method]);
    } else {
      bucket.push(method);
    }
  }
  for (const [key, bucket] of groups) {
    if (bucket.length < 2) {
      groups.delete(key);
    }
  }
  return groups;
}

/**
 * A one line summary of a library for the CLI banner.
 *
 * @param {MethodLibrary} library
 * @returns {string}
 */
export function summarise(library) {
  const stages = [...countByStage(library).entries()]
    .map(([stage, count]) => `${count} on ${stage}`)
    .join(', ');
  return stages === '' ? 'no methods' : `${library.size} methods: ${stages}`;
}
