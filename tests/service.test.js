import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { Service, METHODS, PERFORMANCES } from '../src/service.js';
import { Repository } from '../src/store/repository.js';
import { Journal } from '../src/store/journal.js';
import { ConflictError, NotFoundError, ValidationError } from '../src/errors.js';

/** @type {string} */
let directory;
/** @type {Repository} */
let repository;
/** @type {Service} */
let service;

beforeEach(() => {
  directory = mkdtempSync(join(tmpdir(), 'garterhole-'));
  repository = new Repository({ journal: new Journal(join(directory, 'journal.jsonl')) });
  service = new Service({ repository });
});

afterEach(() => {
  rmSync(directory, { recursive: true, force: true });
});

const plainBobMajor = { name: 'Plain', stage: 8, notation: 'x18x18x18x18,12' };
const six = ['Alice', 'Bob', 'Carol', 'Dan', 'Erin', 'Frank'];

const quarter = (overrides = {}) => ({
  tower: 'St Mary',
  date: '2025-05-18',
  title: 'Plain Bob Minor',
  changes: 1260,
  band: six,
  conductor: 'Alice',
  ...overrides,
});

describe('methods', () => {
  it('files a method under the title its notation gives it', () => {
    const document = service.addMethod(plainBobMajor);
    expect(document.title).toBe('Plain Bob Major');
    expect(repository.has(METHODS, 'Plain Bob Major')).toBe(true);
  });

  it('works the class out rather than trusting the caller', () => {
    service.addMethod({ name: 'Cambridge', stage: 8, notation: 'x38x14x1258x36x14x58x16x78,12' });
    expect(service.getMethod('Cambridge Surprise Major').class).toBe('Surprise');
  });

  it('refuses a method that is already there', () => {
    service.addMethod(plainBobMajor);
    expect(() => service.addMethod(plainBobMajor)).toThrow(ConflictError);
  });

  it('refuses notation it cannot read', () => {
    expect(() => service.addMethod({ name: 'Bad', stage: 8, notation: 'x1?x' })).toThrow(
      /not part of place notation/,
    );
  });

  it('lists what it holds in title order', () => {
    service.addMethod(plainBobMajor);
    service.addMethod({ name: 'Cambridge', stage: 8, notation: 'x38x14x1258x36x14x58x16x78,12' });
    expect(service.listMethods().map((entry) => entry.title)).toEqual([
      'Cambridge Surprise Major',
      'Plain Bob Major',
    ]);
  });

  it('complains about one it does not hold', () => {
    expect(() => service.getMethod('Nothing Major')).toThrow(NotFoundError);
  });

  it('removes one', () => {
    service.addMethod(plainBobMajor);
    expect(service.removeMethod('Plain Bob Major')).toBe(true);
    expect(service.removeMethod('Plain Bob Major')).toBe(false);
  });

  it('describes one with the facts derived from its changes', () => {
    service.addMethod(plainBobMajor);
    const described = service.describeMethod('Plain Bob Major');
    expect(described.className).toBe('Bob');
    expect(described.huntBells).toEqual([1]);
  });
});

describe('analysing a touch', () => {
  beforeEach(() => {
    service.addMethod(plainBobMajor);
  });

  it('rings the touch and reports', () => {
    const analysis = service.analyse({ title: 'Plain Bob Major', touch: '3(H)' });
    expect(analysis.length).toBe(336);
    expect(analysis.true).toBe(true);
    expect(analysis.comesRound).toBe(true);
  });

  it('reports the parts and the calls', () => {
    const analysis = service.analyse({ title: 'Plain Bob Major', touch: '3(H)' });
    expect(analysis.parts.parts).toBe(3);
    expect(analysis.calls).toEqual([{ symbol: '-', count: 3 }]);
  });

  it('complains about a method it does not hold', () => {
    expect(() => service.analyse({ title: 'Nothing Major', touch: '3(H)' })).toThrow(NotFoundError);
  });

  it('complains about a touch it cannot read', () => {
    expect(() => service.analyse({ title: 'Plain Bob Major', touch: 'W)' })).toThrow(
      /without opening/,
    );
  });
});

describe('towers', () => {
  it('files and reads one back', () => {
    service.putTower('st-mary', { name: 'St Mary', bells: 8 });
    expect(service.getTower('st-mary')).toEqual({ name: 'St Mary', bells: 8 });
  });

  it('replaces one that is already there', () => {
    service.putTower('st-mary', { bells: 6 });
    service.putTower('st-mary', { bells: 8 });
    expect(service.listTowers()).toHaveLength(1);
  });

  it('complains about one it does not hold', () => {
    expect(() => service.getTower('nothing')).toThrow(NotFoundError);
  });

  it('refuses something that is not a tower', () => {
    expect(() => service.putTower('st-mary', 'St Mary')).toThrow(ValidationError);
  });
});

describe('performances', () => {
  it('files one and gives back its identifier', () => {
    const filed = service.filePerformance(quarter());
    expect(filed.id).toContain('2025-05-18');
    expect(repository.has(PERFORMANCES, filed.id)).toBe(true);
  });

  it('reports the warnings without refusing', () => {
    const filed = service.filePerformance(quarter({ conductor: undefined }));
    expect(filed.warnings).toContain('no conductor is recorded');
  });

  it('refuses one that does not add up', () => {
    expect(() => service.filePerformance(quarter({ band: six.slice(0, 5) }))).toThrow(
      /needs a band of 6/,
    );
  });

  it('refuses the same performance twice', () => {
    service.filePerformance(quarter());
    expect(() => service.filePerformance(quarter())).toThrow(ConflictError);
  });

  it('writes nothing when it refuses', () => {
    const before = repository.journal.count();
    expect(() => service.filePerformance(quarter({ band: [] }))).toThrow();
    expect(repository.journal.count()).toBe(before);
  });

  it('lists what it holds', () => {
    service.filePerformance(quarter());
    expect(service.listPerformances()).toHaveLength(1);
  });
});

describe('counts', () => {
  it('says what the service holds', () => {
    service.addMethod(plainBobMajor);
    service.putTower('st-mary', { bells: 8 });
    expect(service.counts()).toEqual({ methods: 1, towers: 1, performances: 0 });
  });

  it('refuses options it cannot use', () => {
    expect(() => new Service(null)).toThrow(/needs its options/);
    expect(() => new Service({ repository: {} })).toThrow(/needs a Repository/);
  });
});
