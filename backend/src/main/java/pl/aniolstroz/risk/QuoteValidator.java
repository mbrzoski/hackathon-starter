package pl.aniolstroz.risk;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pl.aniolstroz.contracts.QuoteNormalizer;
import pl.aniolstroz.contracts.StageHit;
import pl.aniolstroz.contracts.TranscriptSegment;

/**
 * AI-08: the AI may only quote, never invent. A hit is valid when the segment it cites exists and the normalized
 * quote (CON-05) is contained in the normalized text of that segment. Whatever the incoming {@code validated} flag
 * says is ignored; the result is the only truth. A quote of fewer than {@value #MIN_QUOTE_LENGTH} characters after
 * normalization is rejected, too. Invalid hits stay in the list with {@code validated = false}, so the
 * audit can show them, and they never influence the risk.
 */
public final class QuoteValidator {

    /** A shorter quote (after normalization) is too weak as evidence: one common word fits almost any segment. */
    static final int MIN_QUOTE_LENGTH = 3;

    private QuoteValidator() {
    }

    /** The same hits in the same order, each with {@code validated} set by this check. */
    public static List<StageHit> validate(List<StageHit> hits, List<TranscriptSegment> transcript) {
        Map<String, String> normalizedBySegment = new HashMap<>();
        for (TranscriptSegment segment : transcript) {
            normalizedBySegment.put(segment.segId(), QuoteNormalizer.normalize(segment.text()));
        }
        return hits.stream().map(hit -> withFlag(hit, isValid(hit, normalizedBySegment))).toList();
    }

    private static boolean isValid(StageHit hit, Map<String, String> normalizedBySegment) {
        String segmentText = normalizedBySegment.get(hit.segId());
        if (segmentText == null || hit.quote() == null) {
            return false;
        }
        String quote = QuoteNormalizer.normalize(hit.quote());
        return quote.length() >= MIN_QUOTE_LENGTH && segmentText.contains(quote);
    }

    private static StageHit withFlag(StageHit hit, boolean validated) {
        return new StageHit(hit.stage(), hit.segId(), hit.quote(), hit.speakerRole(), hit.source(), validated);
    }
}
