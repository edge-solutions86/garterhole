import { cyclicGroup, order, transpose } from '../core/transposition.js';
import { Row, rounds } from '../core/row.js';
import { fixedBells, cycleLengths } from '../core/cycles.js';
import { ValidationError } from '../errors.js';

/**
 * Part structure. Most long compositions are one short calling rung several
 * times over, each time starting from a different row. That row is the part
 * head, and the whole composition is the part repeated until the part head
 * comes back to rounds.
 *
 * Knowing the structure is what makes a peal callable. A conductor who knows a
 * composition is five parts has five things to remember rather than a hundred
 * and twenty.
 */

/**
 * Every part head of a composition with the given part end, starting at rounds.
 *
 * @param {import('../core/row.js').Row} partEnd
 * @returns {import('../core/row.js').Row[]}
 */
export function partHeads(partEnd) {
  return cyclicGroup(partEnd);
}

/**
 * How many parts a composition with this part end has.
 *
 * @param {import('../core/row.js').Row} partEnd
 * @returns {number}
 */
export function partCount(partEnd) {
  return order(partEnd);
}

/**
 * The bells a part end leaves alone. A conductor calls from one of these,
 * because a bell that moves between the parts is a bell whose work changes.
 *
 * @param {import('../core/row.js').Row} partEnd
 * @returns {number[]}
 */
export function observationBells(partEnd) {
  return fixedBells(partEnd);
}

/**
 * A description of the part end for a report.
 *
 * @param {import('../core/row.js').Row} partEnd
 * @returns {{ parts: number, shape: string, fixed: number[], heads: string[] }}
 */
export function describePartEnd(partEnd) {
  return {
    parts: partCount(partEnd),
    shape: cycleLengths(partEnd).join('.'),
    fixed: observationBells(partEnd),
    heads: partHeads(partEnd).map((row) => row.toString()),
  };
}

/**
 * Break a list of lead heads into parts of equal length, and say what the part
 * end is. Gives null when the lead heads do not divide evenly, which is the
 * usual answer for a composition that was not written in parts.
 *
 * @param {readonly import('../core/row.js').Row[]} leadHeads the lead head after every lead
 * @param {number} parts how many parts to look for
 * @returns {{ partEnd: import('../core/row.js').Row, leadsPerPart: number } | null}
 */
export function findPartEnd(leadHeads, parts) {
  if (!Number.isInteger(parts) || parts < 2) {
    throw new ValidationError(`a composition cannot be in ${parts} parts`);
  }
  if (leadHeads.length === 0 || leadHeads.length % parts !== 0) {
    return null;
  }
  const leadsPerPart = leadHeads.length / parts;
  const partEnd = leadHeads[leadsPerPart - 1];
  if (order(partEnd) !== parts) {
    return null;
  }
  for (let part = 1; part < parts; part += 1) {
    const expected = transpose(partEnd, leadHeads[(part - 1) * leadsPerPart + leadsPerPart - 1]);
    const found = leadHeads[part * leadsPerPart + leadsPerPart - 1];
    if (found === undefined || !found.equals(expected)) {
      return null;
    }
  }
  return { partEnd, leadsPerPart };
}

/**
 * Look for any part structure a run of lead heads has, trying the part counts
 * that divide the number of leads. The largest number of parts is reported,
 * because that is the one a conductor would use.
 *
 * @param {readonly import('../core/row.js').Row[]} leadHeads
 * @returns {{ parts: number, partEnd: import('../core/row.js').Row, leadsPerPart: number } | null}
 */
export function detectParts(leadHeads) {
  for (let parts = leadHeads.length; parts >= 2; parts -= 1) {
    if (leadHeads.length % parts !== 0) {
      continue;
    }
    const found = findPartEnd(leadHeads, parts);
    if (found !== null) {
      return { parts, ...found };
    }
  }
  return null;
}

/**
 * True when a row could be a part end, that is when repeating it brings the
 * composition back to rounds in more than one part.
 *
 * @param {import('../core/row.js').Row} row
 * @returns {boolean}
 */
export function isPartEnd(row) {
  return !row.isRounds() && order(row) > 1;
}

/**
 * The row a part head takes another row to, which is how the second part of a
 * composition is worked out from the first.
 *
 * @param {import('../core/row.js').Row} partHead
 * @param {import('../core/row.js').Row} row
 * @returns {import('../core/row.js').Row}
 */
export function inPart(partHead, row) {
  return transpose(partHead, row);
}

/**
 * The part heads written out for a composition of a given number of parts at a
 * stage, when the part end is a plain cyclic shift. Used as a starting point
 * when a composer asks for a cyclic composition.
 *
 * @param {number} stage
 * @param {number} parts
 * @returns {import('../core/row.js').Row[]}
 */
export function cyclicPartHeads(stage, parts) {
  if (!Number.isInteger(parts) || parts < 2 || parts > stage) {
    throw new ValidationError(`there is no cyclic part end for ${parts} parts on ${stage}`);
  }
  const bells = rounds(stage).toArray();
  const moving = bells.slice(stage - parts);
  moving.push(moving.shift());
  const partEnd = rounds(stage).toArray();
  for (let index = 0; index < parts; index += 1) {
    partEnd[stage - parts + index] = moving[index];
  }
  return partHeads(new Row(partEnd));
}
