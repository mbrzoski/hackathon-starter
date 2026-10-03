package pl.aniolstroz.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.aniolstroz.contracts.HitSource;
import pl.aniolstroz.contracts.SpeakerLabel;
import pl.aniolstroz.contracts.SpeakerRole;
import pl.aniolstroz.contracts.StageHit;
import pl.aniolstroz.contracts.StageId;
import pl.aniolstroz.contracts.TranscriptSegment;

/** AI-08: a hit counts only if its segment exists and its quote is in that segment after normalization. */
class QuoteValidatorTest {

    private static final List<TranscriptSegment> TRANSCRIPT = List.of(
            segment("s1", "Dzień dobry, czy rozmawiam z panią Zofią?"),
            segment("s2", "Proszę wypłacić pieniądze z konta, nikomu nie mów!"),
            segment("s3", "wyplac pieniondze do kopeCie"));

    private static TranscriptSegment segment(String id, String text) {
        return new TranscriptSegment("c", id, 0, 0, text, true, SpeakerLabel.B, null);
    }

    private static StageHit hit(String segId, String quote) {
        return new StageHit(StageId.MONEY_REQUEST, segId, quote, SpeakerRole.CALLER, HitSource.LLM, false);
    }

    private static boolean validated(String segId, String quote) {
        return QuoteValidator.validate(List.of(hit(segId, quote)), TRANSCRIPT).get(0).validated();
    }

    @Test
    void exactQuoteIsValid() {
        assertThat(validated("s2", "Proszę wypłacić pieniądze z konta")).isTrue();
    }

    @Test
    void quoteOfThePartOfASentenceIsValid() {
        assertThat(validated("s2", "nikomu nie mów")).isTrue();
    }

    @Test
    void quoteWithoutPolishDiacriticsIsValidAgainstATextWithThem() {
        assertThat(validated("s2", "Prosze wyplacic pieniadze z konta")).isTrue();
        assertThat(validated("s1", "rozmawiam z pania Zofia")).isTrue();
    }

    @Test
    void caseAndPunctuationAndWhitespaceDoNotMatter() {
        assertThat(validated("s2", "  PROSZĘ   wypłacić pieniądze,  z konta!! ")).isTrue();
    }

    @Test
    void aQuoteShorterThanThreeCharactersAfterNormalizationIsNotValid() {
        // "a" and "z" are in the segment, but one letter proves nothing
        assertThat(validated("s1", "a")).isFalse();
        assertThat(validated("s2", "z")).isFalse();
        assertThat(validated("s2", "ż")).isFalse();
        assertThat(validated("s2", " , ")).isFalse();
    }

    @Test
    void aQuoteOfExactlyThreeCharactersIsStillValid() {
        assertThat(validated("s2", "nie")).isTrue();
        assertThat(validated("s2", "ni e")).isFalse();
    }

    @Test
    void quoteMatchesTheErroneousTranscriptTextNotTheCorrectedOne() {
        assertThat(validated("s3", "wyplac pieniondze do kopeCie")).isTrue();
        assertThat(validated("s3", "wypłać pieniądze do kopercie")).isFalse();
    }

    @Test
    void madeUpQuoteIsInvalid() {
        assertThat(validated("s2", "Proszę przelać pieniądze na bezpieczne konto")).isFalse();
    }

    @Test
    void quoteFromAnotherSegmentIsInvalid() {
        assertThat(validated("s1", "Proszę wypłacić pieniądze z konta")).isFalse();
    }

    @Test
    void unknownSegmentIdIsInvalid() {
        assertThat(validated("s99", "Proszę wypłacić pieniądze z konta")).isFalse();
        assertThat(validated("x", "Proszę wypłacić pieniądze z konta")).isFalse();
    }

    @Test
    void emptyOrPunctuationOnlyQuoteIsInvalid() {
        assertThat(validated("s2", "")).isFalse();
        assertThat(validated("s2", " ... !!! ")).isFalse();
    }

    @Test
    void validationKeepsEveryHitInOrderAndOnlyChangesTheFlag() {
        StageHit good = hit("s2", "nikomu nie mów");
        StageHit bad = hit("s2", "zmyślony cytat");

        List<StageHit> result = QuoteValidator.validate(List.of(good, bad), TRANSCRIPT);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(new StageHit(good.stage(), good.segId(), good.quote(),
                good.speakerRole(), good.source(), true));
        assertThat(result.get(1)).isEqualTo(bad);
        assertThat(result.get(1).validated()).isFalse();
    }

    @Test
    void anAlreadyValidatedFlagIsNotTrusted() {
        StageHit claimsValid = new StageHit(StageId.MONEY_REQUEST, "s2", "zmyślony cytat", SpeakerRole.CALLER,
                HitSource.LLM, true);

        assertThat(QuoteValidator.validate(List.of(claimsValid), TRANSCRIPT).get(0).validated()).isFalse();
    }

    @Test
    void emptyInputGivesEmptyResult() {
        assertThat(QuoteValidator.validate(List.of(), TRANSCRIPT)).isEmpty();
        assertThat(QuoteValidator.validate(List.of(hit("s1", "dzień dobry")), List.of()).get(0).validated()).isFalse();
    }
}
