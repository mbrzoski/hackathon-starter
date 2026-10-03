import vectors from '../../../../contracts/test-vectors/normalize.json';
import { normalize } from './normalize';
import { findQuote } from './quote-match';

const cut = (text: string, quote: string) => {
  const r = findQuote(text, quote);
  return r ? text.slice(r.start, r.end) : null;
};

describe('findQuote', () => {
  const segment = 'Dzień dobry. Proszę nikomu o tym nie mówić, nawet rodzinie!';

  it('finds a quote with Polish characters where it was said', () => {
    expect(cut(segment, 'Proszę nikomu o tym nie mówić')).toBe('Proszę nikomu o tym nie mówić');
  });

  it('finds a quote written without Polish characters, punctuation or case', () => {
    expect(cut(segment, 'prosze NIKOMU o tym, nie mowic')).toBe('Proszę nikomu o tym nie mówić');
    expect(cut('Musi pani wypłacić gotówkę.', 'wyplacic gotowke')).toBe('wypłacić gotówkę');
    expect(cut('Łukasz z ŁODZI dzwonił', 'lukasz z lodzi')).toBe('Łukasz z ŁODZI');
  });

  it('handles decomposed characters and non-breaking spaces', () => {
    expect(cut('Mówi kod BLIK', 'mowi kod blik')).toBe('Mówi kod BLIK');
  });

  it('returns null when the quote is not in the segment', () => {
    expect(findQuote(segment, 'wypłać pieniądze')).toBeNull();
    expect(findQuote(segment, '...')).toBeNull();
  });

  it('matches every shared CON-05 vector against itself', () => {
    for (const { input } of vectors.vectors) {
      if (!normalize(input)) continue;
      const r = findQuote(input, input);
      expect(r, input).not.toBeNull();
      expect(normalize(input.slice(r!.start, r!.end))).toBe(normalize(input));
    }
  });
});
