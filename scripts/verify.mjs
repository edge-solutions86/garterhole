#!/usr/bin/env node
/**
 * Run the same checks CI runs, in the same order, and stop at the first one
 * that fails. Having it here rather than as a chain of npm scripts means the
 * output says which stage failed instead of leaving that to be worked out from
 * an exit code.
 */
import { spawnSync } from 'node:child_process';
import process from 'node:process';

const STAGES = [
  { name: 'lint', command: 'npm', args: ['run', '--silent', 'lint'] },
  { name: 'format', command: 'npm', args: ['run', '--silent', 'format:check'] },
  { name: 'surface', command: 'npm', args: ['run', '--silent', 'surface:check'] },
  { name: 'test', command: 'npm', args: ['run', '--silent', 'test'] },
  { name: 'coverage', command: 'npm', args: ['run', '--silent', 'coverage'] },
];

/**
 * @param {{ name: string, command: string, args: string[] }} stage
 * @returns {number} the exit status
 */
function runStage(stage) {
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

const only = process.argv.slice(2);
const wanted = only.length > 0 ? STAGES.filter((stage) => only.includes(stage.name)) : STAGES;

if (wanted.length === 0) {
  process.stderr.write(`no such stage. known stages: ${STAGES.map((s) => s.name).join(', ')}\n`);
  process.exit(2);
}

for (const stage of wanted) {
  const status = runStage(stage);
  if (status !== 0) {
    process.stderr.write(`verify stopped at ${stage.name}\n`);
    process.exit(status);
  }
}

process.stdout.write(`all ${wanted.length} stages passed\n`);
