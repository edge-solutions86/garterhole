import { requireFlag, requireNumber, phraseFlag } from '../args.js';
import { Performance } from '../../records/performance.js';
import { boardLines } from '../../records/report.js';
import { writerNamed, formats } from '../../records/writers.js';
import { toList, toDate } from '../../validation/coerce.js';
import { ValidationError } from '../../errors.js';

/**
 * The performance commands.
 *
 * Filing a quarter peal on the night is the thing a tower actually does with
 * this, and it has to be one line typed with cold hands. So the band is a comma
 * separated list, the date defaults to today, and the conductor defaults to the
 * first name in the band, which is what a tower writes on the board anyway when
 * the conductor rang the treble.
 */

/** The flags these commands take. */
export const RECORD_FLAGS = Object.freeze({
  tower: { takesValue: true },
  date: { takesValue: true, short: 'd' },
  title: { takesValue: true, short: 't' },
  changes: { takesValue: true, short: 'c' },
  band: { takesValue: true, short: 'b' },
  conductor: { takesValue: true },
  composer: { takesValue: true },
  duration: { takesValue: true },
  format: { takesValue: true, short: 'f' },
  ringer: { takesValue: true },
  json: {},
  quiet: {},
});

/**
 * `records file --tower "St Mary" --title "Plain Bob Minor" --changes 1260 --band Alice,Bob,...`
 *
 * @param {import('../args.js').ParsedArgs} parsed
 * @param {{ service: object, output: import('../output.js').Output, today?: Date }} context
 */
export function fileRecord(parsed, context) {
  const band = toList(requireFlag(parsed, 'band'), 'band');
  if (band.length === 0) {
    throw new ValidationError('a performance needs a band');
  }
  const date =
    parsed.flags.date === undefined
      ? (context.today ?? new Date())
      : toDate(String(parsed.flags.date), 'date');
  const filed = context.service.filePerformance({
    tower: requireFlag(parsed, 'tower'),
    date: date.toISOString().slice(0, 10),
    title: phraseFlag(parsed, 'title'),
    changes: requireNumber(parsed, 'changes'),
    band,
    conductor: requireFlag(parsed, 'conductor', band[0]),
    composer: parsed.flags.composer === undefined ? undefined : String(parsed.flags.composer),
    duration: parsed.flags.duration === undefined ? undefined : requireNumber(parsed, 'duration'),
  });
  for (const warning of filed.warnings) {
    context.output.note(warning);
  }
  context.output.note(`filed as ${filed.id}`);
  context.output.answer(filed);
}

/**
 * `records list [--ringer Alice]`
 *
 * @param {import('../args.js').ParsedArgs} parsed
 * @param {{ service: object, output: import('../output.js').Output }} context
 */
export function listRecords(parsed, context) {
  const ringer = parsed.flags.ringer === undefined ? undefined : String(parsed.flags.ringer);
  const records = context.service
    .listPerformances()
    .filter((entry) => ringer === undefined || entry.band.includes(ringer));
  if (context.output.json) {
    context.output.answer(records);
    return;
  }
  if (records.length === 0) {
    context.output.note('nothing filed yet');
    return;
  }
  context.output.table(
    [
      ['date', 'tower', 'changes', 'method'],
      ...records.map((entry) => [entry.date, entry.tower, String(entry.changes), entry.title]),
    ],
    { align: ['left', 'left', 'right', 'left'] },
  );
}

/**
 * `records report [--format csv]`
 *
 * @param {import('../args.js').ParsedArgs} parsed
 * @param {{ service: object, output: import('../output.js').Output }} context
 */
export function reportRecords(parsed, context) {
  const format = requireFlag(parsed, 'format', 'text');
  if (!formats().includes(format)) {
    throw new ValidationError(
      `${JSON.stringify(format)} is not a format, so try one of ${formats().join(', ')}`,
    );
  }
  const records = context.service.listPerformances().map((entry) => performanceFrom(entry));
  const written = writerNamed(format).write(records, {});
  context.output.answer(written);
}

/**
 * `records board --title ... --date ...` prints one performance the way it
 * goes on a peal board.
 *
 * @param {import('../args.js').ParsedArgs} parsed
 * @param {{ service: object, output: import('../output.js').Output }} context
 */
export function printBoard(parsed, context) {
  const records = context.service.listPerformances();
  const wanted =
    parsed.flags.title === undefined
      ? records[0]
      : records.find((entry) => entry.title === String(parsed.flags.title));
  if (wanted === undefined) {
    throw new ValidationError('there is no performance to print');
  }
  context.output.answer(wanted, () => boardLines(performanceFrom(wanted)));
}

/**
 * @param {object} document
 * @returns {Performance}
 */
function performanceFrom(document) {
  return new Performance({ ...document, date: new Date(document.date) });
}
