import { describe, it, expect } from 'vitest';
import {
  TruthTracker,
  prove,
  proveTouch,
  extentSize,
  isExtent,
  firstRepeat,
  describeProof,
} from '../../src/composition/prover.js';
import { expandTouch } from '../../src/composition/touchExpander.js';
import { parseTouch } from '../../src/composition/touchParser.js';
import { method } from '../../src/method/method.js';
import { RowBlock } from '../../src/core/rowBlock.js';
import { rounds } from '../../src/core/row.js';
import { parseRow } from '../../src/core/rowParse.js';
import { plainCourse } from '../../src/method/plainCourse.js';
import { ValidationError } from '../../src/errors.js';

const plainBobMinor = () => method('Plain', 6, 'x1x1x1,2');
const plainBobMajor = () => method('Plain', 8, 'x18x18x18x18,12');
const ring = (m, text) => expandTouch({ method: m, touch: parseTouch(text) });

describe('prove', () => {
  it('finds a plain course true', () => {
    const proof = prove(plainCourse(plainBobMinor()));
    expect(proof.isTrue).toBe(true);
    expect(proof.rows).toBe(60);
    expect(proof.distinct).toBe(60);
    expect(proof.repeats).toEqual([]);
  });

  it('does not count the row a touch comes round on', () => {
    const proof = prove(plainCourse(plainBobMinor()));
    expect(proof.comesRound).toBe(true);
    expect(proof.rows).toBe(60);
  });

  it('counts every row when the block does not come round', () => {
    const lead = plainBobMinor().plainLead();
    const proof = prove(lead);
    expect(proof.comesRound).toBe(false);
    expect(proof.rows).toBe(13);
  });

  it('finds a repeated row and says where', () => {
    const block = new RowBlock([rounds(4), parseRow('2143'), parseRow('2413'), parseRow('2143')]);
    const proof = prove(block);
    expect(proof.isTrue).toBe(false);
    expect(proof.repeats).toEqual([{ row: '2143', at: [1, 3] }]);
  });

  it('lists repeats in the order they first appear', () => {
    const block = new RowBlock([
      rounds(4),
      parseRow('2143'),
      rounds(4),
      parseRow('2143'),
      parseRow('2413'),
    ]);
    expect(prove(block).repeats.map((repeat) => repeat.row)).toEqual(['1234', '2143']);
  });

  it('finds a false touch false', () => {
    const rung = ring(plainBobMinor(), '3(-..-..)');
    expect(prove(rung.block).isTrue).toBe(false);
  });

  it('refuses something that is not a block', () => {
    expect(() => prove([rounds(6)])).toThrow(ValidationError);
  });
});

describe('proveTouch', () => {
  it('proves a rung touch', () => {
    const proof = proveTouch(ring(plainBobMajor(), '3(H)'));
    expect(proof.isTrue).toBe(true);
    expect(proof.rows).toBe(336);
  });

  it('refuses something that is not a rung touch', () => {
    expect(() => proveTouch({ block: 'rows' })).toThrow(/a rung touch is needed/);
    expect(() => proveTouch(null)).toThrow(ValidationError);
  });
});

describe('extentSize', () => {
  it('counts the rows at a stage', () => {
    expect(extentSize(4)).toBe(24);
    expect(extentSize(6)).toBe(720);
    expect(extentSize(8)).toBe(40320);
  });

  it('counts one row on one bell', () => {
    expect(extentSize(1)).toBe(1);
  });
});

describe('isExtent', () => {
  it('says a plain course is not an extent', () => {
    expect(isExtent(plainCourse(plainBobMinor()))).toBe(false);
  });

  it('says a false block is not an extent', () => {
    const block = new RowBlock([rounds(4), parseRow('2143'), parseRow('2143')]);
    expect(isExtent(block)).toBe(false);
  });

  it('measures an odd stage over the changing bells', () => {
    const doubles = plainCourse(method('Plain', 5, '5.1.5.1.5,125'));
    expect(isExtent(doubles)).toBe(false);
  });
});

describe('reporting a proof', () => {
  it('names the first repeat', () => {
    const block = new RowBlock([rounds(4), parseRow('2143'), parseRow('2143')]);
    const proof = prove(block);
    expect(firstRepeat(proof).row).toBe('2143');
    expect(describeProof(proof)).toBe('false: 2143 is rung 2 times, first at row 2');
  });

  it('gives null when there is nothing to report', () => {
    expect(firstRepeat(prove(plainCourse(plainBobMinor())))).toBeNull();
  });

  it('says a true touch is true', () => {
    expect(describeProof(prove(plainCourse(plainBobMinor())))).toBe('true, 60 rows');
  });

  it('mentions a true block that does not come round', () => {
    expect(describeProof(prove(plainBobMinor().plainLead()))).toBe(
      'true, 13 rows, but it does not come round',
    );
  });
});

describe('TruthTracker', () => {
  it('accepts a row it has not seen', () => {
    const tracker = new TruthTracker();
    expect(tracker.add(rounds(4))).toBe(true);
    expect(tracker.add(parseRow('2143'))).toBe(true);
    expect(tracker.isTrue()).toBe(true);
  });

  it('rejects a row it has seen before', () => {
    const tracker = new TruthTracker();
    tracker.add(rounds(4));
    expect(tracker.add(rounds(4))).toBe(false);
    expect(tracker.isTrue()).toBe(false);
  });

  it('counts every row offered and every distinct one', () => {
    const tracker = new TruthTracker();
    tracker.add(rounds(4));
    tracker.add(rounds(4));
    tracker.add(parseRow('2143'));
    expect(tracker.count).toBe(3);
    expect(tracker.distinct).toBe(2);
  });

  it('says where each repeat was', () => {
    const tracker = new TruthTracker();
    tracker.add(rounds(4));
    tracker.add(parseRow('2143'));
    tracker.add(rounds(4));
    expect(tracker.repeats()).toEqual([{ row: '1234', at: [0, 2] }]);
  });

  it('starts empty and true', () => {
    const tracker = new TruthTracker();
    expect(tracker.isTrue()).toBe(true);
    expect(tracker.distinct).toBe(0);
    expect(tracker.repeats()).toEqual([]);
  });
});
