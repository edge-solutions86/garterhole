import { Tower } from './tower.js';
import { Bell, bellWeighing } from './bell.js';
import { Ringer } from './ringer.js';
import { CompetencyRegister } from './competency.js';
import { ValidationError } from '../errors.js';

/**
 * Towers and bands on disk.
 *
 * A tower record is the thing most likely to be typed once and then carried
 * from machine to machine for twenty years, so the format is written down
 * plainly and every reader checks the version before it believes anything.
 *
 * Weights are written the way a tower board writes them rather than as pounds,
 * because somebody will read this file with their own eyes and `23-2-11` is
 * what they expect to see.
 */

/** The version stamped into every document written here. */
export const FORMAT_VERSION = 1;

/**
 * @param {Tower} tower
 * @returns {object}
 */
export function towerToJson(tower) {
  if (!(tower instanceof Tower)) {
    throw new ValidationError('only a Tower can be serialised');
  }
  return {
    format: 'garterhole.tower',
    version: FORMAT_VERSION,
    id: tower.id,
    name: tower.name,
    place: tower.place,
    bells: tower.bells.map((bell) => ({
      number: bell.number,
      weight: bell.weightText,
      note: bell.note,
      founder: bell.founder,
      cast: bell.cast,
    })),
  };
}

/**
 * @param {object} document
 * @returns {Tower}
 */
export function towerFromJson(document) {
  assertDocument(document, 'garterhole.tower');
  if (!Array.isArray(document.bells)) {
    throw new ValidationError('a tower document needs its bells');
  }
  const bells = document.bells.map((entry, index) => bellFromJson(entry, index));
  return new Tower({
    name: String(document.name ?? ''),
    place: document.place === null ? undefined : document.place,
    bells,
  });
}

/**
 * @param {object} entry
 * @param {number} index
 * @returns {Bell}
 */
function bellFromJson(entry, index) {
  if (entry === null || typeof entry !== 'object') {
    throw new ValidationError(`bell ${index} is not an object`);
  }
  const details = {
    note: entry.note === null ? undefined : entry.note,
    founder: entry.founder === null ? undefined : entry.founder,
    cast: entry.cast === null ? undefined : entry.cast,
  };
  if (typeof entry.weight === 'string') {
    return bellWeighing(entry.number, entry.weight, details);
  }
  if (typeof entry.weightPounds === 'number') {
    return new Bell({ number: entry.number, weightPounds: entry.weightPounds, ...details });
  }
  throw new ValidationError(`bell ${index} does not say what it weighs`);
}

/**
 * A band and what it can ring, as one document.
 *
 * @param {{ band: readonly Ringer[], competency: CompetencyRegister }} state
 * @returns {object}
 */
export function bandToJson(state) {
  if (!Array.isArray(state?.band)) {
    throw new ValidationError('a band document needs a band');
  }
  if (!(state.competency instanceof CompetencyRegister)) {
    throw new ValidationError('a band document needs a competency register');
  }
  return {
    format: 'garterhole.band',
    version: FORMAT_VERSION,
    ringers: state.band.map((member) => ({
      id: member.id,
      name: member.name,
      heaviestBellPounds: member.heaviestBellPounds,
      roles: [...member.roles],
      notes: member.notes,
    })),
    competency: state.band.map((member) => ({
      ringer: member.id,
      methods: state.competency.repertoireOf(member.id),
    })),
  };
}

/**
 * @param {object} document
 * @returns {{ band: Ringer[], competency: CompetencyRegister }}
 */
export function bandFromJson(document) {
  assertDocument(document, 'garterhole.band');
  if (!Array.isArray(document.ringers)) {
    throw new ValidationError('a band document needs its ringers');
  }
  const band = document.ringers.map(
    (entry) =>
      new Ringer({
        name: String(entry.name ?? ''),
        id: entry.id,
        heaviestBellPounds:
          entry.heaviestBellPounds === null ? undefined : entry.heaviestBellPounds,
        roles: entry.roles ?? [],
        notes: entry.notes,
      }),
  );
  const competency = new CompetencyRegister();
  for (const entry of document.competency ?? []) {
    for (const record of entry.methods ?? []) {
      competency.record(entry.ringer, record.title, record.level);
    }
  }
  return { band, competency };
}

/**
 * @param {unknown} document
 * @param {string} format
 */
function assertDocument(document, format) {
  if (document === null || typeof document !== 'object') {
    throw new ValidationError(`a ${format} document must be an object`);
  }
  if (document.format !== format) {
    throw new ValidationError(
      `this reads ${format} documents and that one says ${JSON.stringify(document.format)}`,
    );
  }
  if (document.version !== FORMAT_VERSION) {
    throw new ValidationError(
      `this reads version ${FORMAT_VERSION} and that document is version ${document.version}`,
    );
  }
}
