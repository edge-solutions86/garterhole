import { describe, it, expect } from 'vitest';
import {
  MIN_STAGE,
  MAX_STAGE,
  isSupportedStage,
  stageName,
  stageFromName,
  isOddStage,
  hasCover,
  bellsInTower,
  supportedStages,
} from '../../src/core/stage.js';

describe('stage support', () => {
  it('accepts every stage between the documented bounds', () => {
    for (let stage = MIN_STAGE; stage <= MAX_STAGE; stage += 1) {
      expect(isSupportedStage(stage)).toBe(true);
    }
  });

  it('rejects stages outside the bounds', () => {
    expect(isSupportedStage(MIN_STAGE - 1)).toBe(false);
    expect(isSupportedStage(MAX_STAGE + 1)).toBe(false);
  });

  it('rejects values that are not whole numbers', () => {
    expect(isSupportedStage(6.5)).toBe(false);
    expect(isSupportedStage(Number.NaN)).toBe(false);
    expect(isSupportedStage('8')).toBe(false);
  });
});

describe('stage names', () => {
  it('names the common stages the way ringers do', () => {
    expect(stageName(6)).toBe('Minor');
    expect(stageName(8)).toBe('Major');
    expect(stageName(10)).toBe('Royal');
    expect(stageName(12)).toBe('Maximus');
  });

  it('names the odd stages', () => {
    expect(stageName(5)).toBe('Doubles');
    expect(stageName(7)).toBe('Triples');
    expect(stageName(9)).toBe('Caters');
    expect(stageName(11)).toBe('Cinques');
  });

  it('throws for a stage it has no name for', () => {
    expect(() => stageName(2)).toThrow(RangeError);
    expect(() => stageName(17)).toThrow(/no stage name/);
  });

  it('round trips a name back to its stage', () => {
    for (const stage of supportedStages()) {
      expect(stageFromName(stageName(stage))).toBe(stage);
    }
  });

  it('resolves a name regardless of case or padding', () => {
    expect(stageFromName('  major ')).toBe(8);
    expect(stageFromName('MINOR')).toBe(6);
  });

  it('throws for a name that is not a stage', () => {
    expect(() => stageFromName('Quadruples')).toThrow(/unknown stage name/);
  });
});

describe('cover bells', () => {
  it('treats odd stages as covered', () => {
    expect(isOddStage(5)).toBe(true);
    expect(isOddStage(6)).toBe(false);
    expect(hasCover(5)).toBe(true);
    expect(hasCover(6)).toBe(false);
  });

  it('counts the cover bell as an extra bell in the tower', () => {
    expect(bellsInTower(5)).toBe(6);
    expect(bellsInTower(7)).toBe(8);
  });

  it('leaves an even stage alone', () => {
    expect(bellsInTower(8)).toBe(8);
    expect(bellsInTower(12)).toBe(12);
  });
});

describe('supportedStages', () => {
  it('lists every stage in ascending order', () => {
    const stages = supportedStages();
    expect(stages[0]).toBe(MIN_STAGE);
    expect(stages[stages.length - 1]).toBe(MAX_STAGE);
    expect(stages).toEqual([...stages].sort((a, b) => a - b));
  });
});
