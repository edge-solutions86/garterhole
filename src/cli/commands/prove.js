import { requireFlag, requireNumber, flagIsSet } from '../args.js';
import { titleFrom } from './method.js';
import { method as buildMethod } from '../../method/method.js';
import { analyseTouch, reportLines } from '../../composition/analysis.js';
import { expandNotation } from '../../notation/expand.js';
import { writeLeadLines } from '../../notation/format.js';
import { RowBlock } from '../../core/rowBlock.js';
import { rounds } from '../../core/row.js';

/**
 * Proving a touch from the command line.
 *
 * A conductor writes a calling on the back of an envelope and wants to know two
 * things before the band arrives: how long it is, and whether it is true. This
 * answers both, and it answers them by ringing the touch rather than by looking
 * anything up.
 *
 * The method can come from the library by title, or straight off the command
 * line as notation, because a conductor trying a method the tower has never
 * rung should not have to file it first.
 */

/** The flags these commands take. */
export const PROVE_FLAGS = Object.freeze({
  title: { takesValue: true, short: 't' },
  touch: { takesValue: true },
  notation: { takesValue: true },
  stage: { takesValue: true, short: 's' },
  name: { takesValue: true, short: 'n' },
  lead: {},
  json: {},
  quiet: {},
});

/**
 * `prove --title "Plain Bob Major" --touch 3(H)`
 *
 * @param {import('../args.js').ParsedArgs} parsed
 * @param {{ service: object, output: import('../output.js').Output }} context
 */
export function prove(parsed, context) {
  const method = methodFrom(parsed, context);
  const analysis = analyseTouch({ method, touch: requireFlag(parsed, 'touch') });
  context.output.answer(
    {
      title: analysis.title,
      length: analysis.length,
      kind: analysis.kind,
      comesRound: analysis.comesRound,
      true: analysis.proof.isTrue,
      summary: analysis.summary,
      calls: analysis.calls,
      parts: analysis.parts,
      musicScore: analysis.music.score,
    },
    () => reportLines(analysis),
  );
  if (!analysis.proof.isTrue) {
    context.output.problem('this touch is false');
  }
}

/**
 * `lead --notation x18x18x18x18,12 --stage 8`
 *
 * @param {import('../args.js').ParsedArgs} parsed
 * @param {{ service: object, output: import('../output.js').Output }} context
 */
export function printLead(parsed, context) {
  const method = methodFrom(parsed, context);
  const block = RowBlock.fromChanges(rounds(method.stage), method.changes);
  const lines = writeLeadLines(method.changes, block.rows);
  context.output.answer({ title: method.name, rows: block.rows.map(String) }, () => lines);
}

/**
 * The method a command is about: the one named in the library, or one built
 * from notation given on the line.
 *
 * @param {import('../args.js').ParsedArgs} parsed
 * @param {{ service: object }} context
 * @returns {import('../../method/method.js').Method}
 */
export function methodFrom(parsed, context) {
  if (parsed.flags.notation !== undefined && parsed.flags.notation !== true) {
    const stage = requireNumber(parsed, 'stage');
    const notation = String(parsed.flags.notation);
    expandNotation(notation, stage);
    return buildMethod(requireFlag(parsed, 'name', 'Unnamed'), stage, notation);
  }
  const title = titleFrom(parsed);
  const document = context.service.getMethod(title);
  return buildMethod(document.name, document.stage, document.notation);
}

/**
 * True when the caller asked for the lead to be printed as well.
 *
 * @param {import('../args.js').ParsedArgs} parsed
 * @returns {boolean}
 */
export function wantsLead(parsed) {
  return flagIsSet(parsed, 'lead');
}
