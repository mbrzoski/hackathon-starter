package pl.aniolstroz.contracts;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Quote normalisation (CON-05): lowercase, NFD without combining marks, l-stroke to l, every run of
 * characters that are not letters or digits becomes one space, trimmed. Must stay identical to
 * {@code normalizeQuote} in contracts/ts/normalize.ts; both read contracts/test-vectors/normalize.json.
 */
public final class QuoteNormalizer {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{N}]+");

    private QuoteNormalizer() {
    }

    public static String normalize(String text) {
        String decomposed = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        String withoutMarks = COMBINING_MARKS.matcher(decomposed).replaceAll("");
        // l-stroke has no NFD decomposition
        String ascii = withoutMarks.replace('ł', 'l');
        return NOT_LETTER_OR_DIGIT.matcher(ascii).replaceAll(" ").trim();
    }
}
