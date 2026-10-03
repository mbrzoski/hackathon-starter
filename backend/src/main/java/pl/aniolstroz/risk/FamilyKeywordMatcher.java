package pl.aniolstroz.risk;

import java.util.List;
import java.util.regex.Pattern;
import pl.aniolstroz.contracts.HitSource;
import pl.aniolstroz.contracts.QuoteNormalizer;
import pl.aniolstroz.contracts.SpeakerRole;
import pl.aniolstroz.contracts.StageHit;
import pl.aniolstroz.contracts.StageId;
import pl.aniolstroz.contracts.TranscriptSegment;

/**
 * The words the family asked this profile to be sensitive to (SeniorConfig.keywords). A pure function of the segment
 * and the words: matched on the backend after {@link QuoteNormalizer} (lowercase, no Polish diacritics), so STT without
 * diacritics and inflected forms ("aktu własności") still match: every word may carry an ending. A single word of up to
 * three letters matches whole words only ("pin" is not "pinezka").
 * These words are settings and never go to Claude or STT (rule 5). A match is a {@link StageId#FAMILY_KEYWORD} hit
 * that quotes the original sentence.
 */
public final class FamilyKeywordMatcher {

    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?…])\\s+");
    private static final int SHORT_WORD = 3;

    private FamilyKeywordMatcher() {
    }

    /** One hit per segment at most, quoting the first sentence that contains any of the words. */
    public static List<StageHit> detect(TranscriptSegment segment, List<String> keywords) {
        if (keywords.isEmpty()) {
            return List.of();
        }
        List<Pattern> patterns = keywords.stream().map(FamilyKeywordMatcher::pattern).toList();
        for (String raw : SENTENCE_END.split(segment.text())) {
            String sentence = raw.strip();
            if (sentence.isEmpty()) {
                continue;
            }
            String normalized = QuoteNormalizer.normalize(sentence);
            if (patterns.stream().anyMatch(p -> p.matcher(normalized).find())) {
                return List.of(new StageHit(StageId.FAMILY_KEYWORD, segment.segId(), sentence, SpeakerRole.UNCLEAR,
                        HitSource.KEYWORDS, true));
            }
        }
        return List.of();
    }

    private static Pattern pattern(String keyword) {
        String normalized = QuoteNormalizer.normalize(keyword);
        String[] words = normalized.split(" ");
        if (words.length == 1 && normalized.length() <= SHORT_WORD) {
            return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(normalized) + "(?![a-z0-9])");
        }
        // Every word may carry an ending: "akt własności" also finds "aktu własności", "dowód" finds "dowodu".
        StringBuilder regex = new StringBuilder();
        for (String word : words) {
            if (regex.length() > 0) {
                regex.append(" ");
            }
            regex.append("(?<![a-z0-9])").append(Pattern.quote(word)).append("[a-z0-9]*");
        }
        return Pattern.compile(regex.toString());
    }
}
