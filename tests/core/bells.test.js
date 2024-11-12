import { describe, it, expect } from 'vitest';
import {
  TREBLE,
  bellSymbol,
  bellFromSymbol,
  isBellSymbol,
  symbolsForStage,
  bellsFromSymbols,
  symbolsFromBells,
  tenorOf,
} from '../../src/core/bells.js';
import { MAX_STAGE, supportedStages } from '../../src/core/stage.js';

describe('bellSymbol', () => {
  it('writes the single digit bells as digits', () => {
    expect(bellSymbol(1)).toBe('1');
    expect(bellSymbol(9)).toBe('9');
  });

  it('writes the tenth bell as zero', () => {
    expect(bellSymbol(10)).toBe('0');
  });

  it('writes the eleventh and twelfth as E and T', () => {
    expect(bellSymbol(11)).toBe('E');
    expect(bellSymbol(12)).toBe('T');
  });

  it('carries on with letters above twelve', () => {
    expect(bellSymbol(13)).toBe('A');
    expect(bellSymbol(16)).toBe('D');
  });

  it('refuses a bell outside the supported range', () => {
    expect(() => bellSymbol(0)).toThrow(RangeError);
    expect(() => bellSymbol(MAX_STAGE + 1)).toThrow(RangeError);
    expect(() => bellSymbol(2.5)).toThrow(RangeError);
  });
});

describe('bellFromSymbol', () => {
  it('round trips every bell', () => {
    for (let bell = 1; bell <= MAX_STAGE; bell += 1) {
      expect(bellFromSymbol(bellSymbol(bell))).toBe(bell);
    }
  });

  it('accepts lower case letters', () => {
    expect(bellFromSymbol('e')).toBe(11);
    expect(bellFromSymbol('t')).toBe(12);
    expect(bellFromSymbol('d')).toBe(16);
  });

  it('refuses a character that is not a bell', () => {
    expect(() => bellFromSymbol('x')).toThrow(/not a bell symbol/);
    expect(() => bellFromSymbol('-')).toThrow(RangeError);
  });
});

describe('isBellSymbol', () => {
  it('recognises the whole alphabet', () => {
    for (const character of '1234567890ETABCD') {
      expect(isBellSymbol(character)).toBe(true);
    }
  });

  it('rejects the place notation cross symbol', () => {
    expect(isBellSymbol('x')).toBe(false);
    expect(isBellSymbol('X')).toBe(false);
    expect(isBellSymbol('.')).toBe(false);
  });
});

describe('symbolsForStage', () => {
  it('gives one symbol per bell', () => {
    for (const stage of supportedStages()) {
      expect(symbolsForStage(stage)).toHaveLength(stage);
    }
  });

  it('gives rounds on twelve', () => {
    expect(symbolsForStage(12)).toBe('1234567890ET');
  });

  it('refuses an unwritable stage', () => {
    expect(() => symbolsForStage(MAX_STAGE + 1)).toThrow(RangeError);
  });
});

describe('bell sequences', () => {
  it('reads a run of symbols', () => {
    expect(bellsFromSymbols('1357')).toEqual([1, 3, 5, 7]);
    expect(bellsFromSymbols('0ET')).toEqual([10, 11, 12]);
  });

  it('writes a run of bells', () => {
    expect(symbolsFromBells([1, 3, 5, 7])).toBe('1357');
    expect(symbolsFromBells([10, 11, 12])).toBe('0ET');
  });

  it('round trips a whole stage', () => {
    const rounds = symbolsForStage(16);
    expect(symbolsFromBells(bellsFromSymbols(rounds))).toBe(rounds);
  });
});

describe('named bells', () => {
  it('calls bell one the treble', () => {
    expect(TREBLE).toBe(1);
  });

  it('calls the highest numbered bell the tenor', () => {
    expect(tenorOf(8)).toBe(8);
    expect(tenorOf(12)).toBe(12);
  });
});
