package pl.aniolstroz.call;

import java.util.List;
import org.springframework.stereotype.Component;
import pl.aniolstroz.contracts.StageHit;
import pl.aniolstroz.contracts.TranscriptSegment;
import pl.aniolstroz.risk.KeywordDetector;

/**
 * What is found in a segment without asking the model, at once and also in interim text (DET-03): the built-in
 * dictionary, only when {@link KeywordBaseline} says so.
 */
@FunctionalInterface
public interface SegmentScanner {

    List<StageHit> scan(TranscriptSegment segment);

    @Component
    class Default implements SegmentScanner {

        private final KeywordDetector dictionary;
        private final KeywordBaseline baseline;

        Default(KeywordDetector dictionary, KeywordBaseline baseline) {
            this.dictionary = dictionary;
            this.baseline = baseline;
        }

        @Override
        public List<StageHit> scan(TranscriptSegment segment) {
            return baseline.active() ? dictionary.detect(segment) : List.of();
        }
    }
}
