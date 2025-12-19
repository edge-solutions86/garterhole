import { describe, it, expect } from 'vitest';
import {
  systemClock,
  FixedClock,
  describeDuration,
  startOfDay,
  SECOND,
  MINUTE,
  HOUR,
  DAY,
} from '../../src/util/clock.js';
import { ValidationError } from '../../src/errors.js';

describe('systemClock', () => {
  it('reads the real time', () => {
    const clock = systemClock();
    const before = Date.now();
    const seen = clock.now();
    expect(seen).toBeGreaterThanOrEqual(before);
    expect(clock.date()).toBeInstanceOf(Date);
  });
});

describe('FixedClock', () => {
  it('stands still', () => {
    const clock = new FixedClock(1000);
    expect(clock.now()).toBe(1000);
    expect(clock.now()).toBe(1000);
  });

  it('starts at zero by default', () => {
    expect(new FixedClock().now()).toBe(0);
  });

  it('takes a date to start at', () => {
    const clock = new FixedClock(new Date(Date.UTC(2025, 0, 1)));
    expect(clock.date().toISOString()).toBe('2025-01-01T00:00:00.000Z');
  });

  it('moves forward when it is told to', () => {
    const clock = new FixedClock(1000);
    clock.advance(500).advance(500);
    expect(clock.now()).toBe(2000);
  });

  it('can be put at a moment', () => {
    const clock = new FixedClock(1000);
    expect(clock.set(5000).now()).toBe(5000);
    expect(clock.set(new Date(7000)).now()).toBe(7000);
  });

  it('refuses to start or move nowhere', () => {
    expect(() => new FixedClock('now')).toThrow(/is not a moment to start a clock at/);
    expect(() => new FixedClock(0).advance(-1)).toThrow(/cannot move forward by -1/);
    expect(() => new FixedClock(0).advance(Number.NaN)).toThrow(ValidationError);
  });
});

describe('the units', () => {
  it('build on each other', () => {
    expect(MINUTE).toBe(60 * SECOND);
    expect(HOUR).toBe(60 * MINUTE);
    expect(DAY).toBe(24 * HOUR);
  });
});

describe('describeDuration', () => {
  it('writes milliseconds under a second', () => {
    expect(describeDuration(0)).toBe('0ms');
    expect(describeDuration(250)).toBe('250ms');
  });

  it('writes seconds under a minute', () => {
    expect(describeDuration(1500)).toBe('1.5s');
    expect(describeDuration(59 * SECOND)).toBe('59.0s');
  });

  it('writes minutes under an hour', () => {
    expect(describeDuration(5 * MINUTE)).toBe('5m');
    expect(describeDuration(59 * MINUTE)).toBe('59m');
  });

  it('writes hours and minutes above that', () => {
    expect(describeDuration(3 * HOUR + 5 * MINUTE)).toBe('3h 5m');
    expect(describeDuration(2 * HOUR)).toBe('2h 0m');
  });

  it('refuses something that is not a length of time', () => {
    expect(() => describeDuration(-1)).toThrow(/is not a length of time/);
    expect(() => describeDuration('a while')).toThrow(ValidationError);
  });
});

describe('startOfDay', () => {
  it('drops the time', () => {
    const moment = new Date(Date.UTC(2025, 4, 18, 19, 30, 15));
    expect(startOfDay(moment).toISOString()).toBe('2025-05-18T00:00:00.000Z');
  });

  it('leaves a moment already at midnight alone', () => {
    const midnight = new Date(Date.UTC(2025, 4, 18));
    expect(startOfDay(midnight).getTime()).toBe(midnight.getTime());
  });

  it('refuses something that is not a date', () => {
    expect(() => startOfDay('2025-05-18')).toThrow(/a date is needed/);
    expect(() => startOfDay(new Date('nonsense'))).toThrow(ValidationError);
  });
});
