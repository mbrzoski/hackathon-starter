package pl.aniolstroz.alerts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.aniolstroz.contracts.SpeakerLabel;
import pl.aniolstroz.contracts.TranscriptSegment;

/** DAT-01: cited segments plus two segments of context on each side. */
class TranscriptExcerptTest {

    private static List<TranscriptSegment> transcript(int size) {
        List<TranscriptSegment> segments = new ArrayList<>();
        for (int i = 1; i <= size; i++) {
            segments.add(new TranscriptSegment("c", "s" + i, i * 10L, i * 10L, "tekst " + i, true,
                    SpeakerLabel.B, null));
        }
        return segments;
    }

    private static List<String> ids(List<TranscriptSegment> segments) {
        return segments.stream().map(TranscriptSegment::segId).toList();
    }

    @Test
    void keepsCitedSegmentAndTwoOnEachSide() {
        assertThat(ids(TranscriptExcerpt.select(transcript(10), Set.of("s5"), 2)))
                .containsExactly("s3", "s4", "s5", "s6", "s7");
    }

    @Test
    void clampsAtTheStartAndTheEnd() {
        assertThat(ids(TranscriptExcerpt.select(transcript(10), Set.of("s1"), 2))).containsExactly("s1", "s2", "s3");
        assertThat(ids(TranscriptExcerpt.select(transcript(10), Set.of("s10"), 2))).containsExactly("s8", "s9", "s10");
    }

    @Test
    void overlappingRangesAreMergedWithoutDuplicates() {
        assertThat(ids(TranscriptExcerpt.select(transcript(12), Set.of("s4", "s6"), 2)))
                .containsExactly("s2", "s3", "s4", "s5", "s6", "s7", "s8");
    }

    @Test
    void distantCitationsLeaveTheMiddleOut() {
        assertThat(ids(TranscriptExcerpt.select(transcript(20), Set.of("s3", "s15"), 2)))
                .containsExactly("s1", "s2", "s3", "s4", "s5", "s13", "s14", "s15", "s16", "s17");
    }

    @Test
    void resultKeepsTranscriptOrderWhateverTheOrderOfCitations() {
        assertThat(ids(TranscriptExcerpt.select(transcript(10), Set.of("s9", "s2"), 1)))
                .containsExactly("s1", "s2", "s3", "s8", "s9", "s10");
    }

    @Test
    void citationOfAnUnknownSegmentKeepsNothingForIt() {
        assertThat(TranscriptExcerpt.select(transcript(5), Set.of("s99"), 2)).isEmpty();
    }

    @Test
    void noCitationsKeepNothing() {
        assertThat(TranscriptExcerpt.select(transcript(5), Set.of(), 2)).isEmpty();
    }
}
