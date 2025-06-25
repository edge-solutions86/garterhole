import { describe, it, expect } from 'vitest';
import { StrikeStream, streamFrom } from '../../src/striking/stream.js';
import { ValidationError } from '../../src/errors.js';

/**
 * Six bells struck evenly, one row after another.
 *
 * @param {number} rows
 * @param {number} [gap]
 */
function evenStriking(rows, gap = 250) {
  const strikes = [];
  let at = 0;
  for (let row = 0; row < rows; row += 1) {
    for (let bell = 1; bell <= 6; bell += 1) {
      strikes.push({ bell, at });
      at += gap;
    }
  }
  return strikes;
}

describe('StrikeStream construction', () => {
  it('keeps the strikes it was given', () => {
    const stream = new StrikeStream(6, evenStriking(2));
    expect(stream.length).toBe(12);
    expect(stream.stage).toBe(6);
  });

  it('is frozen', () => {
    const stream = new StrikeStream(6, evenStriking(1));
    expect(Object.isFrozen(stream)).toBe(true);
    expect(Object.isFrozen(stream.strikes)).toBe(true);
  });

  it('accepts an empty stream', () => {
    expect(new StrikeStream(6, []).length).toBe(0);
    expect(new StrikeStream(6, []).duration).toBe(0);
  });

  it('refuses an unsupported stage', () => {
    expect(() => new StrikeStream(2, [])).toThrow(/stage 2 is not supported/);
  });

  it('refuses strikes that are not an array', () => {
    expect(() => new StrikeStream(6, null)).toThrow(/needs an array of strikes/);
  });

  it('refuses a bell that is not on the stage', () => {
    expect(() => new StrikeStream(6, [{ bell: 7, at: 0 }])).toThrow(
      'strike 0 names bell 7, which is not on 6',
    );
  });

  it('refuses a strike with no time', () => {
    expect(() => new StrikeStream(6, [{ bell: 1 }])).toThrow('strike 0 has no time');
    expect(() => new StrikeStream(6, [{ bell: 1, at: Number.NaN }])).toThrow(ValidationError);
  });

  it('refuses a strike that is not an object', () => {
    expect(() => new StrikeStream(6, ['1@0'])).toThrow('strike 0 is not an object');
  });

  it('refuses strikes that are out of order', () => {
    expect(
      () =>
        new StrikeStream(6, [
          { bell: 1, at: 100 },
          { bell: 2, at: 50 },
        ]),
    ).toThrow('strike 1 is at 50 which is before strike 0');
  });
});

describe('streamFrom', () => {
  it('sorts the strikes into time order', () => {
    const stream = streamFrom(6, [
      { bell: 2, at: 250 },
      { bell: 1, at: 0 },
    ]);
    expect(stream.strikes.map((strike) => strike.bell)).toEqual([1, 2]);
  });

  it('refuses something that is not an array', () => {
    expect(() => streamFrom(6, 'strikes')).toThrow(ValidationError);
  });
});

describe('reading a stream', () => {
  const stream = new StrikeStream(6, evenStriking(4));

  it('measures the recording', () => {
    expect(stream.duration).toBe(23 * 250);
  });

  it('reports the gaps between blows', () => {
    expect(new Set(stream.gaps())).toEqual(new Set([250]));
    expect(stream.gaps()).toHaveLength(23);
  });

  it('counts the blows each bell struck', () => {
    expect(stream.blowsPerBell().get(1)).toBe(4);
    expect(stream.blowsPerBell().get(6)).toBe(4);
  });

  it('finds nothing missing in an even stream', () => {
    expect(stream.bellsWithMissingBlows()).toEqual([]);
  });

  it('finds a bell that stopped ringing', () => {
    const short = new StrikeStream(
      6,
      evenStriking(4).filter((strike) => strike.bell !== 3 || strike.at < 3000),
    );
    expect(short.bellsWithMissingBlows()).toContain(3);
  });

  it('takes a slice by time', () => {
    expect(stream.between(0, 1500)).toHaveLength(6);
    expect(stream.between(1500, 3000)).toHaveLength(6);
  });

  it('iterates', () => {
    expect([...stream]).toHaveLength(24);
  });
});
