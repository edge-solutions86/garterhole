import { expandTouch } from './touchExpander.js';
import { parseTouch, PLAIN_SYMBOL } from './touchParser.js';
import { prove, describeProof } from './prover.js';
import { classifyLength, checkLength } from './lengths.js';
import { musicSummary } from './music/scorer.js';
import { detectParts } from './partStructure.js';
import { titleOf } from '../method/title.js';
import { countBy } from '../util/collections.js';
import { Method } from '../method/method.js';
import { ValidationError } from '../errors.js';

/**
 * The report a composer wants back after handing over a calling: how long it
 * is, whether it is true, what it is worth musically, and how it is built.
 *
 * Everything in the report is derived by ringing the touch. Nothing is taken
 * on trust from the composition file, because the whole point of the exercise
 * is to check what the file claims.
 */

/**
 * Ring a touch and report on it.
 *
 * @param {{
 *   method: Method,
 *   touch: string,
 *   calls?: Record<string, import('./call.js').Call>,
 *   observation?: number
 * }} request
 * @returns {{
 *   title: string,
 *   length: number,
 *   kind: string,
 *   comesRound: boolean,
 *   proof: import('./prover.js').Proof,
 *   summary: string,
 *   calls: { symbol: string, count: number }[],
 *   parts: { parts: number, leadsPerPart: number, partEnd: string } | null,
 *   music: ReturnType<typeof musicSummary>
 * }}
 */
export function analyseTouch(request) {
  if (!(request?.method instanceof Method)) {
    throw new ValidationError('a Method is needed to analyse a touch of it');
  }
  if (typeof request.touch !== 'string') {
    throw new ValidationError('the touch must be written out as a string');
  }
  const rung = expandTouch({
    method: request.method,
    touch: parseTouch(request.touch),
    calls: request.calls,
    observation: request.observation,
  });
  const proof = prove(rung.block);
  const detected = detectParts(rung.leads.map((lead) => lead.leadHead));
  return {
    title: titleOf(request.method),
    length: rung.block.changeCount,
    kind: classifyLength(rung.block.changeCount, request.method.stage),
    comesRound: rung.comesRound,
    proof,
    summary: describeProof(proof),
    calls: callCounts(rung.leads),
    parts:
      detected === null
        ? null
        : {
            parts: detected.parts,
            leadsPerPart: detected.leadsPerPart,
            partEnd: detected.partEnd.toString(),
          },
    music: musicSummary(rung.block.rows.slice(0, -1), request.method.stage),
  };
}

/**
 * Check a touch against what it claims to be, and list everything that does
 * not add up.
 *
 * @param {ReturnType<typeof analyseTouch>} analysis
 * @param {{ length?: number, kind?: 'peal' | 'quarter peal', stage: number }} claim
 * @returns {string[]} the problems, empty when the claim holds
 */
export function checkClaim(analysis, claim) {
  const problems = [];
  if (!analysis.comesRound) {
    problems.push('the touch does not come round');
  }
  if (!analysis.proof.isTrue) {
    problems.push(analysis.summary);
  }
  if (claim.length !== undefined && claim.length !== analysis.length) {
    problems.push(`the calling gives ${analysis.length} changes, not the ${claim.length} claimed`);
  }
  if (claim.kind !== undefined) {
    const checked = checkLength(analysis.length, claim.stage, claim.kind);
    if (!checked.ok) {
      problems.push(checked.reason);
    }
  }
  return problems;
}

/**
 * How many of each call the touch has, plain leads left out.
 *
 * @param {readonly { symbol: string }[]} leads
 * @returns {{ symbol: string, count: number }[]}
 */
export function callCounts(leads) {
  const counts = countBy(
    leads.filter((lead) => lead.symbol !== PLAIN_SYMBOL),
    (lead) => lead.symbol,
  );
  return [...counts.entries()]
    .map(([symbol, count]) => ({ symbol, count }))
    .sort((left, right) => left.symbol.localeCompare(right.symbol));
}

/**
 * The report as lines of text, which is what the CLI prints.
 *
 * @param {ReturnType<typeof analyseTouch>} analysis
 * @returns {string[]}
 */
export function reportLines(analysis) {
  const lines = [
    `${analysis.title}`,
    `${analysis.length} changes, ${analysis.kind}`,
    analysis.summary,
  ];
  if (analysis.parts !== null) {
    lines.push(
      `${analysis.parts.parts} parts of ${analysis.parts.leadsPerPart} leads, part end ${analysis.parts.partEnd}`,
    );
  }
  const calls = analysis.calls.map((entry) => `${entry.count}${entry.symbol}`).join(' ');
  lines.push(calls === '' ? 'no calls' : `calls: ${calls}`);
  lines.push(`music score ${analysis.music.score}`);
  for (const run of analysis.music.runs) {
    if (run.total > 0) {
      lines.push(`  ${run.length} bell runs: ${run.total} (${run.front} front, ${run.back} back)`);
    }
  }
  return lines;
}
