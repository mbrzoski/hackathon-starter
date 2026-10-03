package pl.aniolstroz.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.aniolstroz.contracts.HitSource;
import pl.aniolstroz.contracts.SpeakerLabel;
import pl.aniolstroz.contracts.SpeakerRole;
import pl.aniolstroz.contracts.StageHit;
import pl.aniolstroz.contracts.StageId;
import pl.aniolstroz.contracts.TranscriptSegment;

class KeywordDetectorTest {

    private final KeywordDetector detector = KeywordDetector.bundled();

    private static TranscriptSegment segment(String text) {
        return new TranscriptSegment("call-1", "s7", 0, 0, text, true, SpeakerLabel.B, null);
    }

    private List<StageHit> detect(String text) {
        return detector.detect(segment(text));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', textBlock = """
            Mówi komisarz Kowalski.                          | AUTHORITY_CLAIM
            Jestem z policji.                                | AUTHORITY_CLAIM
            Dzwonię z prokuratury.                           | AUTHORITY_CLAIM
            Tu CBŚ, mamy sprawę.                             | AUTHORITY_CLAIM
            Dzwonię z działu bezpieczeństwa banku.           | AUTHORITY_CLAIM
            Nikomu nie mów o tej rozmowie.                   | SECRECY_DEMAND
            To jest tajemnica śledztwa.                      | SECRECY_DEMAND
            Nie informuj nikogo z rodziny.                   | SECRECY_DEMAND
            Proszę wypłacić gotówkę z konta.                 | MONEY_REQUEST
            Trzeba zrobić przelew.                           | MONEY_REQUEST
            Potrzebuję pieniędzy na kaucję.                  | MONEY_REQUEST
            Proszę podać kod BLIK.                           | PAYMENT_CHANNEL
            Po pieniądze przyjdzie kurier.                   | PAYMENT_CHANNEL
            Przelejemy na bezpieczne konto.                  | PAYMENT_CHANNEL
            Kupimy bitcoin w bankomacie.                     | PAYMENT_CHANNEL
            Proszę nie rozłączaj się.                        | ISOLATION
            Proszę zostać na linii.                          | ISOLATION
            Zainstaluj aplikację AnyDesk.                    | REMOTE_ACCESS
            Pobierz TeamViewer.                              | REMOTE_ACCESS
            Podaj mi numer PESEL.                            | PERSONAL_DATA_REQUEST
            Jaki jest numer karty?                           | PERSONAL_DATA_REQUEST
            Proszę podać PIN.                                | PERSONAL_DATA_REQUEST
            """)
    void detectsStagesWithPolishDiacritics(String text, StageId expected) {
        assertThat(detect(text.strip())).extracting(StageHit::stage).contains(expected);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', textBlock = """
            Mowi komisarz Kowalski.                          | AUTHORITY_CLAIM
            Jestem z policji.                                | AUTHORITY_CLAIM
            Tu CBS, mamy sprawe.                             | AUTHORITY_CLAIM
            Dzwonie z dzialu bezpieczenstwa banku.           | AUTHORITY_CLAIM
            Nikomu nie mow o tej rozmowie.                   | SECRECY_DEMAND
            To jest tajemnica sledztwa.                      | SECRECY_DEMAND
            Prosze wyplacic gotowke z konta.                 | MONEY_REQUEST
            Potrzebuje pieniedzy na kaucje.                  | MONEY_REQUEST
            Przelejemy na bezpieczne konto.                  | PAYMENT_CHANNEL
            Prosze zostac na linii.                          | ISOLATION
            Zainstaluj aplikacje AnyDesk.                    | REMOTE_ACCESS
            Podaj mi numer karty.                            | PERSONAL_DATA_REQUEST
            """)
    void detectsStagesWithoutPolishDiacritics(String text, StageId expected) {
        assertThat(detect(text.strip())).extracting(StageHit::stage).contains(expected);
    }

    @Test
    void matchingIsCaseInsensitive() {
        assertThat(detect("PROSZĘ PODAĆ KOD BLIK")).extracting(StageHit::stage).contains(StageId.PAYMENT_CHANNEL);
        assertThat(detect("prosze podac kod blik")).extracting(StageHit::stage).contains(StageId.PAYMENT_CHANNEL);
    }

    @Test
    void hitCarriesKeywordSourceValidatedUnclearSpeakerAndSegmentId() {
        StageHit hit = detect("Mówi policja.").get(0);

        assertThat(hit.stage()).isEqualTo(StageId.AUTHORITY_CLAIM);
        assertThat(hit.source()).isEqualTo(HitSource.KEYWORDS);
        assertThat(hit.validated()).isTrue();
        assertThat(hit.speakerRole()).isEqualTo(SpeakerRole.UNCLEAR);
        assertThat(hit.segId()).isEqualTo("s7");
    }

    @Test
    void quoteIsTheWholeOriginalSentenceWithDiacriticsNotTheNormalizedText() {
        String text = "Dzień dobry, tu Jan. Proszę wypłacić pieniądze z konta! Czekam na odpowiedź.";

        StageHit hit = detect(text).stream().filter(h -> h.stage() == StageId.MONEY_REQUEST).findFirst()
                .orElseThrow();

        assertThat(hit.quote()).isEqualTo("Proszę wypłacić pieniądze z konta!");
        assertThat(text).contains(hit.quote());
    }

    @Test
    void quoteIsTheSentenceWhereTheStageMatches() {
        List<StageHit> hits = detect("Mówi policja. Nikomu nie mów o tym.");

        assertThat(hits).extracting(StageHit::stage)
                .containsExactlyInAnyOrder(StageId.AUTHORITY_CLAIM, StageId.SECRECY_DEMAND);
        assertThat(hits).filteredOn(h -> h.stage() == StageId.AUTHORITY_CLAIM)
                .extracting(StageHit::quote).containsExactly("Mówi policja.");
        assertThat(hits).filteredOn(h -> h.stage() == StageId.SECRECY_DEMAND)
                .extracting(StageHit::quote).containsExactly("Nikomu nie mów o tym.");
    }

    @Test
    void oneHitPerStageAndSegmentUsingTheFirstMatchingSentence() {
        List<StageHit> hits = detect("Policja jest tutaj. Policja prosi o spokój.");

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).quote()).isEqualTo("Policja jest tutaj.");
    }

    @Test
    void sentenceWithoutPunctuationIsTheWholeSegmentText() {
        assertThat(detect("  prosze podac kod blik  ").get(0).quote()).isEqualTo("prosze podac kod blik");
    }

    @Test
    void harmlessTextHasNoHits() {
        assertThat(detect("Dzień dobry babciu, co dziś gotujesz na obiad?")).isEmpty();
        assertThat(detect("")).isEmpty();
    }

    @Test
    void shortWordsMatchWholeWordsOnly() {
        assertThat(detect("Kupiłem pinezki i spinacze.")).isEmpty();
        assertThat(detect("Mój ulubiony cbsowy serial.")).isEmpty();
    }

    @Test
    void dictionaryCoversTheStagesFromTheTask() {
        assertThat(KeywordDetector.bundled().stagesWithPatterns())
                .contains(StageId.AUTHORITY_CLAIM, StageId.SECRECY_DEMAND, StageId.MONEY_REQUEST,
                        StageId.PAYMENT_CHANNEL, StageId.ISOLATION, StageId.REMOTE_ACCESS,
                        StageId.PERSONAL_DATA_REQUEST);
    }

    private static KeywordDetector fromYaml(String yaml) {
        return KeywordDetector.fromYaml(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "test.yml");
    }

    @Test
    void unknownStageInTheDictionaryNamesFileAndStage() {
        assertThatThrownBy(() -> fromYaml("NOT_A_STAGE:\n  - abc\n"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test.yml")
                .hasMessageContaining("NOT_A_STAGE");
    }

    @Test
    void invalidRegexNamesFileStageAndPattern() {
        assertThatThrownBy(() -> fromYaml("MONEY_REQUEST:\n  - \"wyplac(\"\n"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test.yml")
                .hasMessageContaining("MONEY_REQUEST")
                .hasMessageContaining("wyplac(");
    }

    @Test
    void customDictionaryIsMatchedAgainstNormalizedText() {
        KeywordDetector custom = fromYaml("REMOTE_ACCESS:\n  - \"zdalny pulpit\"\n");

        assertThat(custom.detect(segment("Włącz Zdalny Pulpit."))).extracting(StageHit::stage)
                .containsExactly(StageId.REMOTE_ACCESS);
    }
}
