import { Performance } from './performance.js';
import { parseTitle } from '../method/title.js';
import { countBy, sortBy, sum } from '../util/collections.js';
import { ValidationError } from '../errors.js';

/**
 * Counting up what a band has rung.
 *
 * Every association report opens with the same handful of numbers: how many
 * peals and quarters, at how many towers, in how many methods, and who rang the
 * most of them. None of it is hard, and all of it is fiddly enough to be worth
 * writing once.
 *
 * A performance counts once for each person in the band, which is what "peals
 * rung" means to a ringer. It counts once for the tower and once for the
 * method, which is what it means to a reporter.
 */

/**
 * @typedef {{
 *   performances: number,
 *   peals: number,
 *   quarters: number,
 *   touches: number,
 *   changes: number
 * }} Totals
 */

/**
 * Add a set of performances up.
 *
 * @param {Iterable<Performance>} records
 * @returns {Totals}
 */
export function totals(records) {
  const kept = collect(records);
  return {
    performances: kept.length,
    peals: kept.filter((rung) => rung.kind === 'peal').length,
    quarters: kept.filter((rung) => rung.kind === 'quarter peal').length,
    touches: kept.filter((rung) => rung.kind === 'touch').length,
    changes: sum(kept.map((rung) => rung.changes)),
  };
}

/**
 * What each ringer has rung, most first.
 *
 * @param {Iterable<Performance>} records
 * @returns {{ name: string, totals: Totals, conducted: number }[]}
 */
export function byRinger(records) {
  const kept = collect(records);
  /** @type {Map<string, Performance[]>} */
  const byName = new Map();
  for (const rung of kept) {
    for (const name of rung.band) {
      const bucket = byName.get(name);
      if (bucket === undefined) {
        byName.set(name, [rung]);
      } else {
        bucket.push(rung);
      }
    }
  }
  const lines = [];
  for (const [name, theirs] of byName) {
    lines.push({
      name,
      totals: totals(theirs),
      conducted: theirs.filter((rung) => rung.conductor === name).length,
    });
  }
  return sortBy(
    lines,
    (line) => -line.totals.performances,
    (line) => line.name,
  );
}

/**
 * What each tower has had rung in it, most first.
 *
 * @param {Iterable<Performance>} records
 * @returns {{ tower: string, totals: Totals }[]}
 */
export function byTower(records) {
  const kept = collect(records);
  const byName = new Map();
  for (const rung of kept) {
    const bucket = byName.get(rung.tower);
    if (bucket === undefined) {
      byName.set(rung.tower, [rung]);
    } else {
      bucket.push(rung);
    }
  }
  const lines = [...byName.entries()].map(([tower, theirs]) => ({ tower, totals: totals(theirs) }));
  return sortBy(
    lines,
    (line) => -line.totals.performances,
    (line) => line.tower,
  );
}

/**
 * How often each method has been rung.
 *
 * @param {Iterable<Performance>} records
 * @returns {{ title: string, count: number }[]}
 */
export function byMethod(records) {
  const counts = countBy(collect(records), (rung) => rung.title);
  return sortBy(
    [...counts.entries()].map(([title, count]) => ({ title, count })),
    (line) => -line.count,
    (line) => line.title,
  );
}

/**
 * How often each stage has been rung, largest stage first.
 *
 * @param {Iterable<Performance>} records
 * @returns {{ stage: number, count: number }[]}
 */
export function byStage(records) {
  const counts = new Map();
  for (const rung of collect(records)) {
    let stage;
    try {
      stage = parseTitle(rung.title).stage;
    } catch {
      continue;
    }
    counts.set(stage, (counts.get(stage) ?? 0) + 1);
  }
  return [...counts.entries()]
    .map(([stage, count]) => ({ stage, count }))
    .sort((left, right) => right.stage - left.stage);
}

/**
 * The performances in a stretch of time.
 *
 * @param {Iterable<Performance>} records
 * @param {Date} from
 * @param {Date} to
 * @returns {Performance[]}
 */
export function between(records, from, to) {
  if (!(from instanceof Date) || !(to instanceof Date)) {
    throw new ValidationError('two dates are needed to look between them');
  }
  return collect(records).filter(
    (rung) => rung.date.getTime() >= from.getTime() && rung.date.getTime() < to.getTime(),
  );
}

/**
 * @param {Iterable<Performance>} records
 * @returns {Performance[]}
 */
function collect(records) {
  const kept = [];
  for (const rung of records) {
    if (!(rung instanceof Performance)) {
      throw new ValidationError('only performances can be counted up');
    }
    kept.push(rung);
  }
  return kept;
}
