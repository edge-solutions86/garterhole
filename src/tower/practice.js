import { assignBells } from './assignment.js';
import { CompetencyRegister } from './competency.js';
import { Tower } from './tower.js';
import { parseTitle } from '../method/title.js';
import { sortBy } from '../util/collections.js';
import { ValidationError } from '../errors.js';

/**
 * Planning a practice night.
 *
 * A practice is not a list of the hardest methods the band can ring. It is a
 * sequence that gets everybody ringing, teaches somebody something, and does
 * not leave the same three people standing out all evening.
 *
 * So the planner works from two lists: what the band present can already ring,
 * which fills most of the evening, and what one or two of them are learning,
 * which fills the rest. A method nobody present can ring is left out however
 * much the captain wants to ring it.
 */

/**
 * @typedef {{
 *   title: string,
 *   stage: number,
 *   purpose: 'repertoire' | 'stretch',
 *   band: string[],
 *   assignment: Map<number, string>,
 *   conductor: string | null
 * }} PracticeItem
 */

/**
 * How many of the band present can ring a method at a level.
 *
 * @param {CompetencyRegister} competency
 * @param {readonly import('./ringer.js').Ringer[]} present
 * @param {string} title
 * @param {string} level
 * @returns {number}
 */
export function readyFor(competency, present, title, level) {
  return present.filter((member) => competency.canRing(member.id, title, level)).length;
}

/**
 * The methods worth ringing tonight, best first.
 *
 * A method is worth ringing when enough of the band present can ring it to fill
 * the bells. Everything else is a stretch, and a stretch is worth one slot at
 * most.
 *
 * @param {{
 *   tower: Tower,
 *   present: readonly import('./ringer.js').Ringer[],
 *   competency: CompetencyRegister
 * }} setting
 * @returns {{ title: string, stage: number, ready: number, learning: number }[]}
 */
export function candidates(setting) {
  assertSetting(setting);
  const found = [];
  for (const title of setting.competency.titles()) {
    let stage;
    try {
      stage = parseTitle(title).stage;
    } catch {
      continue;
    }
    if (!setting.tower.canRing(stage)) {
      continue;
    }
    found.push({
      title,
      stage,
      ready: readyFor(setting.competency, setting.present, title, 'inside'),
      learning: readyFor(setting.competency, setting.present, title, 'learning'),
    });
  }
  return sortBy(
    found,
    (entry) => -entry.ready,
    (entry) => -entry.stage,
    (entry) => entry.title,
  );
}

/**
 * Plan an evening.
 *
 * @param {{
 *   tower: Tower,
 *   present: readonly import('./ringer.js').Ringer[],
 *   competency: CompetencyRegister,
 *   slots?: number,
 *   stretches?: number
 * }} setting
 * @returns {PracticeItem[]}
 */
export function planPractice(setting) {
  assertSetting(setting);
  const slots = setting.slots ?? 6;
  const stretches = setting.stretches ?? 1;
  if (!Number.isInteger(slots) || slots < 1) {
    throw new ValidationError(`a practice of ${slots} touches is not a practice`);
  }
  const options = candidates(setting);
  const plan = [];
  let stretchesUsed = 0;
  for (const option of options) {
    if (plan.length >= slots) {
      break;
    }
    const bellsNeeded = option.stage;
    const purpose = option.ready >= bellsNeeded ? 'repertoire' : 'stretch';
    if (purpose === 'stretch' && stretchesUsed >= stretches) {
      continue;
    }
    const level = purpose === 'repertoire' ? 'inside' : 'learning';
    const item = tryItem(setting, option, level, purpose);
    if (item === null) {
      continue;
    }
    if (purpose === 'stretch') {
      stretchesUsed += 1;
    }
    plan.push(item);
  }
  return plan;
}

/**
 * A line of the plan as a captain would read it out.
 *
 * @param {PracticeItem} item
 * @returns {string}
 */
export function describeItem(item) {
  const bells = [...item.assignment.entries()]
    .map(([bell, ringerId]) => `${bell}:${ringerId}`)
    .join(' ');
  const conductor =
    item.conductor === null ? 'nobody conducting' : `conducted by ${item.conductor}`;
  return `${item.title} (${item.purpose}) ${bells}, ${conductor}`;
}

/**
 * @param {object} setting
 * @param {{ title: string, stage: number }} option
 * @param {string} level
 * @param {string} purpose
 * @returns {PracticeItem | null}
 */
function tryItem(setting, option, level, purpose) {
  try {
    const { assignment, conductor } = assignBells({
      tower: setting.tower,
      band: setting.present,
      stage: option.stage,
      title: option.title,
      competency: setting.competency,
      level,
    });
    return {
      title: option.title,
      stage: option.stage,
      purpose,
      band: [...assignment.values()],
      assignment,
      conductor,
    };
  } catch {
    return null;
  }
}

/**
 * @param {unknown} setting
 */
function assertSetting(setting) {
  if (setting === null || typeof setting !== 'object') {
    throw new ValidationError('a practice needs a setting');
  }
  if (!(setting.tower instanceof Tower)) {
    throw new ValidationError('a practice needs a Tower');
  }
  if (!Array.isArray(setting.present) || setting.present.length === 0) {
    throw new ValidationError('a practice needs somebody to turn up');
  }
  if (!(setting.competency instanceof CompetencyRegister)) {
    throw new ValidationError('a practice needs to know what the band can ring');
  }
}
