import { describe, it, expect } from 'vitest';
import {
  Composition,
  compositionToJson,
  compositionFromJson,
} from '../../src/composition/composition.js';
import { method } from '../../src/method/method.js';
import { ValidationError } from '../../src/errors.js';

const plainBobMajor = () => method('Plain', 8, 'x18x18x18x18,12');

const sound = () =>
  new Composition({
    composer: 'A Ringer',
    method: plainBobMajor(),
    touch: '3(H)',
    claimedLength: 336,
  });

describe('Composition', () => {
  it('keeps what it was given', () => {
    const composition = sound();
    expect(composition.composer).toBe('A Ringer');
    expect(composition.touch).toBe('3(H)');
    expect(composition.claimedLength).toBe(336);
  });

  it('is frozen', () => {
    expect(Object.isFrozen(sound())).toBe(true);
  });

  it('squashes stray whitespace in the calling', () => {
    const composition = new Composition({
      composer: '  A Ringer ',
      method: plainBobMajor(),
      touch: ' 3 (H) ',
    });
    expect(composition.composer).toBe('A Ringer');
    expect(composition.touch).toBe('3 (H)');
  });

  it('titles itself the way a peal board would', () => {
    expect(sound().title).toBe('336 Plain Bob Major');
    expect(sound().toString()).toBe('336 Plain Bob Major by A Ringer');
  });

  it('leaves the length out of the title when none was claimed', () => {
    const composition = new Composition({
      composer: 'A Ringer',
      method: plainBobMajor(),
      touch: '3(H)',
    });
    expect(composition.title).toBe('Plain Bob Major');
  });

  it('refuses a definition that is not an object', () => {
    expect(() => new Composition('3(H)')).toThrow('a composition needs a definition object');
  });

  it('refuses a missing composer or calling', () => {
    expect(() => new Composition({ composer: '', method: plainBobMajor(), touch: '3(H)' })).toThrow(
      /a composer must be a non empty string/,
    );
    expect(() => new Composition({ composer: 'A', method: plainBobMajor(), touch: '' })).toThrow(
      /a calling must be a non empty string/,
    );
  });

  it('refuses something that is not a method', () => {
    expect(() => new Composition({ composer: 'A', method: 'Plain Bob', touch: '3(H)' })).toThrow(
      'a composition needs a Method',
    );
  });

  it('refuses a claimed length that is not a positive whole number', () => {
    expect(
      () =>
        new Composition({
          composer: 'A',
          method: plainBobMajor(),
          touch: '3(H)',
          claimedLength: 0,
        }),
    ).toThrow(/cannot be 0 changes long/);
  });
});

describe('analysing a composition', () => {
  it('rings it and reports', () => {
    expect(sound().analyse().length).toBe(336);
  });

  it('finds nothing wrong with a sound composition', () => {
    expect(sound().problems()).toEqual([]);
    expect(sound().isSound()).toBe(true);
  });

  it('catches a length that does not match the calling', () => {
    const wrong = new Composition({
      composer: 'A Ringer',
      method: plainBobMajor(),
      touch: '3(H)',
      claimedLength: 1280,
    });
    expect(wrong.isSound()).toBe(false);
    expect(wrong.problems()[0]).toMatch(/gives 336 changes, not the 1280 claimed/);
  });
});

describe('serialising a composition', () => {
  it('writes the format and version', () => {
    const document = compositionToJson(sound());
    expect(document.format).toBe('garterhole.composition');
    expect(document.version).toBe(1);
  });

  it('round trips', () => {
    const document = JSON.parse(JSON.stringify(compositionToJson(sound())));
    const rebuilt = compositionFromJson(document);
    expect(rebuilt.composer).toBe('A Ringer');
    expect(rebuilt.touch).toBe('3(H)');
    expect(rebuilt.claimedLength).toBe(336);
    expect(rebuilt.method.ringsTheSameAs(plainBobMajor())).toBe(true);
  });

  it('writes a missing length as null and reads it back as missing', () => {
    const composition = new Composition({
      composer: 'A Ringer',
      method: plainBobMajor(),
      touch: '3(H)',
    });
    const document = compositionToJson(composition);
    expect(document.claimedLength).toBeNull();
    expect(compositionFromJson(document).claimedLength).toBeUndefined();
  });

  it('refuses a version it does not know', () => {
    expect(() => compositionFromJson({ version: 2 })).toThrow(/the document is version 2/);
  });

  it('refuses something that is not a composition', () => {
    expect(() => compositionToJson({ composer: 'A' })).toThrow(ValidationError);
    expect(() => compositionFromJson(null)).toThrow(/must be an object/);
  });
});
