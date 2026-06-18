import { describe, it, expect } from 'vitest';
import {
  FORMAT_VERSION,
  towerToJson,
  towerFromJson,
  bandToJson,
  bandFromJson,
} from '../../src/tower/serialise.js';
import { Tower } from '../../src/tower/tower.js';
import { bellWeighing } from '../../src/tower/bell.js';
import { ringer } from '../../src/tower/ringer.js';
import { CompetencyRegister } from '../../src/tower/competency.js';
import { ValidationError } from '../../src/errors.js';

const stMary = () =>
  new Tower({
    name: 'St Mary the Virgin',
    place: 'Bridgend',
    bells: [
      bellWeighing(1, '4-0-0', { note: 'D' }),
      bellWeighing(2, '4-2-7'),
      bellWeighing(3, '5-1-14'),
      bellWeighing(4, '6-0-0'),
      bellWeighing(5, '8-2-21'),
      bellWeighing(6, '12-1-4', { founder: 'Taylor', cast: 1901 }),
    ],
  });

const band = () => [
  ringer('Alice Vane', { id: 'alice', roles: ['conductor'] }),
  ringer('Bob Reed', { id: 'bob', heaviestBellPounds: 900 }),
];

const competency = () =>
  new CompetencyRegister()
    .record('alice', 'Plain Bob Minor', 'conducts')
    .record('bob', 'Plain Bob Minor', 'inside');

describe('towerToJson', () => {
  it('stamps the format and the version', () => {
    const document = towerToJson(stMary());
    expect(document.format).toBe('garterhole.tower');
    expect(document.version).toBe(FORMAT_VERSION);
  });

  it('writes the weights the way a board writes them', () => {
    expect(towerToJson(stMary()).bells[5].weight).toBe('12-1-4');
  });

  it('keeps the details a bell carries', () => {
    const document = towerToJson(stMary());
    expect(document.bells[0].note).toBe('D');
    expect(document.bells[5].founder).toBe('Taylor');
    expect(document.bells[5].cast).toBe(1901);
  });

  it('refuses anything that is not a tower', () => {
    expect(() => towerToJson({ name: 'St Mary' })).toThrow(ValidationError);
  });
});

describe('towerFromJson', () => {
  it('round trips', () => {
    const rebuilt = towerFromJson(JSON.parse(JSON.stringify(towerToJson(stMary()))));
    expect(rebuilt.name).toBe('St Mary the Virgin');
    expect(rebuilt.size).toBe(6);
    expect(rebuilt.tenor.weightText).toBe('12-1-4');
    expect(rebuilt.id).toBe(stMary().id);
  });

  it('takes a weight in pounds as well', () => {
    const rebuilt = towerFromJson({
      format: 'garterhole.tower',
      version: FORMAT_VERSION,
      name: 'Test',
      place: null,
      bells: [{ number: 1, weightPounds: 448 }],
    });
    expect(rebuilt.bells[0].weightText).toBe('4-0-0');
  });

  it('refuses a document of another kind', () => {
    expect(() => towerFromJson({ format: 'garterhole.band', version: 1 })).toThrow(
      /this reads garterhole.tower documents/,
    );
  });

  it('refuses a version it does not know', () => {
    expect(() => towerFromJson({ format: 'garterhole.tower', version: 9 })).toThrow(
      /that document is version 9/,
    );
  });

  it('refuses a document with no bells', () => {
    expect(() =>
      towerFromJson({ format: 'garterhole.tower', version: FORMAT_VERSION, name: 'Test' }),
    ).toThrow(/needs its bells/);
  });

  it('refuses a bell that does not say what it weighs', () => {
    expect(() =>
      towerFromJson({
        format: 'garterhole.tower',
        version: FORMAT_VERSION,
        name: 'Test',
        bells: [{ number: 1 }],
      }),
    ).toThrow(/bell 0 does not say what it weighs/);
  });

  it('refuses something that is not an object', () => {
    expect(() => towerFromJson(null)).toThrow(/must be an object/);
  });
});

describe('bands', () => {
  it('writes the band and what it can ring', () => {
    const document = bandToJson({ band: band(), competency: competency() });
    expect(document.format).toBe('garterhole.band');
    expect(document.ringers).toHaveLength(2);
    expect(document.competency[0].methods[0].title).toBe('Plain Bob Minor');
  });

  it('round trips', () => {
    const document = JSON.parse(
      JSON.stringify(bandToJson({ band: band(), competency: competency() })),
    );
    const rebuilt = bandFromJson(document);
    expect(rebuilt.band.map((member) => member.name)).toEqual(['Alice Vane', 'Bob Reed']);
    expect(rebuilt.band[0].conducts).toBe(true);
    expect(rebuilt.band[1].heaviestBellPounds).toBe(900);
    expect(rebuilt.competency.levelOf('bob', 'Plain Bob Minor')).toBe('inside');
  });

  it('refuses a document it cannot write', () => {
    expect(() => bandToJson({ band: 'everyone', competency: competency() })).toThrow(
      /needs a band/,
    );
    expect(() => bandToJson({ band: band(), competency: {} })).toThrow(
      /needs a competency register/,
    );
  });

  it('refuses a document it cannot read', () => {
    expect(() => bandFromJson({ format: 'garterhole.band', version: FORMAT_VERSION })).toThrow(
      /needs its ringers/,
    );
    expect(() => bandFromJson(null)).toThrow(ValidationError);
  });
});
