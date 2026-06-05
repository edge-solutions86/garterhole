import { describe, it, expect } from 'vitest';
import * as garterhole from '../src/index.js';

/**
 * The public surface.
 *
 * This test exists to make a removal deliberate. Anything exported from the
 * index is something somebody may have imported, so dropping one should fail a
 * test rather than turning up as a broken import in a tower's script.
 */

const EXPECTED = [
  'Bell',
  'Call',
  'CallRegistry',
  'Change',
  'CompetencyRegister',
  'Composition',
  'ConfigError',
  'ConflictError',
  'Diary',
  'GarterholeError',
  'Interval',
  'IntervalSet',
  'Journal',
  'Logger',
  'MAX_STAGE',
  'MIN_STAGE',
  'Method',
  'MethodLibrary',
  'NotFoundError',
  'NotationError',
  'PRODUCT',
  'Performance',
  'PerformanceCollection',
  'Repository',
  'Ringer',
  'Roster',
  'Row',
  'RowBlock',
  'Service',
  'Snapshot',
  'StorageError',
  'StrikeStream',
  'StrikingSession',
  'TREBLE',
  'TimingModel',
  'Tower',
  'TruthTracker',
  'UnitOfWork',
  'VERSION',
  'ValidationError',
];

describe('the public surface', () => {
  it('exports everything it says it does', () => {
    for (const name of EXPECTED) {
      expect(garterhole[name], `${name} is missing from the index`).toBeDefined();
    }
  });

  it('exports the version', () => {
    expect(garterhole.VERSION).toMatch(/^\d+\.\d+\.\d+$/);
    expect(garterhole.buildLabel()).toContain(garterhole.PRODUCT);
  });

  it('exports enough to ring a touch without reaching inside', () => {
    const bob = garterhole.method('Plain', 8, 'x18x18x18x18,12');
    const rung = garterhole.expandTouch({ method: bob, touch: garterhole.parseTouch('3(H)') });
    expect(garterhole.touchLength(rung)).toBe(336);
    expect(garterhole.prove(rung.block).isTrue).toBe(true);
  });

  it('exports enough to classify a method without reaching inside', () => {
    const cambridge = garterhole.method('Cambridge', 8, 'x38x14x1258x36x14x58x16x78,12');
    expect(garterhole.classify(cambridge).className).toBe('Surprise');
    expect(garterhole.titleOf(cambridge)).toBe('Cambridge Surprise Major');
  });

  it('exports enough to analyse striking without reaching inside', () => {
    const model = new garterhole.TimingModel({ stage: 4, interval: 250 });
    const strikes = [];
    for (let rowIndex = 0; rowIndex < 4; rowIndex += 1) {
      for (let place = 1; place <= 4; place += 1) {
        strikes.push({ bell: place, at: model.expectedAt(rowIndex, place) });
      }
    }
    const session = garterhole.analyseStriking(4, strikes);
    expect(session.accuracy().rms).toBeCloseTo(0, 6);
  });

  it('exports the two surfaces', () => {
    expect(typeof garterhole.buildApp).toBe('function');
    expect(typeof garterhole.runCommandLine).toBe('function');
  });

  it('exports nothing undefined', () => {
    for (const [name, value] of Object.entries(garterhole)) {
      expect(value, `${name} is exported but undefined`).toBeDefined();
    }
  });
});
