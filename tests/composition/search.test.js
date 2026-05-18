import { describe, it, expect } from 'vitest';
import { searchTouches, shortest, MAX_DEPTH } from '../../src/composition/search.js';
import { expandTouch } from '../../src/composition/touchExpander.js';
import { parseTouch } from '../../src/composition/touchParser.js';
import { prove } from '../../src/composition/prover.js';
import { method } from '../../src/method/method.js';
import { ValidationError } from '../../src/errors.js';

const plainBobMinor = () => method('Plain', 6, 'x1x1x1,2');
const plainBobMajor = () => method('Plain', 8, 'x18x18x18x18,12');

describe('searchTouches', () => {
  it('finds touches of plain bob minor', () => {
    const found = searchTouches({ method: plainBobMinor(), maxLeads: 8, limit: 8 });
    expect(found.length).toBeGreaterThan(0);
  });

  it('finds the plain course first, since it is the shortest', () => {
    const found = searchTouches({ method: plainBobMinor(), maxLeads: 8, limit: 8 });
    expect(shortest(found).calling).toBe('.....');
    expect(shortest(found).changes).toBe(60);
  });

  it('reports the length in changes and in leads', () => {
    const found = searchTouches({ method: plainBobMinor(), maxLeads: 8, limit: 4 });
    for (const touch of found) {
      expect(touch.changes).toBe(touch.leads * 12);
    }
  });

  it('only reports touches that come round and are true', () => {
    const bob = plainBobMinor();
    const found = searchTouches({ method: bob, maxLeads: 8, limit: 8 });
    for (const touch of found) {
      const rung = expandTouch({ method: bob, touch: parseTouch(touch.calling) });
      expect(rung.comesRound).toBe(true);
      expect(prove(rung.block).isTrue).toBe(true);
    }
  });

  it('stops at the number of touches it was asked for', () => {
    expect(searchTouches({ method: plainBobMinor(), maxLeads: 8, limit: 3 })).toHaveLength(3);
  });

  it('keeps only the lengths it was asked for', () => {
    const found = searchTouches({
      method: plainBobMinor(),
      maxLeads: 8,
      limit: 10,
      lengths: [72],
    });
    expect(found.length).toBeGreaterThan(0);
    for (const touch of found) {
      expect(touch.changes).toBe(72);
    }
  });

  it('finds nothing when the search is too shallow', () => {
    expect(searchTouches({ method: plainBobMajor(), maxLeads: 3, limit: 5 })).toEqual([]);
  });

  it('carries the notation with each touch', () => {
    const found = searchTouches({ method: plainBobMinor(), maxLeads: 6, limit: 1 });
    expect(found[0].notation).toBe('x16x16x16x16x16x12');
  });

  it('refuses a search it cannot run', () => {
    expect(() => searchTouches({ method: 'Plain Bob Minor' })).toThrow(ValidationError);
    expect(() => searchTouches({ method: plainBobMinor(), maxLeads: 0 })).toThrow(
      /outside 1\.\.60/,
    );
    expect(() => searchTouches({ method: plainBobMinor(), maxLeads: MAX_DEPTH + 1 })).toThrow(
      ValidationError,
    );
    expect(() => searchTouches({ method: plainBobMinor(), limit: 0 })).toThrow(/is not a search/);
  });
});

describe('shortest', () => {
  it('finds the shortest of several', () => {
    const found = [
      { calling: 'a', changes: 120 },
      { calling: 'b', changes: 60 },
      { calling: 'c', changes: 180 },
    ];
    expect(shortest(found).calling).toBe('b');
  });

  it('gives null when nothing was found', () => {
    expect(shortest([])).toBeNull();
  });
});
