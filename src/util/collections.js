/**
 * Small collection helpers. Nothing here is clever; it exists so that the
 * domain code reads as domain code rather than as index arithmetic, and so
 * that the same fold is not written out eleven times.
 */

/**
 * Group items by a key.
 *
 * @template T
 * @template K
 * @param {Iterable<T>} items
 * @param {(item: T) => K} keyOf
 * @returns {Map<K, T[]>}
 */
export function groupBy(items, keyOf) {
  /** @type {Map<K, T[]>} */
  const groups = new Map();
  for (const item of items) {
    const key = keyOf(item);
    const bucket = groups.get(key);
    if (bucket === undefined) {
      groups.set(key, [item]);
    } else {
      bucket.push(item);
    }
  }
  return groups;
}

/**
 * Count items by a key.
 *
 * @template T
 * @template K
 * @param {Iterable<T>} items
 * @param {(item: T) => K} keyOf
 * @returns {Map<K, number>}
 */
export function countBy(items, keyOf) {
  /** @type {Map<K, number>} */
  const counts = new Map();
  for (const item of items) {
    const key = keyOf(item);
    counts.set(key, (counts.get(key) ?? 0) + 1);
  }
  return counts;
}

/**
 * Break a list into runs of a fixed size. The last run is short when the list
 * does not divide evenly.
 *
 * @template T
 * @param {readonly T[]} items
 * @param {number} size
 * @returns {T[][]}
 */
export function chunk(items, size) {
  if (!Number.isInteger(size) || size < 1) {
    throw new RangeError(`chunk size ${size} must be a positive whole number`);
  }
  const chunks = [];
  for (let index = 0; index < items.length; index += size) {
    chunks.push(items.slice(index, index + size));
  }
  return chunks;
}

/**
 * Remove repeats, keeping the first of each. Identity is decided by a key
 * function so that value objects can be de-duplicated by their printed form.
 *
 * @template T
 * @param {Iterable<T>} items
 * @param {(item: T) => unknown} [keyOf]
 * @returns {T[]}
 */
export function unique(items, keyOf = (item) => item) {
  const seen = new Set();
  const kept = [];
  for (const item of items) {
    const key = keyOf(item);
    if (seen.has(key)) {
      continue;
    }
    seen.add(key);
    kept.push(item);
  }
  return kept;
}

/**
 * Sort without disturbing the input, by one or more comparison keys. Numbers
 * sort numerically and strings sort by code unit, which is enough for method
 * titles and ringer names.
 *
 * @template T
 * @param {readonly T[]} items
 * @param {...((item: T) => number | string)} keys
 * @returns {T[]}
 */
export function sortBy(items, ...keys) {
  return [...items].sort((left, right) => {
    for (const keyOf of keys) {
      const a = keyOf(left);
      const b = keyOf(right);
      if (a < b) {
        return -1;
      }
      if (a > b) {
        return 1;
      }
    }
    return 0;
  });
}

/**
 * Split a list in two by a test.
 *
 * @template T
 * @param {Iterable<T>} items
 * @param {(item: T) => boolean} test
 * @returns {[T[], T[]]} the items that passed and the items that did not
 */
export function partition(items, test) {
  const passed = [];
  const failed = [];
  for (const item of items) {
    (test(item) ? passed : failed).push(item);
  }
  return [passed, failed];
}

/**
 * Pair up two lists. The result is as long as the shorter one, which is what
 * the striking analysis wants when a stream of strikes runs out early.
 *
 * @template A
 * @template B
 * @param {readonly A[]} left
 * @param {readonly B[]} right
 * @returns {[A, B][]}
 */
export function zip(left, right) {
  const pairs = [];
  const length = Math.min(left.length, right.length);
  for (let index = 0; index < length; index += 1) {
    pairs.push([left[index], right[index]]);
  }
  return pairs;
}

/**
 * The sum of a list of numbers, or zero for an empty one.
 *
 * @param {Iterable<number>} values
 * @returns {number}
 */
export function sum(values) {
  let total = 0;
  for (const value of values) {
    total += value;
  }
  return total;
}

/**
 * The mean, or null for an empty list. Null rather than NaN, because a missing
 * answer travels through JSON and a NaN does not.
 *
 * @param {readonly number[]} values
 * @returns {number | null}
 */
export function mean(values) {
  if (values.length === 0) {
    return null;
  }
  return sum(values) / values.length;
}

/**
 * The item with the largest score, or null for an empty list. Ties go to the
 * first item, which keeps the answer stable across runs.
 *
 * @template T
 * @param {readonly T[]} items
 * @param {(item: T) => number} scoreOf
 * @returns {T | null}
 */
export function maxBy(items, scoreOf) {
  let best = null;
  let bestScore = Number.NEGATIVE_INFINITY;
  for (const item of items) {
    const score = scoreOf(item);
    if (score > bestScore) {
      best = item;
      bestScore = score;
    }
  }
  return best;
}
