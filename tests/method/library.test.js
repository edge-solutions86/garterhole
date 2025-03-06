import { describe, it, expect, beforeEach } from 'vitest';
import { MethodLibrary } from '../../src/method/library.js';
import { method } from '../../src/method/method.js';
import { ConflictError, NotFoundError, ValidationError } from '../../src/errors.js';

const cambridge = () => method('Cambridge', 8, 'x38x14x1258x36x14x58x16x78,12');
const yorkshire = () => method('Yorkshire', 8, 'x38x14x58x16x12x38x14x78,12');
const bristol = () => method('Bristol', 8, 'x58x14.58x58.36.14x14.58x14x18,18');
const plainBobMinor = () => method('Plain', 6, 'x1x1x1,2');

describe('adding methods', () => {
  /** @type {MethodLibrary} */
  let library;

  beforeEach(() => {
    library = new MethodLibrary();
  });

  it('starts empty', () => {
    expect(library.size).toBe(0);
    expect(library.titles()).toEqual([]);
  });

  it('files a method under the title its changes give it', () => {
    expect(library.add(cambridge())).toBe('Cambridge Surprise Major');
    expect(library.size).toBe(1);
  });

  it('adds several at once', () => {
    expect(library.addAll([cambridge(), bristol(), plainBobMinor()])).toEqual([
      'Cambridge Surprise Major',
      'Bristol Surprise Major',
      'Plain Bob Minor',
    ]);
  });

  it('refuses a second method with the same title', () => {
    library.add(cambridge());
    expect(() => library.add(cambridge())).toThrow(ConflictError);
    expect(() => library.add(cambridge())).toThrow(/already holds "Cambridge Surprise Major"/);
  });

  it('refuses a method that rings the same rows under another name', () => {
    library.add(cambridge());
    expect(() => library.add(method('Impostor', 8, 'x38x14x1258x36x14x58x16x78,12'))).toThrow(
      /rings the same rows as "Cambridge Surprise Major"/,
    );
  });

  it('refuses anything that is not a method', () => {
    expect(() => library.add({ name: 'Cambridge' })).toThrow(ValidationError);
  });

  it('stops part way through a batch that clashes', () => {
    expect(() => library.addAll([cambridge(), yorkshire(), cambridge()])).toThrow(ConflictError);
    expect(library.size).toBe(2);
  });
});

describe('reading the library', () => {
  /** @type {MethodLibrary} */
  let library;

  beforeEach(() => {
    library = new MethodLibrary();
    library.addAll([cambridge(), yorkshire(), bristol(), plainBobMinor()]);
  });

  it('says whether it holds a title', () => {
    expect(library.has('Cambridge Surprise Major')).toBe(true);
    expect(library.has('Superlative Surprise Major')).toBe(false);
  });

  it('gets a method by title', () => {
    expect(library.get('Bristol Surprise Major').name).toBe('Bristol');
  });

  it('throws for a title it does not hold', () => {
    expect(() => library.get('Superlative Surprise Major')).toThrow(NotFoundError);
    expect(() => library.get('Superlative Surprise Major')).toThrow(/no method with id/);
  });

  it('finds without throwing', () => {
    expect(library.find('Cambridge Surprise Major')).toBeDefined();
    expect(library.find('Superlative Surprise Major')).toBeUndefined();
  });

  it('finds a method by what it rings', () => {
    const impostor = method('Impostor', 8, '-38-14-1258-36-14-58-16-78,12');
    expect(library.findByNotation(impostor).name).toBe('Cambridge');
  });

  it('gives undefined when nothing rings those rows', () => {
    expect(library.findByNotation(method('Nothing', 6, 'x16x16x16x16x16x16'))).toBeUndefined();
  });

  it('lists titles in order', () => {
    expect(library.titles()).toEqual([
      'Bristol Surprise Major',
      'Cambridge Surprise Major',
      'Plain Bob Minor',
      'Yorkshire Surprise Major',
    ]);
  });

  it('iterates in title order', () => {
    expect([...library].map((m) => m.name)).toEqual(['Bristol', 'Cambridge', 'Plain', 'Yorkshire']);
  });
});

describe('removing methods', () => {
  it('takes a method out and frees its title', () => {
    const library = new MethodLibrary();
    library.add(cambridge());
    expect(library.remove('Cambridge Surprise Major').name).toBe('Cambridge');
    expect(library.size).toBe(0);
    expect(() => library.add(cambridge())).not.toThrow();
  });

  it('frees the notation as well', () => {
    const library = new MethodLibrary();
    library.add(cambridge());
    library.remove('Cambridge Surprise Major');
    expect(() => library.add(method('Impostor', 8, 'x38x14x1258x36x14x58x16x78,12'))).not.toThrow();
  });

  it('throws for a title it does not hold', () => {
    expect(() => new MethodLibrary().remove('Nothing Major')).toThrow(NotFoundError);
  });
});

describe('slugFor', () => {
  it('makes a title safe for a URL', () => {
    expect(MethodLibrary.slugFor('Cambridge Surprise Major')).toBe('cambridge-surprise-major');
  });
});
