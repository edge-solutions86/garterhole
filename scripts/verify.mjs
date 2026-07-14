/**
 * Run with `node scripts/verify.mjs [stage...]`.
 *
 * Run the same checks CI runs, in the same order, and stop at the first one
 * that fails. Having it here rather than as a chain of npm scripts means the
 * output says which stage failed instead of leaving that to be worked out from
 * an exit code.
 *
 * There is no shebang, and the work happens under a guard, so that the tests
 * can import this file to check the stage list without running it.
 */
import { spawnSync } from 'node:child_process';
import process from 'node:process';

/** Every stage, in the order they run. */
export const STAGES = Object.freeze([
  { name: 'lint', command: 'npm', args: ['run', '--silent', 'lint'] },
  { name: 'format', command: 'npm', args: ['run', '--silent', 'format:check'] },
  { name: 'surface', command: 'npm', args: ['run', '--silent', 'surface:check'] },
  { name: 'release', command: 'npm', args: ['run', '--silent', 'release:check'] },
  { name: 'test', command: 'npm', args: ['run', '--silent', 'test'] },
  { name: 'coverage', command: 'npm', args: ['run', '--silent', 'coverage'] },
]);

/**
 * The stages a run should cover, given what was asked for on the command line.
 *
 * @param {readonly string[]} wanted
 * @returns {typeof STAGES[number][]}
 */
export function stagesFor(wanted) {
  if (wanted.length === 0) {
    return [...STAGES];
  }
  return STAGES.filter((stage) => wanted.includes(stage.name));
}

/**
 * Run one stage.
 *
 * @param {{ name: string, command: string, args: string[] }} stage
 * @returns {number} the exit status
 */
export function runStage(stage) {
  const started = Date.now();
  process.stdout.write(`==> ${stage.name}\n`);
  const result = spawnSync(stage.command, stage.args, {
    stdio: 'inherit',
    shell: process.platform === 'win32',
  });
  const elapsed = ((Date.now() - started) / 1000).toFixed(1);
  const status = result.status ?? 1;
  process.stdout.write(`<== ${stage.name} ${status === 0 ? 'ok' : 'failed'} in ${elapsed}s\n`);
  return status;
}

/**
 * Run the stages that were asked for, stopping at the first failure.
 *
 * @param {readonly string[]} wanted
 * @returns {number} the status to exit with
 */
export function verify(wanted) {
  const stages = stagesFor(wanted);
  if (stages.length === 0) {
    process.stderr.write(`no such stage. known stages: ${STAGES.map((s) => s.name).join(', ')}\n`);
    return 2;
  }
  for (const stage of stages) {
    const status = runStage(stage);
    if (status !== 0) {
      process.stderr.write(`verify stopped at ${stage.name}\n`);
      return status;
    }
  }
  process.stdout.write(`all ${stages.length} stages passed\n`);
  return 0;
}

if (process.argv[1]?.endsWith('verify.mjs')) {
  process.exitCode = verify(process.argv.slice(2));
}
