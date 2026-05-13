import { requireFlag, phraseFlag } from '../args.js';
import { toList } from '../../validation/coerce.js';
import { towerFrom } from '../../http/routes/towers.js';
import { stageName } from '../../core/stage.js';
import { ValidationError } from '../../errors.js';

/**
 * The tower commands.
 *
 * Adding a tower is typing its name and the weights off the board, which is
 * what a bell weight looks like written down: `4-0-0,4-2-7,5-1-14`. The
 * numbering is filled in from the order, because the treble is always first and
 * nobody wants to type that.
 */

/** The flags these commands take. */
export const TOWER_FLAGS = Object.freeze({
  name: { takesValue: true, short: 'n' },
  place: { takesValue: true, short: 'p' },
  weights: { takesValue: true, short: 'w' },
  id: { takesValue: true },
  json: {},
  quiet: {},
});

/**
 * `tower add --name "St Mary" --place Bridgend --weights 4-0-0,4-2-7,...`
 *
 * @param {import('../args.js').ParsedArgs} parsed
 * @param {{ service: object, output: import('../output.js').Output }} context
 */
export function addTower(parsed, context) {
  const weights = toList(requireFlag(parsed, 'weights'), 'weights');
  if (weights.length === 0) {
    throw new ValidationError('a tower needs at least one bell');
  }
  const document = {
    name: phraseFlag(parsed, 'name'),
    place: parsed.flags.place === undefined ? undefined : String(parsed.flags.place),
    bells: weights.map((weight, index) => ({ number: index + 1, weight })),
  };
  const tower = towerFrom(document);
  const stored = {
    ...document,
    id: tower.id,
    size: tower.size,
    stages: tower.stages(),
  };
  context.service.putTower(tower.id, stored);
  context.output.note(`filed as ${tower.id}`);
  context.output.answer(stored, () => describeTower(tower));
}

/**
 * `tower list`
 *
 * @param {import('../args.js').ParsedArgs} parsed
 * @param {{ service: object, output: import('../output.js').Output }} context
 */
export function listTowers(parsed, context) {
  const towers = context.service.listTowers();
  if (context.output.json) {
    context.output.answer(towers);
    return;
  }
  if (towers.length === 0) {
    context.output.note('no towers yet');
    return;
  }
  context.output.table(
    [
      ['id', 'name', 'bells', 'rings up to'],
      ...towers.map((entry) => [
        entry.id,
        entry.name,
        String(entry.size),
        entry.stages.length === 0 ? 'nothing' : stageName(entry.stages[0]),
      ]),
    ],
    { align: ['left', 'left', 'right', 'left'] },
  );
}

/**
 * `tower show --id bridgend-st-mary`
 *
 * @param {import('../args.js').ParsedArgs} parsed
 * @param {{ service: object, output: import('../output.js').Output }} context
 */
export function showTower(parsed, context) {
  const id = requireFlag(parsed, 'id');
  const document = context.service.getTower(id);
  context.output.answer(document, () => describeTower(towerFrom(document)));
}

/**
 * @param {import('../../tower/tower.js').Tower} tower
 * @returns {string[]}
 */
function describeTower(tower) {
  return [
    tower.describe(),
    '',
    ...tower.bells.map((bell) => `  ${bell.number}  ${bell.weightText}`),
  ];
}
