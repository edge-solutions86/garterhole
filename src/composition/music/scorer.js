import { countPattern } from './patterns.js';
import { countRuns, runTable } from './runs.js';
import { nameOfRow } from '../../core/namedRows.js';
import { ValidationError } from '../../errors.js';

/**
 * Scoring the music in a composition.
 *
 * There is no single right scoring. Every band weights the music it likes, and
 * two compositions that score the same under one scheme will not under another.
 * So the scheme is data rather than code: a list of named rules, each with a
 * pattern or a run length and a weight, and the scorer applies whatever it is
 * handed.
 *
 * The default scheme is the one most peal boards would recognise, with four
 * bell runs weighted one and the whole row of tenors weighted more.
 */

/**
 * @typedef {{
 *   name: string,
 *   weight: number,
 *   pattern?: string,
 *   runLength?: number,
 *   namedRow?: string
 * }} MusicRule
 */

/**
 * The scheme used when none is given.
 *
 * @param {number} stage
 * @returns {MusicRule[]}
 */
export function defaultScheme(stage) {
  if (!Number.isInteger(stage) || stage < 4) {
    throw new ValidationError(`there is no default music scheme on ${stage}`);
  }
  const rules = [
    { name: 'four bell runs', runLength: 4, weight: 1 },
    { name: 'five bell runs', runLength: 5, weight: 2 },
    { name: 'six bell runs', runLength: 6, weight: 3 },
    { name: 'queens', namedRow: 'Queens', weight: 5 },
    { name: 'backrounds', namedRow: 'Backrounds', weight: 5 },
    { name: 'tittums', namedRow: 'Tittums', weight: 5 },
  ];
  return rules.filter((rule) => rule.runLength === undefined || rule.runLength <= stage);
}

/**
 * Score a block of rows against a scheme.
 *
 * @param {Iterable<import('../../core/row.js').Row>} rows
 * @param {{ stage: number, scheme?: MusicRule[] }} options
 * @returns {{ total: number, lines: { name: string, count: number, weight: number, score: number }[] }}
 */
export function scoreMusic(rows, options) {
  const stage = options.stage;
  const scheme = options.scheme ?? defaultScheme(stage);
  const kept = [...rows];
  const lines = [];
  let total = 0;
  for (const rule of scheme) {
    const count = countRule(kept, rule);
    const score = count * rule.weight;
    total += score;
    lines.push({ name: rule.name, count, weight: rule.weight, score });
  }
  return { total, lines };
}

/**
 * How many times a single rule fires over a block.
 *
 * @param {readonly import('../../core/row.js').Row[]} rows
 * @param {MusicRule} rule
 * @returns {number}
 */
export function countRule(rows, rule) {
  if (rule === null || typeof rule !== 'object') {
    throw new ValidationError('a music rule must be an object');
  }
  if (typeof rule.weight !== 'number' || !Number.isFinite(rule.weight)) {
    throw new ValidationError(`the rule ${JSON.stringify(rule.name)} has no weight`);
  }
  if (rule.pattern !== undefined) {
    return countPattern(rows, rule.pattern);
  }
  if (rule.runLength !== undefined) {
    return countRuns(rows, rule.runLength).total;
  }
  if (rule.namedRow !== undefined) {
    return rows.filter((row) => nameOfRow(row) === rule.namedRow).length;
  }
  throw new ValidationError(`the rule ${JSON.stringify(rule.name)} has nothing to count`, {
    details: { rule: rule.name },
  });
}

/**
 * A summary of the music in a block, as a report writer wants it.
 *
 * @param {Iterable<import('../../core/row.js').Row>} rows
 * @param {number} stage
 * @returns {{
 *   score: number,
 *   runs: { length: number, front: number, back: number, total: number }[],
 *   named: { name: string, count: number }[]
 * }}
 */
export function musicSummary(rows, stage) {
  const kept = [...rows];
  const { total } = scoreMusic(kept, { stage });
  const runs = [...runTable(kept, stage).entries()].map(([length, counts]) => ({
    length,
    ...counts,
  }));
  /** @type {Map<string, number>} */
  const named = new Map();
  for (const row of kept) {
    const name = nameOfRow(row);
    if (name !== null && name !== 'Rounds') {
      named.set(name, (named.get(name) ?? 0) + 1);
    }
  }
  return {
    score: total,
    runs,
    named: [...named.entries()].map(([name, count]) => ({ name, count })),
  };
}

/**
 * Compare two compositions on the same scheme.
 *
 * @param {Iterable<import('../../core/row.js').Row>} left
 * @param {Iterable<import('../../core/row.js').Row>} right
 * @param {{ stage: number, scheme?: MusicRule[] }} options
 * @returns {number} positive when the left one is more musical
 */
export function compareMusic(left, right, options) {
  return scoreMusic(left, options).total - scoreMusic(right, options).total;
}
