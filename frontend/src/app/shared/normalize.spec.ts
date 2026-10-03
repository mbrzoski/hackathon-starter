import vectors from '../../../../contracts/test-vectors/normalize.json';
import { normalize } from './normalize';

describe('normalize (CON-05 shared vectors)', () => {
  it('has vectors to check', () => {
    expect(vectors.vectors.length).toBeGreaterThan(0);
  });

  for (const { input, expected } of vectors.vectors) {
    it(`normalizes ${JSON.stringify(input)}`, () => {
      expect(normalize(input)).toBe(expected);
    });
  }
});
