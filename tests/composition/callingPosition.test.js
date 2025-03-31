import { describe, it, expect } from 'vitest';
import {
  positionPlaces,
  placeCodes,
  positionAt,
  placeForPosition,
  positionName,
  namedPositions,
  positionOfCall,
} from '../../src/composition/callingPosition.js';
import { parseRow } from '../../src/core/rowParse.js';
import { ValidationError } from '../../src/errors.js';

describe('positionPlaces', () => {
  it('places home at the back and wrong two under it', () => {
    const places = positionPlaces(8);
    expect(places.get('H')).toBe(8);
    expect(places.get('W')).toBe(6);
    expect(places.get('M')).toBe(4);
  });

  it('puts the front three positions at the front', () => {
    const places = positionPlaces(8);
    expect(places.get('B')).toBe(1);
    expect(places.get('I')).toBe(2);
    expect(places.get('O')).toBe(3);
  });

  it('spreads out at royal', () => {
    const places = positionPlaces(10);
    expect(places.get('H')).toBe(10);
    expect(places.get('W')).toBe(8);
    expect(places.get('M')).toBe(6);
    expect(places.get('V')).toBe(5);
    expect(places.get('F')).toBe(4);
  });

  it('leaves out positions that fall off the front at minor', () => {
    const places = positionPlaces(6);
    expect(places.get('M')).toBe(2);
    expect(places.has('V')).toBe(true);
  });

  it('refuses an unsupported stage', () => {
    expect(() => positionPlaces(2)).toThrow(ValidationError);
  });
});

describe('placeCodes', () => {
  it('resolves the collision at major in favour of the middle', () => {
    expect(placeCodes(8).get(4)).toBe('M');
  });

  it('keeps fourths and middle apart at royal', () => {
    expect(placeCodes(10).get(4)).toBe('F');
    expect(placeCodes(10).get(6)).toBe('M');
  });
});

describe('positionAt', () => {
  it('names the places that have names', () => {
    expect(positionAt(8, 8)).toBe('H');
    expect(positionAt(8, 6)).toBe('W');
    expect(positionAt(8, 1)).toBe('B');
  });

  it('gives the place as a number when it has no name', () => {
    expect(positionAt(8, 7)).toBe('7');
    expect(positionAt(10, 9)).toBe('9');
  });

  it('refuses a place outside the stage', () => {
    expect(() => positionAt(8, 9)).toThrow(/place 9 is outside 1\.\.8/);
    expect(() => positionAt(8, 0)).toThrow(ValidationError);
  });
});

describe('placeForPosition', () => {
  it('reads a named position back', () => {
    expect(placeForPosition(8, 'H')).toBe(8);
    expect(placeForPosition(8, 'M')).toBe(4);
  });

  it('reads a place written as a number', () => {
    expect(placeForPosition(8, '7')).toBe(7);
  });

  it('refuses a code that is not a position', () => {
    expect(() => placeForPosition(8, 'Z')).toThrow(/is not a calling position on 8/);
    expect(() => placeForPosition(8, '9')).toThrow(ValidationError);
  });

  it('round trips every named place', () => {
    for (const [code, place] of positionPlaces(10)) {
      expect(placeForPosition(10, code)).toBe(place);
    }
  });
});

describe('positionName', () => {
  it('spells the codes out', () => {
    expect(positionName('H')).toBe('Home');
    expect(positionName('W')).toBe('Wrong');
    expect(positionName('B')).toBe('Before');
  });

  it('leaves an unknown code alone', () => {
    expect(positionName('7')).toBe('7');
  });
});

describe('namedPositions', () => {
  it('lists them from the front to the back', () => {
    expect(namedPositions(8).map((entry) => entry.code)).toEqual([
      'B',
      'I',
      'O',
      'M',
      'V',
      'W',
      'H',
    ]);
  });

  it('gives the long name with each', () => {
    expect(namedPositions(8)[0]).toEqual({ code: 'B', place: 1, name: 'Before' });
  });
});

describe('positionOfCall', () => {
  it('names the position from where the observation bell ended up', () => {
    expect(positionOfCall(parseRow('13527468'), 8)).toBe('H');
    expect(positionOfCall(parseRow('12345678'), 8)).toBe('H');
  });

  it('finds the tenor in the middle', () => {
    expect(positionOfCall(parseRow('12384567'), 8)).toBe('M');
    expect(positionOfCall(parseRow('13527486'), 8)).toBe('7');
  });

  it('refuses a bell that is not ringing', () => {
    expect(() => positionOfCall(parseRow('123456'), 8)).toThrow(/not in a row of 6/);
  });
});
