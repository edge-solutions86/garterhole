import { describe, it, expect } from 'vitest';
import {
  analyseTouch,
  checkClaim,
  callCounts,
  reportLines,
} from '../../src/composition/analysis.js';
import { method } from '../../src/method/method.js';
import { ValidationError } from '../../src/errors.js';

const plainBobMajor = () => method('Plain', 8, 'x18x18x18x18,12');
const plainBobMinor = () => method('Plain', 6, 'x1x1x1,2');

describe('analyseTouch', () => {
  it('reports the length and what it counts as', () => {
    const analysis = analyseTouch({ method: plainBobMajor(), touch: '3(H)' });
    expect(analysis.length).toBe(336);
    expect(analysis.kind).toBe('touch');
    expect(analysis.comesRound).toBe(true);
  });

  it('titles the method from its changes', () => {
    expect(analyseTouch({ method: plainBobMajor(), touch: '3(H)' }).title).toBe('Plain Bob Major');
  });

  it('proves the touch', () => {
    const analysis = analyseTouch({ method: plainBobMajor(), touch: '3(H)' });
    expect(analysis.proof.isTrue).toBe(true);
    expect(analysis.summary).toBe('true, 336 rows');
  });

  it('counts the calls', () => {
    const analysis = analyseTouch({ method: plainBobMajor(), touch: '3(H)' });
    expect(analysis.calls).toEqual([{ symbol: '-', count: 3 }]);
  });

  it('finds the part structure', () => {
    const analysis = analyseTouch({ method: plainBobMajor(), touch: '3(H)' });
    expect(analysis.parts).toEqual({
      parts: 3,
      leadsPerPart: 7,
      partEnd: '14235678',
    });
  });

  it('summarises the music', () => {
    const analysis = analyseTouch({ method: plainBobMajor(), touch: '3(H)' });
    expect(analysis.music.score).toBeGreaterThan(0);
    expect(analysis.music.runs[0].length).toBe(4);
  });

  it('reports a touch that does not come round', () => {
    const analysis = analyseTouch({ method: plainBobMinor(), touch: '-.-.-.' });
    expect(analysis.comesRound).toBe(false);
  });

  it('refuses something that is not a method', () => {
    expect(() => analyseTouch({ method: 'Plain Bob', touch: '3(H)' })).toThrow(ValidationError);
  });

  it('refuses a touch that is not written out', () => {
    expect(() => analyseTouch({ method: plainBobMinor(), touch: 42 })).toThrow(
      /must be written out as a string/,
    );
  });
});

describe('checkClaim', () => {
  const analysis = () => analyseTouch({ method: plainBobMajor(), touch: '3(H)' });

  it('finds nothing wrong with an honest claim', () => {
    expect(checkClaim(analysis(), { length: 336, stage: 8 })).toEqual([]);
  });

  it('catches a length that does not match', () => {
    expect(checkClaim(analysis(), { length: 1280, stage: 8 })).toEqual([
      'the calling gives 336 changes, not the 1280 claimed',
    ]);
  });

  it('catches a touch claimed as a quarter that is too short', () => {
    expect(checkClaim(analysis(), { kind: 'quarter peal', stage: 8 })[0]).toMatch(
      /needs at least 1250 changes/,
    );
  });

  it('catches a touch that does not come round', () => {
    const short = analyseTouch({ method: plainBobMinor(), touch: '-.-.-.' });
    expect(checkClaim(short, { stage: 6 })).toContain('the touch does not come round');
  });
});

describe('callCounts', () => {
  it('counts each symbol and leaves the plains out', () => {
    expect(
      callCounts([{ symbol: '-' }, { symbol: '.' }, { symbol: 's' }, { symbol: '-' }]),
    ).toEqual([
      { symbol: '-', count: 2 },
      { symbol: 's', count: 1 },
    ]);
  });

  it('counts nothing for a plain course', () => {
    expect(callCounts([{ symbol: '.' }, { symbol: '.' }])).toEqual([]);
  });
});

describe('reportLines', () => {
  it('writes the report out', () => {
    const lines = reportLines(analyseTouch({ method: plainBobMajor(), touch: '3(H)' }));
    expect(lines[0]).toBe('Plain Bob Major');
    expect(lines[1]).toBe('336 changes, touch');
    expect(lines[2]).toBe('true, 336 rows');
    expect(lines).toContain('3 parts of 7 leads, part end 14235678');
    expect(lines).toContain('calls: 3-');
  });

  it('says so when there are no calls', () => {
    const lines = reportLines(analyseTouch({ method: plainBobMinor(), touch: '.....' }));
    expect(lines).toContain('no calls');
  });
});
