import { describe, it, expect } from 'vitest';
import {
  defaultScheme,
  scoreMusic,
  countRule,
  musicSummary,
  compareMusic,
} from '../../src/composition/music/scorer.js';
import { parseRows } from '../../src/core/rowParse.js';
import { plainCourse } from '../../src/method/plainCourse.js';
import { method } from '../../src/method/method.js';
import { ValidationError } from '../../src/errors.js';

const musical = () => parseRows('42135678 12346857 13527468 87654321 13572468');
const dull = () => parseRows('13527468 24681357');

describe('defaultScheme', () => {
  it('weights longer runs more', () => {
    const scheme = defaultScheme(8);
    const four = scheme.find((rule) => rule.runLength === 4);
    const six = scheme.find((rule) => rule.runLength === 6);
    expect(six.weight).toBeGreaterThan(four.weight);
  });

  it('leaves out runs longer than the stage', () => {
    expect(defaultScheme(5).some((rule) => rule.runLength === 6)).toBe(false);
  });

  it('refuses a stage with no scheme', () => {
    expect(() => defaultScheme(3)).toThrow(/no default music scheme on 3/);
  });
});

describe('scoreMusic', () => {
  it('adds the weighted counts up', () => {
    const scored = scoreMusic(musical(), { stage: 8 });
    expect(scored.total).toBeGreaterThan(0);
    expect(scored.lines.map((line) => line.name)).toContain('four bell runs');
  });

  it('gives every line its count, weight and score', () => {
    const line = scoreMusic(musical(), { stage: 8 }).lines.find(
      (entry) => entry.name === 'four bell runs',
    );
    expect(line.score).toBe(line.count * line.weight);
  });

  it('scores an unmusical block low', () => {
    expect(scoreMusic(dull(), { stage: 8 }).total).toBe(0);
  });

  it('scores an empty block zero', () => {
    expect(scoreMusic([], { stage: 8 }).total).toBe(0);
  });

  it('takes a scheme of its own', () => {
    const scheme = [{ name: 'tenors together', pattern: '*78', weight: 10 }];
    const scored = scoreMusic(musical(), { stage: 8, scheme });
    expect(scored.lines).toHaveLength(1);
    expect(scored.total).toBe(scored.lines[0].count * 10);
  });
});

describe('countRule', () => {
  const rows = musical();

  it('counts a pattern', () => {
    expect(countRule(rows, { name: 'back', pattern: '*5678', weight: 1 })).toBe(1);
  });

  it('counts a run length', () => {
    expect(countRule(rows, { name: 'runs', runLength: 4, weight: 1 })).toBeGreaterThan(0);
  });

  it('counts a named row', () => {
    expect(countRule(rows, { name: 'queens', namedRow: 'Queens', weight: 1 })).toBe(1);
    expect(countRule(rows, { name: 'backrounds', namedRow: 'Backrounds', weight: 1 })).toBe(1);
  });

  it('refuses a rule with nothing to count', () => {
    expect(() => countRule(rows, { name: 'empty', weight: 1 })).toThrow(/has nothing to count/);
  });

  it('refuses a rule with no weight', () => {
    expect(() => countRule(rows, { name: 'unweighted', pattern: '*' })).toThrow(/has no weight/);
  });

  it('refuses something that is not a rule', () => {
    expect(() => countRule(rows, null)).toThrow(ValidationError);
  });
});

describe('musicSummary', () => {
  it('reports the score, the runs and the named rows', () => {
    const summary = musicSummary(musical(), 8);
    expect(summary.score).toBeGreaterThan(0);
    expect(summary.runs[0].length).toBe(4);
    expect(summary.named.map((entry) => entry.name).sort()).toEqual(['Backrounds', 'Queens']);
  });

  it('leaves rounds out of the named rows', () => {
    const summary = musicSummary(parseRows('12345678'), 8);
    expect(summary.named).toEqual([]);
  });

  it('summarises a whole plain course', () => {
    const summary = musicSummary(plainCourse(method('Plain', 8, 'x18x18x18x18,12')), 8);
    expect(summary.runs).toHaveLength(5);
    expect(summary.score).toBeGreaterThanOrEqual(0);
  });
});

describe('compareMusic', () => {
  it('is positive when the first block is more musical', () => {
    expect(compareMusic(musical(), dull(), { stage: 8 })).toBeGreaterThan(0);
  });

  it('is zero for two blocks that score the same', () => {
    expect(compareMusic(dull(), dull(), { stage: 8 })).toBe(0);
  });
});
