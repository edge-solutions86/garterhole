import { describe, it, expect } from 'vitest';
import {
  leadHeadSignature,
  leadEndPlaces,
  leadHeadOrder,
  sharesLeadHead,
  groupByLeadHead,
  spliceableWith,
  spliceableEveryLead,
  describeLeadHead,
} from '../../src/method/leadHeads.js';
import { method } from '../../src/method/method.js';

const cambridge = method('Cambridge Surprise', 8, 'x38x14x1258x36x14x58x16x78,12');
const yorkshire = method('Yorkshire Surprise', 8, 'x38x14x58x16x12x38x14x78,12');
const bristol = method('Bristol Surprise', 8, 'x58x14.58x58.36.14x14.58x14x18,18');
const plainBobMajor = method('Plain Bob', 8, 'x18x18x18x18,12');
const plainBobMinor = method('Plain Bob', 6, 'x1x1x1,2');

describe('leadHeadSignature', () => {
  it('includes the stage so two stages never collide', () => {
    expect(leadHeadSignature(plainBobMajor)).toBe('8:13527486');
    expect(leadHeadSignature(plainBobMinor)).toBe('6:135264');
  });

  it('refuses something that is not a method', () => {
    expect(() => leadHeadSignature('Plain Bob')).toThrow(/a Method is needed/);
  });
});

describe('leadEndPlaces', () => {
  it('reads the places made at the lead end', () => {
    expect(leadEndPlaces(cambridge)).toEqual([1, 2]);
    expect(leadEndPlaces(bristol)).toEqual([1, 8]);
  });

  it('hands back a copy', () => {
    const places = leadEndPlaces(cambridge);
    places.push(9);
    expect(leadEndPlaces(cambridge)).toEqual([1, 2]);
  });
});

describe('leadHeadOrder', () => {
  it('counts the leads in a plain course', () => {
    expect(leadHeadOrder(cambridge)).toBe(7);
    expect(leadHeadOrder(plainBobMinor)).toBe(5);
  });
});

describe('sharesLeadHead', () => {
  it('finds two surprise major methods share a lead head', () => {
    expect(sharesLeadHead(cambridge, yorkshire)).toBe(true);
  });

  it('separates methods whose leads are different lengths', () => {
    expect(sharesLeadHead(cambridge, plainBobMajor)).toBe(false);
  });

  it('separates a seconds place method from an eighths place one', () => {
    expect(sharesLeadHead(cambridge, bristol)).toBe(false);
  });

  it('separates two stages', () => {
    expect(sharesLeadHead(plainBobMajor, plainBobMinor)).toBe(false);
  });
});

describe('groupByLeadHead', () => {
  it('collects the methods that finish on the same row', () => {
    const groups = groupByLeadHead([cambridge, yorkshire, bristol, plainBobMajor, plainBobMinor]);
    expect(groups.size).toBe(4);
    expect(groups.get('8:15738264')).toHaveLength(2);
    expect(groups.get('8:14263857')).toHaveLength(1);
    expect(groups.get('8:13527486')).toHaveLength(1);
  });

  it('gives an empty map for no methods', () => {
    expect(groupByLeadHead([]).size).toBe(0);
  });
});

describe('spliceableWith', () => {
  const all = [cambridge, yorkshire, bristol, plainBobMajor];

  it('lists the methods that share the lead head', () => {
    expect(spliceableWith(cambridge, all).map((m) => m.name)).toEqual(['Yorkshire Surprise']);
  });

  it('leaves the method itself out', () => {
    expect(spliceableWith(cambridge, all)).not.toContain(cambridge);
  });

  it('gives nothing when nothing matches', () => {
    expect(spliceableWith(bristol, all)).toEqual([]);
  });
});

describe('spliceableEveryLead', () => {
  it('needs the lead lengths to match as well', () => {
    expect(spliceableEveryLead(cambridge, yorkshire)).toBe(true);
    expect(spliceableEveryLead(cambridge, plainBobMajor)).toBe(false);
  });

  it('is false whenever the lead heads differ', () => {
    expect(spliceableEveryLead(cambridge, bristol)).toBe(false);
  });
});

describe('describeLeadHead', () => {
  it('reports everything a splicing table needs', () => {
    expect(describeLeadHead(cambridge)).toEqual({
      leadHead: '15738264',
      leads: 7,
      leadEnd: '12',
      leadLength: 32,
    });
  });

  it('reports an eighths place method', () => {
    expect(describeLeadHead(bristol).leadEnd).toBe('18');
  });
});
