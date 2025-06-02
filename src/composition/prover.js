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
 * A running check on truth. Rows are handed in one at a time and the tracker
 * says at once whether the row has been rung before, so a search that is
 * building a composition can stop at the first repeat rather than ringing the
 * whole thing and proving it afterwards.
 */
export class TruthTracker {
  constructor() {
    /** @type {Map<string, number[]>} */
    this.seen = new Map();
    this.count = 0;
  }

  /**
   * Offer a row.
   *
   * @param {import('../core/row.js').Row} row
   * @returns {boolean} false when the row has been rung before
   */
  add(row) {
    const key = row.key();
    const at = this.seen.get(key);
    if (at === undefined) {
      this.seen.set(key, [this.count]);
      this.count += 1;
      return true;
    }
    at.push(this.count);
    this.count += 1;
    return false;
  }

  /**
   * @returns {boolean}
   */
  isTrue() {
    for (const at of this.seen.values()) {
      if (at.length > 1) {
        return false;
      }
    }
    return true;
  }

  /**
   * @returns {Repeat[]} the repeats found so far, in the order they started
   */
  repeats() {
    const repeats = [];
    for (const [row, at] of this.seen) {
      if (at.length > 1) {
        repeats.push({ row, at: [...at] });
      }
    }
    return repeats.sort((left, right) => left.at[0] - right.at[0]);
  }

  /**
   * @returns {number} how many different rows have been offered
   */
  get distinct() {
    return this.seen.size;
  }
}

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
  const tracker = new TruthTracker();
  const upTo = comesRound && block.length > 1 ? block.length - 1 : block.length;
  for (let index = 0; index < upTo; index += 1) {
    tracker.add(block.at(index));
  }
  return {
    isTrue: tracker.isTrue(),
    rows: tracker.count,
    distinct: tracker.distinct,
    repeats: tracker.repeats(),
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
