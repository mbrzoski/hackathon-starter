package pl.aniolstroz.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.aniolstroz.contracts.HitSource;
import pl.aniolstroz.contracts.StageHit;
import pl.aniolstroz.contracts.StageId;
import pl.aniolstroz.contracts.TranscriptSegment;

class FamilyKeywordMatcherTest {

    private static TranscriptSegment segment(String text) {
        return new TranscriptSegment("c1", "s1", 0, 1000, text, true, pl.aniolstroz.contracts.SpeakerLabel.UNKNOWN, null);
    }

    @Test
    void matchesAWordWithoutDiacriticsCaseAndInflection() {
        List<StageHit> hits = FamilyKeywordMatcher.detect(segment("Proszę przysłać skan Aktu Własności mieszkania."),
                List.of("akt własności", "dowód"));

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).stage()).isEqualTo(StageId.FAMILY_KEYWORD);
        assertThat(hits.get(0).source()).isEqualTo(HitSource.KEYWORDS);
        assertThat(hits.get(0).validated()).isTrue();
        assertThat(hits.get(0).quote()).isEqualTo("Proszę przysłać skan Aktu Własności mieszkania.");
    }

    @Test
    void sttWithoutDiacriticsStillMatches() {
        assertThat(FamilyKeywordMatcher.detect(segment("wyslij akt wlasnosci"), List.of("akt własności"))).hasSize(1);
    }

    @Test
    void shortWordsMatchWholeWordsOnly() {
        assertThat(FamilyKeywordMatcher.detect(segment("Mam dużą pensję."), List.of("pin"))).isEmpty();
        assertThat(FamilyKeywordMatcher.detect(segment("Podaj PIN do karty."), List.of("pin"))).hasSize(1);
    }

    @Test
    void quotesTheFirstSentenceThatMatches() {
        List<StageHit> hits = FamilyKeywordMatcher.detect(segment("Dzień dobry. Potrzebny jest testament. Pilne."),
                List.of("testament"));
        assertThat(hits.get(0).quote()).isEqualTo("Potrzebny jest testament.");
    }

    @Test
    void nothingToMatchGivesNothing() {
        assertThat(FamilyKeywordMatcher.detect(segment("Dzień dobry babciu."), List.of())).isEmpty();
        assertThat(FamilyKeywordMatcher.detect(segment("Dzień dobry babciu."), List.of("testament"))).isEmpty();
    }
}
