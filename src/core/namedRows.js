import { Row, rounds } from './row.js';
import { isSupportedStage, changingBells } from './stage.js';

/**
 * A handful of rows have names, and a band that rings one on the way past will
 * mention it in the pub afterwards. The music scorer counts them, so they have
 * to be built rather than typed in for every stage.
 *
 * Every named row here is defined over the bells that actually change. On an
 * odd stage the tenor covers, so Queens on Doubles is 13524 with the tenor
 * behind rather than an attempt to interleave six bells.
 */

/**
 * Odd bells ascending, then even bells ascending.
 *
 * @param {number} stage
 * @returns {Row}
 */
export function queens(stage) {
  return buildNamedRow(stage, (working) => {
    const odds = [];
    const evens = [];
    for (let bell = 1; bell <= working; bell += 1) {
      (bell % 2 === 1 ? odds : evens).push(bell);
    }
    return [...odds, ...evens];
  });
}

/**
 * Odd bells descending, then even bells ascending.
 *
 * @param {number} stage
 * @returns {Row}
 */
export function kings(stage) {
  return buildNamedRow(stage, (working) => {
    const odds = [];
    const evens = [];
    for (let bell = 1; bell <= working; bell += 1) {
      (bell % 2 === 1 ? odds : evens).push(bell);
    }
    return [...odds.reverse(), ...evens];
  });
}

/**
 * The two halves of the row interleaved, which gives the rocking sound the
 * name comes from.
 *
 * @param {number} stage
 * @returns {Row}
 */
export function tittums(stage) {
  return buildNamedRow(stage, (working) => {
    const half = working / 2;
    const bells = [];
    for (let index = 0; index < half; index += 1) {
      bells.push(index + 1, half + index + 1);
    }
    return bells;
  });
}

/**
 * Every bell in reverse.
 *
 * @param {number} stage
 * @returns {Row}
 */
export function backrounds(stage) {
  return buildNamedRow(stage, (working) => {
    const bells = [];
    for (let bell = working; bell >= 1; bell -= 1) {
      bells.push(bell);
    }
    return bells;
  });
}

/**
 * Whittingtons, which only exists on eight. There is no rule behind it that
 * generalises, so asking for it at another stage is an error rather than a
 * silent invention.
 *
 * @param {number} stage
 * @returns {Row}
 */
export function whittingtons(stage) {
  if (stage !== 8) {
    throw new RangeError('Whittingtons is only rung on eight bells');
  }
  return new Row([1, 2, 7, 5, 3, 4, 6, 8]);
}

/**
 * Every named row at a stage, keyed by name.
 *
 * @param {number} stage
 * @returns {Map<string, Row>}
 */
export function namedRows(stage) {
  const named = new Map();
  named.set('Rounds', rounds(stage));
  named.set('Backrounds', backrounds(stage));
  named.set('Queens', queens(stage));
  named.set('Kings', kings(stage));
  named.set('Tittums', tittums(stage));
  if (stage === 8) {
    named.set('Whittingtons', whittingtons(stage));
  }
  return named;
}

/**
 * The name of a row, or null when it has none.
 *
 * @param {Row} row
 * @returns {string | null}
 */
export function nameOfRow(row) {
  if (!(row instanceof Row)) {
    throw new TypeError('a row is needed to look up its name');
  }
  for (const [name, candidate] of namedRows(row.stage)) {
    if (candidate.equals(row)) {
      return name;
    }
  }
  return null;
}

/**
 * Build a named row over the changing bells and put the cover bells back on
 * the end.
 *
 * @param {number} stage
 * @param {(working: number) => number[]} build
 * @returns {Row}
 */
function buildNamedRow(stage, build) {
  if (!isSupportedStage(stage)) {
    throw new RangeError(`stage ${stage} is not supported`);
  }
  const working = changingBells(stage);
  const bells = build(working);
  for (let bell = working + 1; bell <= stage; bell += 1) {
    bells.push(bell);
  }
  return new Row(bells);
}
