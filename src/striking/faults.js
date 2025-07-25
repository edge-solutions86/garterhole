import { ValidationError } from '../errors.js';

/**
 * Naming what went wrong.
 *
 * A number tells a band how badly they rang. A name tells them what to do about
 * it, and the names ringers use describe the shape of the error rather than its
 * size: clipping is striking too soon after the bell in front, gapping is
 * leaving too long, and drifting is being consistently on one side of where you
 * should be for a stretch of ringing.
 */

/** Struck too soon after the bell in front. */
export const CLIP = 'clip';
/** Left too long a gap after the bell in front. */
export const GAP = 'gap';
/** Consistently early or late over a run of blows. */
export const DRIFT = 'drift';

/**
 * @typedef {{
 *   kind: string,
 *   bell: number,
 *   rowIndex: number,
 *   place: number,
 *   size: number
 * }} Fault
 */

/**
 * The default thresholds. A blow inside these is not worth naming, because no
 * listener can hear the difference.
 */
export const DEFAULT_THRESHOLDS = Object.freeze({
  clip: 50,
  gap: 50,
  drift: 30,
  driftRun: 4,
});

/**
 * Find the faults in a piece of ringing.
 *
 * @param {readonly import('./metrics.js').BlowError[]} errors
 * @param {{ clip?: number, gap?: number, drift?: number, driftRun?: number }} [thresholds]
 * @returns {Fault[]} in the order they were rung
 */
export function findFaults(errors, thresholds = {}) {
  if (!Array.isArray(errors)) {
    throw new ValidationError('a list of blow errors is needed to find faults in it');
  }
  const limits = { ...DEFAULT_THRESHOLDS, ...thresholds };
  assertThresholds(limits);
  const faults = [];
  for (const entry of errors) {
    if (entry.error <= -limits.clip) {
      faults.push(faultFrom(CLIP, entry));
    } else if (entry.error >= limits.gap) {
      faults.push(faultFrom(GAP, entry));
    }
  }
  faults.push(...driftFaults(errors, limits));
  return faults.sort((left, right) => left.rowIndex - right.rowIndex || left.place - right.place);
}

/**
 * A run of blows by one bell that were all on the same side of where they
 * should have been. This is what a band hears as a bell being "in the wrong
 * place" even though no single blow is badly out.
 *
 * @param {readonly import('./metrics.js').BlowError[]} errors
 * @param {{ drift: number, driftRun: number }} limits
 * @returns {Fault[]}
 */
export function driftFaults(errors, limits) {
  /** @type {Map<number, import('./metrics.js').BlowError[]>} */
  const byBell = new Map();
  for (const entry of errors) {
    const bucket = byBell.get(entry.bell);
    if (bucket === undefined) {
      byBell.set(entry.bell, [entry]);
    } else {
      bucket.push(entry);
    }
  }
  const faults = [];
  for (const [bell, entries] of byBell) {
    let run = [];
    for (const entry of entries) {
      const same = run.length === 0 || Math.sign(entry.error) === Math.sign(run[0].error);
      if (Math.abs(entry.error) >= limits.drift && same && entry.error !== 0) {
        run.push(entry);
        continue;
      }
      faults.push(...emitDrift(bell, run, limits));
      run = Math.abs(entry.error) >= limits.drift && entry.error !== 0 ? [entry] : [];
    }
    faults.push(...emitDrift(bell, run, limits));
  }
  return faults;
}

/**
 * Count the faults of each kind.
 *
 * @param {readonly Fault[]} faults
 * @returns {Record<string, number>}
 */
export function countFaults(faults) {
  const counts = { [CLIP]: 0, [GAP]: 0, [DRIFT]: 0 };
  for (const fault of faults) {
    counts[fault.kind] = (counts[fault.kind] ?? 0) + 1;
  }
  return counts;
}

/**
 * The faults each bell was responsible for.
 *
 * @param {readonly Fault[]} faults
 * @returns {Map<number, Fault[]>}
 */
export function faultsByBell(faults) {
  const byBell = new Map();
  for (const fault of faults) {
    const bucket = byBell.get(fault.bell);
    if (bucket === undefined) {
      byBell.set(fault.bell, [fault]);
    } else {
      bucket.push(fault);
    }
  }
  return new Map([...byBell.entries()].sort((left, right) => left[0] - right[0]));
}

/**
 * A sentence describing a fault, for a report a ringer will actually read.
 *
 * @param {Fault} fault
 * @returns {string}
 */
export function describeFault(fault) {
  const size = Math.round(Math.abs(fault.size));
  if (fault.kind === CLIP) {
    return `bell ${fault.bell} clipped by ${size}ms in row ${fault.rowIndex + 1}`;
  }
  if (fault.kind === GAP) {
    return `bell ${fault.bell} left a ${size}ms gap in row ${fault.rowIndex + 1}`;
  }
  return `bell ${fault.bell} drifted for ${size} blows from row ${fault.rowIndex + 1}`;
}

/**
 * @param {number} bell
 * @param {readonly import('./metrics.js').BlowError[]} run
 * @param {{ driftRun: number }} limits
 * @returns {Fault[]}
 */
function emitDrift(bell, run, limits) {
  if (run.length < limits.driftRun) {
    return [];
  }
  return [
    {
      kind: DRIFT,
      bell,
      rowIndex: run[0].rowIndex,
      place: run[0].place,
      size: run.length,
    },
  ];
}

/**
 * @param {string} kind
 * @param {import('./metrics.js').BlowError} entry
 * @returns {Fault}
 */
function faultFrom(kind, entry) {
  return {
    kind,
    bell: entry.bell,
    rowIndex: entry.rowIndex,
    place: entry.place,
    size: entry.error,
  };
}

/**
 * @param {Record<string, number>} limits
 */
function assertThresholds(limits) {
  for (const [name, value] of Object.entries(limits)) {
    if (typeof value !== 'number' || value <= 0) {
      throw new ValidationError(`the ${name} threshold must be a positive number, not ${value}`);
    }
  }
}
