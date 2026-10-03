package pl.aniolstroz.call;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import pl.aniolstroz.contracts.StageHit;
import pl.aniolstroz.contracts.TranscriptSegment;
import pl.aniolstroz.risk.FamilyKeywordMatcher;
import pl.aniolstroz.risk.KeywordDetector;
import pl.aniolstroz.settings.SeniorConfigService;

/**
 * What is found in a segment without asking the model, at once and also in interim text (DET-03): the family's own
 * words (always) and the built-in dictionary (only when {@link KeywordBaseline} says so).
 */
@FunctionalInterface
public interface SegmentScanner {

    List<StageHit> scan(TranscriptSegment segment);

    @Component
    class Default implements SegmentScanner {

        private final KeywordDetector dictionary;
        private final KeywordBaseline baseline;
        private final SeniorConfigService config;

        Default(KeywordDetector dictionary, KeywordBaseline baseline, SeniorConfigService config) {
            this.dictionary = dictionary;
            this.baseline = baseline;
            this.config = config;
        }

        @Override
        public List<StageHit> scan(TranscriptSegment segment) {
            List<StageHit> hits = new ArrayList<>();
            if (baseline.active()) {
                hits.addAll(dictionary.detect(segment));
            }
            try {
                hits.addAll(FamilyKeywordMatcher.detect(segment, config.keywords()));
            } catch (RuntimeException e) {
                // The family's words are a plus: a database hiccup must not stop the call (type only, OBS-05).
                org.slf4j.LoggerFactory.getLogger(Default.class)
                        .warn("Reading the family's words failed: {}", e.getClass().getName());
            }
            return List.copyOf(hits);
        }
    }
}
