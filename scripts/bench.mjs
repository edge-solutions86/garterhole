/**
 * Run with `node scripts/bench.mjs`.
 *
 * There is no shebang here on purpose. These scripts are imported by the tests
 * as well as run from the command line, and a shebang on a module that gets
 * imported is read as the first line of the program rather than as a comment
 * once a checkout has converted the line endings. The entry point in `bin/` keeps its shebang, because nothing
 * imports it.
 *
 * How long the slow parts take.
 *
 * Three things in this library are slow enough to matter, and they are slow for
 * different reasons: proving a long touch is bounded by the number of rows,
 * finding the false course heads is bounded by the square of the course length,
 * and the touch search is bounded by the branching. A change that makes any of
 * them worse should be noticed by somebody, and a number printed at the end of
 * a run is how.
 *
 * This is not a regression gate. It prints and it exits zero, because a timing
 * that fails a build on a busy machine teaches everybody to ignore it.
 */
import process from 'node:process';
import { method } from '../src/method/method.js';
import { plainCourse } from '../src/method/plainCourse.js';
import { prove } from '../src/composition/prover.js';
import { falseCourseHeads } from '../src/composition/falseness.js';
import { searchTouches } from '../src/composition/search.js';
import { expandTouch } from '../src/composition/touchExpander.js';
import { parseTouch } from '../src/composition/touchParser.js';
import { describeDuration } from '../src/util/clock.js';

const CAMBRIDGE = method('Cambridge', 8, 'x38x14x1258x36x14x58x16x78,12');
const PLAIN_BOB_MINOR = method('Plain', 6, 'x1x1x1,2');
const PLAIN_BOB_MAJOR = method('Plain', 8, 'x18x18x18x18,12');

/**
 * Time something, running it a few times and taking the best.
 *
 * @param {string} name
 * @param {() => unknown} work
 * @param {number} [runs]
 * @returns {{ name: string, ms: number, answer: unknown }}
 */
export function time(name, work, runs = 3) {
  let best = Number.POSITIVE_INFINITY;
  let answer;
  for (let run = 0; run < runs; run += 1) {
    const started = process.hrtime.bigint();
    answer = work();
    const elapsed = Number(process.hrtime.bigint() - started) / 1e6;
    best = Math.min(best, elapsed);
  }
  return { name, ms: best, answer };
}

/** Everything that gets timed. */
export const BENCHMARKS = Object.freeze([
  {
    name: 'ring a plain course of cambridge',
    run: () => plainCourse(CAMBRIDGE).length,
  },
  {
    name: 'prove a plain course of cambridge',
    run: () => prove(plainCourse(CAMBRIDGE)).distinct,
  },
  {
    name: 'ring and prove three bobs home',
    run: () => {
      const rung = expandTouch({ method: PLAIN_BOB_MAJOR, touch: parseTouch('3(H)') });
      return prove(rung.block).rows;
    },
  },
  {
    name: 'false course heads of cambridge',
    run: () => falseCourseHeads(CAMBRIDGE).length,
  },
  {
    name: 'search for touches of plain bob minor',
    run: () => searchTouches({ method: PLAIN_BOB_MINOR, maxLeads: 8, limit: 8 }).length,
  },
]);

/**
 * Run them all.
 *
 * @returns {{ name: string, ms: number, answer: unknown }[]}
 */
export function runBenchmarks() {
  return BENCHMARKS.map((benchmark) => time(benchmark.name, benchmark.run));
}

if (process.argv[1]?.endsWith('bench.mjs')) {
  const results = runBenchmarks();
  const width = Math.max(...results.map((result) => result.name.length));
  for (const result of results) {
    process.stdout.write(
      `${result.name.padEnd(width)}  ${describeDuration(result.ms).padStart(8)}  ${result.answer}\n`,
    );
  }
}
