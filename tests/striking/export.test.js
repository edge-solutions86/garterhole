import { describe, it, expect } from 'vitest';
import { blowsAsCsv, bellsAsCsv, asDocument, asJson } from '../../src/striking/export.js';
import { analyseStriking } from '../../src/striking/session.js';
import { TimingModel } from '../../src/striking/timingModel.js';
import { plainCourse } from '../../src/method/plainCourse.js';
import { method } from '../../src/method/method.js';
import { ValidationError } from '../../src/errors.js';

const plainBobMinor = () => method('Plain', 6, 'x1x1x1,2');

function courseStrikes(shift = () => 0) {
  const block = plainCourse(plainBobMinor());
  const model = new TimingModel({ stage: 6, interval: 250 });
  const strikes = [];
  block.rows.forEach((row, rowIndex) => {
    row.bells.forEach((bell, place) => {
      strikes.push({ bell, at: model.expectedAt(rowIndex, place + 1) + shift(bell, rowIndex) });
    });
  });
  return strikes;
}

const session = (shift) => analyseStriking(6, courseStrikes(shift));

describe('blowsAsCsv', () => {
  it('opens with a header row', () => {
    expect(blowsAsCsv(session()).split('\n')[0]).toBe('row,place,bell,at,expected,error');
  });

  it('writes one row per blow', () => {
    const lines = blowsAsCsv(session()).split('\n');
    expect(lines).toHaveLength(61 * 6 + 1);
  });

  it('rounds the times so a spreadsheet is readable', () => {
    const line = blowsAsCsv(session()).split('\n')[1];
    expect(line.split(',').every((cell) => /^-?\d+$/.test(cell))).toBe(true);
  });

  it('refuses something that is not a session', () => {
    expect(() => blowsAsCsv({})).toThrow(ValidationError);
  });
});

describe('bellsAsCsv', () => {
  it('writes one row per bell', () => {
    const lines = bellsAsCsv(session()).split('\n');
    expect(lines).toHaveLength(7);
    expect(lines[0]).toBe('bell,blows,rms,meanError');
  });

  it('shows the bell that struck badly', () => {
    const lines = bellsAsCsv(session((bell) => (bell === 4 ? 90 : 0))).split('\n');
    const fourth = lines.find((line) => line.startsWith('4,'));
    expect(Number(fourth.split(',')[2])).toBeGreaterThan(0);
  });
});

describe('asDocument', () => {
  it('stamps the format and the version', () => {
    const document = asDocument(session());
    expect(document.format).toBe('garterhole.striking');
    expect(document.version).toBe(1);
  });

  it('reports the model it fitted', () => {
    const document = asDocument(session());
    expect(document.model.interval).toBeCloseTo(250, 1);
    expect(document.model.pealMinutes).toBeGreaterThan(0);
  });

  it('reports the accuracy and every bell', () => {
    const document = asDocument(session());
    expect(document.accuracy.blows).toBe(366);
    expect(document.bells).toHaveLength(6);
  });

  it('reports the windows', () => {
    const document = asDocument(session(), { windowRows: 20 });
    expect(document.windows.length).toBeGreaterThan(1);
    expect(document.windows[0]).toHaveProperty('from');
  });

  it('reports the faults as sentences', () => {
    const document = asDocument(session((bell) => (bell === 4 ? 120 : 0)));
    expect(document.faults[0]).toContain('bell 4');
  });

  it('reports nothing stopping a clean recording', () => {
    expect(asDocument(session()).stoppedAt).toBeNull();
  });
});

describe('asJson', () => {
  it('writes something that reads back', () => {
    const parsed = JSON.parse(asJson(session()));
    expect(parsed.rows).toBe(61);
    expect(parsed.stage).toBe(6);
  });

  it('refuses something that is not a session', () => {
    expect(() => asJson(null)).toThrow(ValidationError);
  });
});
