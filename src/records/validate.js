import { Performance } from './performance.js';
import { checkLength } from '../composition/lengths.js';
import { isWellFormedTitle } from '../method/title.js';
import { hasCover } from '../core/stage.js';
import { ValidationError } from '../errors.js';

/**
 * Checking a performance against the rules before it is published.
 *
 * An association reporter reads several hundred of these a year and cannot
 * check them all by hand. Most of what goes wrong is mechanical: a band of
 * seven for a method on eight, a conductor who is not in the band, a peal of
 * Minor that is not a whole number of extents, a date in the future because
 * somebody typed the year wrong.
 *
 * Each check gives a sentence rather than a code, because the sentence is what
 * gets sent back to the person who filed it.
 */

/**
 * @typedef {{ ok: boolean, problems: string[], warnings: string[] }} Verdict
 */

/**
 * How many ringers a stage needs, allowing for the cover on an odd stage.
 *
 * @param {number} stage
 * @returns {number[]} the band sizes that are acceptable
 */
export function acceptableBandSizes(stage) {
  return hasCover(stage) ? [stage, stage + 1] : [stage];
}

/**
 * Check a performance.
 *
 * @param {Performance} rung
 * @param {{ today?: Date, claim?: 'peal' | 'quarter peal' }} [options]
 * @returns {Verdict}
 */
export function validatePerformance(rung, options = {}) {
  if (!(rung instanceof Performance)) {
    throw new ValidationError('a Performance is needed to check it');
  }
  const problems = [];
  const warnings = [];
  if (!isWellFormedTitle(rung.title)) {
    problems.push(`${JSON.stringify(rung.title)} is not a method title`);
    return { ok: false, problems, warnings };
  }
  const stage = rung.stage;
  const sizes = acceptableBandSizes(stage);
  if (!sizes.includes(rung.bandSize)) {
    problems.push(
      `${rung.title} needs a band of ${sizes.join(' or ')} and this one has ${rung.bandSize}`,
    );
  }
  if (new Set(rung.band).size !== rung.band.length) {
    problems.push('somebody is down for more than one bell');
  }
  if (rung.conductor === null) {
    warnings.push('no conductor is recorded');
  }
  const today = options.today ?? new Date();
  if (rung.date.getTime() > today.getTime()) {
    problems.push(`the date ${rung.dateText} is in the future`);
  }
  if (options.claim !== undefined) {
    const checked = checkLength(rung.changes, stage, options.claim);
    if (!checked.ok) {
      problems.push(checked.reason);
    }
  }
  warnings.push(...speedWarnings(rung));
  return { ok: problems.length === 0, problems, warnings };
}

/**
 * Warnings about the time a performance took. A quarter peal of Minor in
 * fifteen minutes did not happen, and a peal that took nine hours is either a
 * record or a typing mistake.
 *
 * @param {Performance} rung
 * @returns {string[]}
 */
export function speedWarnings(rung) {
  if (rung.duration === null) {
    return [];
  }
  const secondsPerChange = (rung.duration * 60) / rung.changes;
  const warnings = [];
  if (secondsPerChange < 1) {
    warnings.push(
      `${rung.duration} minutes for ${rung.changes} changes is faster than anybody rings`,
    );
  }
  if (secondsPerChange > 5) {
    warnings.push(
      `${rung.duration} minutes for ${rung.changes} changes is slower than anybody rings`,
    );
  }
  return warnings;
}

/**
 * Check a list of performances and gather everything wrong with them.
 *
 * @param {Iterable<Performance>} performances
 * @param {{ today?: Date }} [options]
 * @returns {{ id: string, verdict: Verdict }[]} only the ones with something wrong
 */
export function validateAll(performances, options = {}) {
  const found = [];
  for (const rung of performances) {
    const verdict = validatePerformance(rung, options);
    if (!verdict.ok || verdict.warnings.length > 0) {
      found.push({ id: rung.id, verdict });
    }
  }
  return found;
}

/**
 * A single sentence saying whether a performance passed.
 *
 * @param {Verdict} verdict
 * @returns {string}
 */
export function describeVerdict(verdict) {
  if (verdict.ok && verdict.warnings.length === 0) {
    return 'nothing wrong with it';
  }
  if (verdict.ok) {
    return `nothing wrong with it, but ${verdict.warnings.join('; ')}`;
  }
  return verdict.problems.join('; ');
}
