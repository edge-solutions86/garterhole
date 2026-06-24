import { describe, it, expect } from 'vitest';
import { time, BENCHMARKS, runBenchmarks } from '../scripts/bench.mjs';

/**
 * The benchmark script is not a gate, but it is code, and code that only runs
 * by hand is code that stops running. These tests check that every benchmark
 * still does what it claims and gives the answer it should, which is what makes
 * the numbers it prints worth reading.
 */

describe('time', () => {
  it('gives the name back with a duration', () => {
    const result = time('nothing', () => 42);
    expect(result.name).toBe('nothing');
    expect(result.ms).toBeGreaterThanOrEqual(0);
    expect(result.answer).toBe(42);
  });

  it('runs the work more than once and keeps the best', () => {
    let runs = 0;
    time(
      'counting',
      () => {
        runs += 1;
        return runs;
      },
      4,
    );
    expect(runs).toBe(4);
  });

  it('runs it once when asked to', () => {
    let runs = 0;
    time('once', () => (runs += 1), 1);
    expect(runs).toBe(1);
  });
});

describe('the benchmarks', () => {
  it('all have a name and something to run', () => {
    expect(BENCHMARKS.length).toBeGreaterThan(3);
    for (const benchmark of BENCHMARKS) {
      expect(benchmark.name.length).toBeGreaterThan(0);
      expect(typeof benchmark.run).toBe('function');
    }
  });

  it('is frozen so nothing is added by accident', () => {
    expect(Object.isFrozen(BENCHMARKS)).toBe(true);
  });

  it('gives the answers they should', () => {
    const results = runBenchmarks();
    const byName = new Map(results.map((result) => [result.name, result.answer]));
    expect(byName.get('ring a plain course of cambridge')).toBe(225);
    expect(byName.get('prove a plain course of cambridge')).toBe(224);
    expect(byName.get('ring and prove three bobs home')).toBe(336);
    expect(byName.get('search for touches of plain bob minor')).toBe(8);
  });

  it('times every one of them', () => {
    const results = runBenchmarks();
    expect(results).toHaveLength(BENCHMARKS.length);
    for (const result of results) {
      expect(Number.isFinite(result.ms)).toBe(true);
    }
  });
});
