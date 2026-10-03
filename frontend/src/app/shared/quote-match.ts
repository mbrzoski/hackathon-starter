import { normalize } from './normalize';

export interface QuoteRange {
  start: number;
  end: number;
}

/**
 * Where a quote was said inside a segment, as a range of the original text. Matching uses normalize()
 * (CON-05), so "prosze nikomu nie mowic" finds "Proszę nikomu nie mówić," exactly where it is.
 */
export function findQuote(text: string, quote: string): QuoteRange | null {
  const needle = normalize(quote);
  if (!needle) {
    return null;
  }
  // Build the normalised text character by character, remembering where each character came from.
  let haystack = '';
  const origin: number[] = [];
  for (let i = 0; i < text.length; i++) {
    const piece = normalize(text[i]);
    if (piece) {
      for (const ch of piece) {
        haystack += ch;
        origin.push(i);
      }
    } else if (!/\p{M}/u.test(text[i]) && haystack.length && !haystack.endsWith(' ')) {
      // Any run of non-letters becomes one space, as in normalize(); combining marks just vanish.
      haystack += ' ';
      origin.push(i);
    }
  }
  const at = haystack.indexOf(needle);
  if (at < 0) {
    return null;
  }
  return { start: origin[at], end: origin[at + needle.length - 1] + 1 };
}
