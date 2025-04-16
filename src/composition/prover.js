import { RowBlock } from '../core/rowBlock.js';
import { ValidationError } from '../errors.js';

/**
 * Proving a touch. A touch is true when no row is rung twice, and the only way
 * to be sure of that is to look at every row.
 *
 * The row a touch finishes on is the row it started on, and that repeat does
 * not count. Everything else does, including a repeat that a composer meant to
 * be there, because a touch with a deliberate repeat is still false.
 */

/**
 * @typedef {{ row: string, at: number[] }} Repeat
 */

/**
 * @typedef {{
 *   isTrue: boolean,
 *   rows: number,
 *   distinct: number,
 *   repeats: Repeat[],
 *   comesRound: boolean
 * }} Proof
 */

/**
 * Prove a block of rows.
 *
 * @param {RowBlock} block
 * @returns {Proof}
 */
export function prove(block) {
  if (!(block instanceof RowBlock)) {
    throw new ValidationError('a RowBlock is needed to prove it');
  }
  const comesRound = block.comesRound();
  const body = comesRound && block.length > 1 ? block.rows.slice(0, -1) : block.rows;
  /** @type {Map<string, number[]>} */
  const seen = new Map();
  body.forEach((row, index) => {
    const key = row.key();
    const at = seen.get(key);
    if (at === undefined) {
      seen.set(key, [index]);
    } else {
      at.push(index);
    }
  });
  const repeats = [];
  for (const [row, at] of seen) {
    if (at.length > 1) {
      repeats.push({ row, at });
    }
  }
  repeats.sort((left, right) => left.at[0] - right.at[0]);
  return {
    isTrue: repeats.length === 0,
    rows: body.length,
    distinct: seen.size,
    repeats,
    comesRound,
  };
}

/**
 * Prove a rung touch.
 *
 * @param {{ block: RowBlock }} rung
 * @returns {Proof}
 */
export function proveTouch(rung) {
  if (rung === null || typeof rung !== 'object' || !(rung.block instanceof RowBlock)) {
    throw new ValidationError('a rung touch is needed to prove it');
  }
  return prove(rung.block);
}

/**
 * How many rows there are at a stage, which is what an extent has to contain.
 *
 * @param {number} stage
 * @returns {number}
 */
export function extentSize(stage) {
  let total = 1;
  for (let bell = 2; bell <= stage; bell += 1) {
    total *= bell;
  }
  return total;
}

/**
 * True when the block contains every row at its stage exactly once. On an odd
 * stage the cover bell never moves, so the extent is over the changing bells.
 *
 * @param {RowBlock} block
 * @returns {boolean}
 */
export function isExtent(block) {
  const proof = prove(block);
  if (!proof.isTrue) {
    return false;
  }
  const changing = block.stage % 2 === 1 ? block.stage - 1 : block.stage;
  return proof.distinct === extentSize(changing);
}

/**
 * The first repeat in a false touch, which is what a composer wants to see
 * before the whole list.
 *
 * @param {Proof} proof
 * @returns {Repeat | null}
 */
export function firstRepeat(proof) {
  return proof.repeats.length === 0 ? null : proof.repeats[0];
}

/**
 * A one line summary of a proof.
 *
 * @param {Proof} proof
 * @returns {string}
 */
export function describeProof(proof) {
  if (proof.isTrue) {
    const ending = proof.comesRound ? '' : ', but it does not come round';
    return `true, ${proof.rows} rows${ending}`;
  }
  const first = firstRepeat(proof);
  return `false: ${first.row} is rung ${first.at.length} times, first at row ${first.at[0] + 1}`;
}
