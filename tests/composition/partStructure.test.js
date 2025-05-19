import { describe, it, expect } from 'vitest';
import {
  partHeads,
  partCount,
  observationBells,
  describePartEnd,
  findPartEnd,
  detectParts,
  isPartEnd,
  inPart,
  cyclicPartHeads,
} from '../../src/composition/partStructure.js';
import { parseRow } from '../../src/core/rowParse.js';
import { rounds } from '../../src/core/row.js';
import { method } from '../../src/method/method.js';
import { expandTouch } from '../../src/composition/touchExpander.js';
import { parseTouch } from '../../src/composition/touchParser.js';
import { ValidationError } from '../../src/errors.js';

const plainBobMajor = () => method('Plain', 8, 'x18x18x18x18,12');

describe('partHeads', () => {
  it('starts at rounds', () => {
    expect(partHeads(parseRow('13527486'))[0].isRounds()).toBe(true);
  });

  it('has one head per part', () => {
    const partEnd = parseRow('13527486');
    expect(partHeads(partEnd)).toHaveLength(partCount(partEnd));
  });

  it('gives just rounds for a part end that is rounds', () => {
    expect(partHeads(rounds(8))).toHaveLength(1);
  });
});

describe('partCount', () => {
  it('counts a seven part composition', () => {
    expect(partCount(parseRow('13527486'))).toBe(7);
  });

  it('counts a three part composition', () => {
    expect(partCount(parseRow('14235678'))).toBe(3);
  });
});

describe('observationBells', () => {
  it('finds the bells the part end leaves alone', () => {
    expect(observationBells(parseRow('14235678'))).toEqual([1, 5, 6, 7, 8]);
  });

  it('finds only the treble in a plain bob part end', () => {
    expect(observationBells(parseRow('13527486'))).toEqual([1]);
  });
});

describe('describePartEnd', () => {
  it('reports the parts, the shape and the fixed bells', () => {
    const described = describePartEnd(parseRow('13527486'));
    expect(described.parts).toBe(7);
    expect(described.shape).toBe('7.1');
    expect(described.fixed).toEqual([1]);
    expect(described.heads[0]).toBe('12345678');
  });
});

describe('findPartEnd', () => {
  const rung = () => expandTouch({ method: plainBobMajor(), touch: parseTouch('3(H)') });

  it('finds a three part structure', () => {
    const heads = rung().leads.map((lead) => lead.leadHead);
    const found = findPartEnd(heads, 3);
    expect(found.leadsPerPart).toBe(7);
    expect(found.partEnd.toString()).toBe('14235678');
  });

  it('gives null when the leads do not divide evenly', () => {
    const heads = rung().leads.map((lead) => lead.leadHead);
    expect(findPartEnd(heads, 2)).toBeNull();
  });

  it('gives null for no leads', () => {
    expect(findPartEnd([], 3)).toBeNull();
  });

  it('refuses a part count below two', () => {
    expect(() => findPartEnd([], 1)).toThrow(/cannot be in 1 parts/);
    expect(() => findPartEnd([], 1.5)).toThrow(ValidationError);
  });
});

describe('detectParts', () => {
  it('finds the structure of a three part touch', () => {
    const rung = expandTouch({ method: plainBobMajor(), touch: parseTouch('3(H)') });
    const found = detectParts(rung.leads.map((lead) => lead.leadHead));
    expect(found.parts).toBe(3);
    expect(found.leadsPerPart).toBe(7);
  });

  it('reads a plain course as one lead rung over and over', () => {
    const rung = expandTouch({ method: plainBobMajor(), touch: parseTouch('.......') });
    const found = detectParts(rung.leads.map((lead) => lead.leadHead));
    expect(found.parts).toBe(7);
    expect(found.leadsPerPart).toBe(1);
  });

  it('gives null when the calling has no repeating structure', () => {
    const rung = expandTouch({ method: plainBobMajor(), touch: parseTouch('-...') });
    expect(detectParts(rung.leads.map((lead) => lead.leadHead))).toBeNull();
  });

  it('gives null for nothing', () => {
    expect(detectParts([])).toBeNull();
  });
});

describe('isPartEnd', () => {
  it('rejects rounds', () => {
    expect(isPartEnd(rounds(8))).toBe(false);
  });

  it('accepts a row that comes back to rounds in several parts', () => {
    expect(isPartEnd(parseRow('14235678'))).toBe(true);
  });
});

describe('inPart', () => {
  it('takes a row into a later part', () => {
    const partHead = parseRow('14235678');
    expect(inPart(partHead, parseRow('12345678')).toString()).toBe('14235678');
  });

  it('leaves a row alone in the first part', () => {
    expect(inPart(rounds(8), parseRow('13527486')).toString()).toBe('13527486');
  });
});

describe('cyclicPartHeads', () => {
  it('rotates the back bells', () => {
    expect(cyclicPartHeads(8, 3).map(String)).toEqual(['12345678', '12345786', '12345867']);
  });

  it('has one head per part', () => {
    expect(cyclicPartHeads(8, 5)).toHaveLength(5);
  });

  it('refuses a part count that does not fit the stage', () => {
    expect(() => cyclicPartHeads(8, 9)).toThrow(/no cyclic part end for 9 parts on 8/);
    expect(() => cyclicPartHeads(8, 1)).toThrow(ValidationError);
  });
});
