import { describe, it, expect, beforeEach } from 'vitest';
import {
  search,
  countByStage,
  countByClass,
  spliceGroups,
  summarise,
} from '../../src/method/librarySearch.js';
import { MethodLibrary } from '../../src/method/library.js';
import { method } from '../../src/method/method.js';
import { ValidationError } from '../../src/errors.js';

const cambridge = () => method('Cambridge', 8, 'x38x14x1258x36x14x58x16x78,12');
const yorkshire = () => method('Yorkshire', 8, 'x38x14x58x16x12x38x14x78,12');
const bristol = () => method('Bristol', 8, 'x58x14.58x58.36.14x14.58x14x18,18');
const plainBobMajor = () => method('Plain', 8, 'x18x18x18x18,12');
const plainBobMinor = () => method('Plain', 6, 'x1x1x1,2');
const littleBob = () => method('Little', 8, 'x18x14x18x12');

/** @type {MethodLibrary} */
let library;

beforeEach(() => {
  library = new MethodLibrary();
  library.addAll([
    cambridge(),
    yorkshire(),
    bristol(),
    plainBobMajor(),
    plainBobMinor(),
    littleBob(),
  ]);
});

describe('search', () => {
  it('gives everything when nothing is asked for', () => {
    expect(search(library)).toHaveLength(6);
  });

  it('filters by stage', () => {
    expect(search(library, { stage: 6 }).map((m) => m.name)).toEqual(['Plain']);
    expect(search(library, { stage: 8 })).toHaveLength(5);
  });

  it('filters by class', () => {
    expect(search(library, { className: 'Surprise' }).map((m) => m.name)).toEqual([
      'Bristol',
      'Cambridge',
      'Yorkshire',
    ]);
  });

  it('filters by a piece of the title', () => {
    expect(search(library, { name: 'bristol' }).map((m) => m.name)).toEqual(['Bristol']);
    expect(search(library, { name: 'major' })).toHaveLength(5);
  });

  it('filters by the little flag', () => {
    expect(search(library, { little: true }).map((m) => m.name)).toEqual(['Little']);
    expect(search(library, { little: false })).toHaveLength(5);
  });

  it('filters by differential', () => {
    expect(search(library, { differential: true })).toEqual([]);
  });

  it('finds what shares a lead head with a method', () => {
    const matches = search(library, { sharesLeadHeadWith: cambridge() });
    expect(matches.map((m) => m.name)).toEqual(['Cambridge', 'Yorkshire']);
  });

  it('combines filters', () => {
    expect(search(library, { stage: 8, className: 'Surprise', name: 'york' })).toHaveLength(1);
  });

  it('returns matches in title order', () => {
    expect(search(library, { stage: 8 }).map((m) => m.name)[0]).toBe('Bristol');
  });

  it('refuses something that is not a library', () => {
    expect(() => search([], {})).toThrow(ValidationError);
  });
});

describe('countByStage', () => {
  it('counts each stage, ascending', () => {
    expect([...countByStage(library).entries()]).toEqual([
      [6, 1],
      [8, 5],
    ]);
  });

  it('gives an empty map for an empty library', () => {
    expect(countByStage(new MethodLibrary()).size).toBe(0);
  });
});

describe('countByClass', () => {
  it('counts each class, in name order', () => {
    expect([...countByClass(library).entries()]).toEqual([
      ['Bob', 2],
      ['Little Bob', 1],
      ['Surprise', 3],
    ]);
  });
});

describe('spliceGroups', () => {
  it('keeps only the groups with something to splice', () => {
    const groups = spliceGroups(library);
    expect(groups.size).toBe(1);
    expect([...groups.values()][0].map((m) => m.name)).toEqual(['Cambridge', 'Yorkshire']);
  });

  it('gives nothing for a library where every lead head is different', () => {
    const small = new MethodLibrary();
    small.addAll([cambridge(), bristol()]);
    expect(spliceGroups(small).size).toBe(0);
  });
});

describe('summarise', () => {
  it('counts the methods by stage', () => {
    expect(summarise(library)).toBe('6 methods: 1 on 6, 5 on 8');
  });

  it('says so when there is nothing', () => {
    expect(summarise(new MethodLibrary())).toBe('no methods');
  });
});
