import { describe, it, expect } from 'vitest';
import {
  stage,
  row,
  placeNotation,
  methodTitle,
  touch,
  isoDate,
  changeCount,
} from '../../src/validation/types.js';
import { object, check } from '../../src/validation/schema.js';

const messages = (schema, value) => schema(value, '').map((problem) => problem.message);

describe('stage', () => {
  it('accepts a stage this library works with', () => {
    expect(messages(stage(), 8)).toEqual([]);
    expect(messages(stage(), 3)).toEqual([]);
  });

  it('refuses one outside the range', () => {
    expect(messages(stage(), 2)[0]).toContain('at least 3');
    expect(messages(stage(), 17)[0]).toContain('at most 16');
  });

  it('refuses something that is not a whole number', () => {
    expect(messages(stage(), 6.5)).toEqual(['must be a whole number']);
    expect(messages(stage(), '8')).toEqual(['must be a number']);
  });
});

describe('row', () => {
  it('accepts a row', () => {
    expect(messages(row(), '13527486')).toEqual([]);
  });

  it('refuses one that is not a permutation', () => {
    expect(messages(row(), '1123')[0]).toContain('appears more than once');
  });

  it('checks the stage when one is given', () => {
    expect(messages(row({ stage: 8 }), '123456')[0]).toContain('but the stage is 8');
  });

  it('refuses something that is not text', () => {
    expect(messages(row(), 12345678)).toEqual(['must be a string']);
    expect(messages(row(), '')).toEqual(['must be at least 1 characters']);
  });
});

describe('placeNotation', () => {
  it('accepts notation that expands', () => {
    expect(messages(placeNotation({ stage: 6 }), 'x1x1x1,2')).toEqual([]);
  });

  it('refuses notation with a bad symbol', () => {
    expect(messages(placeNotation({ stage: 6 }), 'x1?x1')[0]).toContain(
      'not part of place notation',
    );
  });

  it('refuses places that cannot make a change', () => {
    expect(messages(placeNotation({ stage: 8 }), 'x13x')[0]).toContain('would leave');
  });

  it('refuses something that is not text', () => {
    expect(messages(placeNotation({ stage: 6 }), null)).toEqual(['must be a string']);
  });
});

describe('methodTitle', () => {
  it('accepts a title', () => {
    expect(messages(methodTitle(), 'Cambridge Surprise Major')).toEqual([]);
    expect(messages(methodTitle(), 'Stedman Triples')).toEqual([]);
  });

  it('refuses one with no stage on the end', () => {
    expect(messages(methodTitle(), 'Cambridge Surprise')).toEqual(['is not a method title']);
  });
});

describe('touch', () => {
  it('accepts both writing styles', () => {
    expect(messages(touch(), '3(H)')).toEqual([]);
    expect(messages(touch(), '-.-.s-')).toEqual([]);
  });

  it('refuses one it cannot read', () => {
    expect(messages(touch(), 'W)')[0]).toContain('without opening');
  });
});

describe('isoDate', () => {
  it('accepts a date a form sends', () => {
    expect(messages(isoDate(), '2025-05-18')).toEqual([]);
  });

  it('refuses one written any other way', () => {
    expect(messages(isoDate(), '18/05/2025')).toEqual(['must be a date like 2025-05-18']);
    expect(messages(isoDate(), 20250518)).toEqual(['must be a date like 2025-05-18']);
  });

  it('refuses a day that does not exist', () => {
    expect(messages(isoDate(), '2025-02-30')).toEqual(['is not a real date']);
    expect(messages(isoDate(), '2025-13-01')).toEqual(['is not a real date']);
  });
});

describe('changeCount', () => {
  it('accepts a length', () => {
    expect(messages(changeCount(), 5040)).toEqual([]);
  });

  it('refuses nothing and refuses nonsense', () => {
    expect(messages(changeCount(), 0)).toEqual(['must be at least 1']);
    expect(messages(changeCount(), 1000000)).toEqual(['must be at most 100000']);
  });
});

describe('the types together', () => {
  const request = object({
    title: methodTitle(),
    touch: touch(),
    stage: stage(),
  });

  it('accepts a sound request', () => {
    expect(() =>
      check(request, { title: 'Plain Bob Major', touch: '3(H)', stage: 8 }),
    ).not.toThrow();
  });

  it('reports every field that is wrong at once', () => {
    const problems = request({ title: 'nope', touch: 'W)', stage: 2 }, '');
    expect(problems).toHaveLength(3);
    expect(problems.map((entry) => entry.path)).toEqual(['title', 'touch', 'stage']);
  });
});
