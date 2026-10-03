/**
 * Quote normalisation (CON-05): lowercase, NFD without combining marks, l-stroke to l, every run of
 * characters that are not letters or digits becomes one space, trimmed. Must stay identical to the backend
 * QuoteNormalizer; both are tested against contracts/test-vectors/normalize.json.
 */
export function normalize(text: string): string {
  return (
    text
      .toLowerCase()
      .normalize('NFD')
      .replace(/\p{M}+/gu, '')
      // l-stroke has no NFD decomposition
      .replace(/ł/g, 'l')
      .replace(/[^\p{L}\p{N}]+/gu, ' ')
      .trim()
  );
}
