import { Repository, PUT } from './repository.js';
import { ValidationError } from '../errors.js';

/**
 * Squeezing the journal.
 *
 * A journal that has been running for a few years holds a great many records
 * that no longer matter: every earlier version of a document that has since
 * been replaced, and every document that has since been deleted. Compaction
 * writes a new journal holding one put per document that is still there.
 *
 * The result folds to exactly the same state, which is the property worth
 * testing rather than the size it saves. Anything that changed the state would
 * be data loss dressed up as maintenance.
 */

/**
 * How much of the journal is no longer doing any work.
 *
 * @param {Repository} repository
 * @returns {{ records: number, live: number, wasted: number, ratio: number }}
 */
export function compactionGain(repository) {
  assertRepository(repository);
  const records = repository.journal.count();
  const live = liveRecords(repository).length;
  const wasted = records - live;
  return {
    records,
    live,
    wasted,
    ratio: records === 0 ? 0 : wasted / records,
  };
}

/**
 * True when the journal is wasteful enough to be worth rewriting. Compaction is
 * cheap but not free, and doing it on every start up would rewrite a file that
 * is almost all live records.
 *
 * @param {Repository} repository
 * @param {{ minimumRecords?: number, minimumRatio?: number }} [thresholds]
 * @returns {boolean}
 */
export function worthCompacting(repository, thresholds = {}) {
  const minimumRecords = thresholds.minimumRecords ?? 100;
  const minimumRatio = thresholds.minimumRatio ?? 0.5;
  const gain = compactionGain(repository);
  return gain.records >= minimumRecords && gain.ratio >= minimumRatio;
}

/**
 * The records a compacted journal would hold: one put per living document, in
 * collection and identifier order so that the file is stable.
 *
 * @param {Repository} repository
 * @returns {unknown[]}
 */
export function liveRecords(repository) {
  assertRepository(repository);
  const records = [];
  for (const collection of repository.collectionNames()) {
    for (const { id, document } of repository.all(collection)) {
      records.push({ op: PUT, collection, id, document });
    }
  }
  return records;
}

/**
 * Rewrite the journal so that it holds only what is still there.
 *
 * @param {Repository} repository
 * @returns {{ before: number, after: number }}
 */
export function compact(repository) {
  assertRepository(repository);
  const before = repository.journal.count();
  const records = liveRecords(repository);
  repository.journal.rewrite(records);
  repository.applied = records.length;
  if (repository.snapshot !== null) {
    repository.snapshot.remove();
  }
  return { before, after: records.length };
}

/**
 * Compact only when it is worth it, and say whether anything was done.
 *
 * @param {Repository} repository
 * @param {{ minimumRecords?: number, minimumRatio?: number }} [thresholds]
 * @returns {{ compacted: boolean, before: number, after: number }}
 */
export function compactIfWorthwhile(repository, thresholds = {}) {
  if (!worthCompacting(repository, thresholds)) {
    const records = repository.journal.count();
    return { compacted: false, before: records, after: records };
  }
  return { compacted: true, ...compact(repository) };
}

/**
 * @param {unknown} repository
 */
function assertRepository(repository) {
  if (!(repository instanceof Repository)) {
    throw new ValidationError('a Repository is needed to compact it');
  }
}
