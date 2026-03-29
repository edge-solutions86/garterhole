import { requireFlag, requireNumber, phraseFlag } from '../args.js';
import { ValidationError } from '../../errors.js';

/**
 * The method commands.
 *
 * `method add` is the one that earns its keep. A tower librarian types the name
 * and the notation off a printed collection, and the class, the lead head and
 * the lead length are worked out rather than copied, which is where the
 * mistakes in a hand kept library come from.
 */

/** The flags these commands take. */
export const METHOD_FLAGS = Object.freeze({
  name: { takesValue: true, short: 'n' },
  stage: { takesValue: true, short: 's' },
  notation: { takesValue: true },
  title: { takesValue: true, short: 't' },
  class: { takesValue: true },
  json: {},
  quiet: {},
});

/**
 * `method add --name Plain --stage 8 --notation x18x18x18x18,12`
 *
 * @param {import('../args.js').ParsedArgs} parsed
 * @param {{ service: object, output: import('../output.js').Output }} context
 */
export function addMethod(parsed, context) {
  const document = context.service.addMethod({
    name: requireFlag(parsed, 'name'),
    stage: requireNumber(parsed, 'stage'),
    notation: requireFlag(parsed, 'notation'),
  });
  context.output.note(`filed as ${document.title}`);
  context.output.answer(document, (value) => describeMethod(value));
}

/**
 * `method list [--stage 8] [--class Surprise]`
 *
 * @param {import('../args.js').ParsedArgs} parsed
 * @param {{ service: object, output: import('../output.js').Output }} context
 */
export function listMethods(parsed, context) {
  const wantedStage = parsed.flags.stage;
  const wantedClass = parsed.flags.class;
  const methods = context.service.listMethods().filter((entry) => {
    if (wantedStage !== undefined && String(entry.stage) !== String(wantedStage)) {
      return false;
    }
    if (wantedClass !== undefined && entry.class !== String(wantedClass)) {
      return false;
    }
    return true;
  });
  if (context.output.json) {
    context.output.answer(methods);
    return;
  }
  if (methods.length === 0) {
    context.output.note('nothing in the library yet');
    return;
  }
  context.output.table(
    [
      ['title', 'lead', 'lead head', 'notation'],
      ...methods.map((entry) => [
        entry.title,
        String(entry.leadLength),
        entry.leadHead,
        entry.notation,
      ]),
    ],
    { align: ['left', 'right', 'left', 'left'] },
  );
}

/**
 * `method show --title "Cambridge Surprise Major"`
 *
 * @param {import('../args.js').ParsedArgs} parsed
 * @param {{ service: object, output: import('../output.js').Output }} context
 */
export function showMethod(parsed, context) {
  const title = titleFrom(parsed);
  context.output.answer(context.service.describeMethod(title), (value) => describeMethod(value));
}

/**
 * `method remove --title "Cambridge Surprise Major"`
 *
 * @param {import('../args.js').ParsedArgs} parsed
 * @param {{ service: object, output: import('../output.js').Output }} context
 */
export function removeMethod(parsed, context) {
  const title = titleFrom(parsed);
  const removed = context.service.removeMethod(title);
  if (!removed) {
    throw new ValidationError(`the library does not hold ${JSON.stringify(title)}`);
  }
  context.output.note(`removed ${title}`);
  context.output.answer({ removed: title });
}

/**
 * The title of the method a command is about.
 *
 * @param {import('../args.js').ParsedArgs} parsed
 * @returns {string}
 */
export function titleFrom(parsed) {
  return phraseFlag(parsed, 'title');
}

/**
 * @param {object} method
 * @returns {string[]}
 */
function describeMethod(method) {
  const lines = [
    method.title,
    `notation  ${method.notation}`,
    `lead      ${method.leadLength} changes, lead head ${method.leadHead}`,
  ];
  if (method.className !== undefined) {
    lines.push(`class     ${method.className}`);
  }
  if (method.huntBells !== undefined) {
    lines.push(`hunt      ${method.huntBells.join(', ')}`);
  }
  return lines;
}
