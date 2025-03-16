import { describe, it, expect } from 'vitest';
import {
  FORMAT_VERSION,
  methodToJson,
  methodFromJson,
  libraryToJson,
  libraryFromJson,
} from '../../src/method/serialise.js';
import { MethodLibrary } from '../../src/method/library.js';
import { method } from '../../src/method/method.js';
import { ValidationError } from '../../src/errors.js';

const cambridge = () => method('Cambridge', 8, 'x38x14x1258x36x14x58x16x78,12');
const plainBobMinor = () => method('Plain', 6, 'x1x1x1,2');

describe('methodToJson', () => {
  it('carries the notation and the derived facts', () => {
    expect(methodToJson(cambridge())).toEqual({
      title: 'Cambridge Surprise Major',
      name: 'Cambridge',
      stage: 8,
      notation: 'x38x14x1258x36x14x58x16x78,12',
      class: 'Surprise',
      leadLength: 32,
      leadHead: '15738264',
      huntBells: [1],
    });
  });

  it('refuses anything that is not a method', () => {
    expect(() => methodToJson({ name: 'Cambridge' })).toThrow(ValidationError);
  });
});

describe('methodFromJson', () => {
  it('rebuilds a method from a document', () => {
    const rebuilt = methodFromJson(methodToJson(cambridge()));
    expect(rebuilt.name).toBe('Cambridge');
    expect(rebuilt.stage).toBe(8);
    expect(rebuilt.ringsTheSameAs(cambridge())).toBe(true);
  });

  it('takes the name and stage from the title when they are missing', () => {
    const rebuilt = methodFromJson({
      title: 'Plain Bob Minor',
      notation: 'x1x1x1,2',
    });
    expect(rebuilt.name).toBe('Plain');
    expect(rebuilt.stage).toBe(6);
  });

  it('ignores a class that disagrees with the notation', () => {
    const rebuilt = methodFromJson({
      name: 'Cambridge',
      stage: 8,
      class: 'Treble Bob',
      notation: 'x38x14x1258x36x14x58x16x78,12',
    });
    expect(methodToJson(rebuilt).class).toBe('Surprise');
  });

  it('refuses a document with no notation', () => {
    expect(() => methodFromJson({ title: 'Plain Bob Minor' })).toThrow(/needs its notation/);
  });

  it('refuses a document with no name and no title', () => {
    expect(() => methodFromJson({ notation: 'x1x1x1,2' })).toThrow(
      /either a title or a name and a stage/,
    );
  });

  it('refuses something that is not an object', () => {
    expect(() => methodFromJson(null)).toThrow(/must be an object/);
    expect(() => methodFromJson('Plain Bob Minor')).toThrow(ValidationError);
  });
});

describe('libraryToJson', () => {
  it('stamps the format and the version', () => {
    const library = new MethodLibrary();
    library.add(cambridge());
    const document = libraryToJson(library);
    expect(document.format).toBe('garterhole.methods');
    expect(document.version).toBe(FORMAT_VERSION);
    expect(document.methods).toHaveLength(1);
  });

  it('writes the methods in title order', () => {
    const library = new MethodLibrary();
    library.addAll([plainBobMinor(), cambridge()]);
    expect(libraryToJson(library).methods.map((entry) => entry.title)).toEqual([
      'Cambridge Surprise Major',
      'Plain Bob Minor',
    ]);
  });

  it('refuses anything that is not a library', () => {
    expect(() => libraryToJson([cambridge()])).toThrow(ValidationError);
  });
});

describe('libraryFromJson', () => {
  it('round trips a library', () => {
    const library = new MethodLibrary();
    library.addAll([cambridge(), plainBobMinor()]);
    const rebuilt = libraryFromJson(JSON.parse(JSON.stringify(libraryToJson(library))));
    expect(rebuilt.titles()).toEqual(library.titles());
  });

  it('refuses a version it does not understand', () => {
    expect(() => libraryFromJson({ version: 99, methods: [] })).toThrow(
      /understands version 1 but the document is version 99/,
    );
  });

  it('refuses a document with no methods list', () => {
    expect(() => libraryFromJson({ version: FORMAT_VERSION })).toThrow(/needs a list of methods/);
  });

  it('says which method could not be read', () => {
    expect(() =>
      libraryFromJson({
        version: FORMAT_VERSION,
        methods: [methodToJson(cambridge()), { title: 'Broken Major' }],
      }),
    ).toThrow(/method 1 could not be read/);
  });

  it('refuses something that is not an object', () => {
    expect(() => libraryFromJson(null)).toThrow(/must be an object/);
  });
});
