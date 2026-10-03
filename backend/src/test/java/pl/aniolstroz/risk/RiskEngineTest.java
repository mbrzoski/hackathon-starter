package pl.aniolstroz.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.aniolstroz.contracts.StageId.AUTHORITY_CLAIM;
import static pl.aniolstroz.contracts.StageId.ISOLATION;
import static pl.aniolstroz.contracts.StageId.MONEY_REQUEST;
import static pl.aniolstroz.contracts.StageId.PAYMENT_CHANNEL;
import static pl.aniolstroz.contracts.StageId.PERSONAL_DATA_REQUEST;
import static pl.aniolstroz.contracts.StageId.REMOTE_ACCESS;
import static pl.aniolstroz.contracts.StageId.SECRECY_DEMAND;
import static pl.aniolstroz.contracts.StageId.URGENT_THREAT;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pl.aniolstroz.contracts.HitSource;
import pl.aniolstroz.contracts.RiskLevel;
import pl.aniolstroz.contracts.Sensitivity;
import pl.aniolstroz.contracts.SpeakerRole;
import pl.aniolstroz.contracts.StageHit;
import pl.aniolstroz.contracts.StageId;
import pl.aniolstroz.contracts.TriggeredBy;

/** DET-01: one row per rule and sensitivity, from section 5 of the architecture. */
class RiskEngineTest {

    private static int counter;

    private static StageHit hit(StageId stage, SpeakerRole role, HitSource source, boolean validated) {
        return new StageHit(stage, "s" + (++counter), "cytat", role, source, validated);
    }

    private static StageHit hit(StageId stage) {
        return hit(stage, SpeakerRole.UNCLEAR, HitSource.KEYWORDS, true);
    }

    private static List<StageHit> hits(StageId... stages) {
        List<StageHit> list = new ArrayList<>();
        for (StageId stage : stages) {
            list.add(hit(stage));
        }
        return list;
    }

    static Stream<Arguments> levels() {
        return Stream.of(
                // STANDARD: none, low, medium, high
                Arguments.of("no hits", hits(), Sensitivity.STANDARD, RiskLevel.NONE),
                Arguments.of("one stage", hits(AUTHORITY_CLAIM), Sensitivity.STANDARD, RiskLevel.LOW),
                Arguments.of("money alone", hits(MONEY_REQUEST), Sensitivity.STANDARD, RiskLevel.LOW),
                Arguments.of("same stage twice is one stage", hits(AUTHORITY_CLAIM, AUTHORITY_CLAIM),
                        Sensitivity.STANDARD, RiskLevel.LOW),
                Arguments.of("authority + threat", hits(AUTHORITY_CLAIM, URGENT_THREAT), Sensitivity.STANDARD,
                        RiskLevel.MEDIUM),
                Arguments.of("authority + remote access", hits(AUTHORITY_CLAIM, REMOTE_ACCESS),
                        Sensitivity.STANDARD, RiskLevel.MEDIUM),
                Arguments.of("money + payment channel, no pressure", hits(MONEY_REQUEST, PAYMENT_CHANNEL),
                        Sensitivity.STANDARD, RiskLevel.MEDIUM),
                Arguments.of("money + personal data, no pressure", hits(MONEY_REQUEST, PERSONAL_DATA_REQUEST),
                        Sensitivity.STANDARD, RiskLevel.MEDIUM),
                Arguments.of("money + authority", hits(MONEY_REQUEST, AUTHORITY_CLAIM), Sensitivity.STANDARD,
                        RiskLevel.HIGH),
                Arguments.of("money + threat", hits(MONEY_REQUEST, URGENT_THREAT), Sensitivity.STANDARD,
                        RiskLevel.HIGH),
                Arguments.of("money + secrecy", hits(MONEY_REQUEST, SECRECY_DEMAND), Sensitivity.STANDARD,
                        RiskLevel.HIGH),
                Arguments.of("money + isolation", hits(MONEY_REQUEST, ISOLATION), Sensitivity.STANDARD,
                        RiskLevel.HIGH),
                Arguments.of("payment channel + authority", hits(PAYMENT_CHANNEL, AUTHORITY_CLAIM),
                        Sensitivity.STANDARD, RiskLevel.HIGH),
                Arguments.of("payment channel + secrecy", hits(PAYMENT_CHANNEL, SECRECY_DEMAND),
                        Sensitivity.STANDARD, RiskLevel.HIGH),
                Arguments.of("classic script", hits(AUTHORITY_CLAIM, SECRECY_DEMAND, MONEY_REQUEST, PAYMENT_CHANNEL),
                        Sensitivity.STANDARD, RiskLevel.HIGH),

                // CALM: two stages for medium, three for high
                Arguments.of("calm: no hits", hits(), Sensitivity.CALM, RiskLevel.NONE),
                Arguments.of("calm: one stage", hits(AUTHORITY_CLAIM), Sensitivity.CALM, RiskLevel.LOW),
                Arguments.of("calm: two non-money stages", hits(AUTHORITY_CLAIM, URGENT_THREAT), Sensitivity.CALM,
                        RiskLevel.MEDIUM),
                Arguments.of("calm: money + authority is only two stages", hits(MONEY_REQUEST, AUTHORITY_CLAIM),
                        Sensitivity.CALM, RiskLevel.MEDIUM),
                Arguments.of("calm: money + payment is only two stages", hits(MONEY_REQUEST, PAYMENT_CHANNEL),
                        Sensitivity.CALM, RiskLevel.MEDIUM),
                Arguments.of("calm: three stages with money and pressure",
                        hits(MONEY_REQUEST, AUTHORITY_CLAIM, SECRECY_DEMAND), Sensitivity.CALM, RiskLevel.HIGH),
                Arguments.of("calm: three stages without pressure",
                        hits(MONEY_REQUEST, PAYMENT_CHANNEL, REMOTE_ACCESS), Sensitivity.CALM, RiskLevel.MEDIUM),

                // SENSITIVE: every rule one level up
                Arguments.of("sensitive: no hits", hits(), Sensitivity.SENSITIVE, RiskLevel.NONE),
                Arguments.of("sensitive: one stage", hits(AUTHORITY_CLAIM), Sensitivity.SENSITIVE,
                        RiskLevel.MEDIUM),
                Arguments.of("sensitive: two non-money stages", hits(AUTHORITY_CLAIM, URGENT_THREAT),
                        Sensitivity.SENSITIVE, RiskLevel.HIGH),
                Arguments.of("sensitive: money + authority", hits(MONEY_REQUEST, AUTHORITY_CLAIM),
                        Sensitivity.SENSITIVE, RiskLevel.HIGH));
    }

    @ParameterizedTest(name = "{0} / {2} -> {3}")
    @MethodSource("levels")
    void levelFollowsTheRules(String name, List<StageHit> hits, Sensitivity sensitivity, RiskLevel expected) {
        assertThat(RiskEngine.computeLevel(hits, sensitivity).level()).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("excludedBySpeaker")
    void seniorAndBackgroundDoNotCountForMoneyOrPayment(SpeakerRole role, StageId stage) {
        List<StageHit> hits = List.of(hit(stage, role, HitSource.LLM, true));

        assertThat(RiskEngine.computeLevel(hits, Sensitivity.STANDARD).level()).isEqualTo(RiskLevel.NONE);
        assertThat(RiskEngine.countedHits(hits)).isEmpty();
    }

    static Stream<Arguments> excludedBySpeaker() {
        return Stream.of(SpeakerRole.SENIOR, SpeakerRole.BACKGROUND)
                .flatMap(role -> Stream.of(MONEY_REQUEST, PAYMENT_CHANNEL).map(stage -> Arguments.of(role, stage)));
    }

    @Test
    void seniorMoneyHitDoesNotTurnAuthorityIntoHigh() {
        List<StageHit> hits = List.of(
                hit(AUTHORITY_CLAIM, SpeakerRole.CALLER, HitSource.LLM, true),
                hit(MONEY_REQUEST, SpeakerRole.SENIOR, HitSource.LLM, true));

        assertThat(RiskEngine.computeLevel(hits, Sensitivity.STANDARD).level()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void otherStagesFromSeniorAndBackgroundStillCount() {
        List<StageHit> hits = List.of(
                hit(AUTHORITY_CLAIM, SpeakerRole.SENIOR, HitSource.LLM, true),
                hit(SECRECY_DEMAND, SpeakerRole.BACKGROUND, HitSource.LLM, true));

        assertThat(RiskEngine.computeLevel(hits, Sensitivity.STANDARD).level()).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void callerAndUnclearMoneyHitsCount() {
        for (SpeakerRole role : List.of(SpeakerRole.CALLER, SpeakerRole.UNCLEAR)) {
            List<StageHit> hits = List.of(hit(MONEY_REQUEST, role, HitSource.LLM, true));

            assertThat(RiskEngine.computeLevel(hits, Sensitivity.STANDARD).level()).isEqualTo(RiskLevel.LOW);
        }
    }

    @Test
    void unvalidatedHitsAreIgnored() {
        List<StageHit> hits = List.of(
                hit(AUTHORITY_CLAIM, SpeakerRole.CALLER, HitSource.LLM, false),
                hit(MONEY_REQUEST, SpeakerRole.CALLER, HitSource.LLM, false));

        RiskAssessment result = RiskEngine.computeLevel(hits, Sensitivity.SENSITIVE);

        assertThat(result.level()).isEqualTo(RiskLevel.NONE);
        assertThat(result.triggeredBy()).isNull();
    }

    @Test
    void triggeredByKeywordsWhenOnlyKeywordHitsCount() {
        List<StageHit> hits = List.of(
                hit(AUTHORITY_CLAIM, SpeakerRole.UNCLEAR, HitSource.KEYWORDS, true),
                hit(SECRECY_DEMAND, SpeakerRole.UNCLEAR, HitSource.KEYWORDS, true));

        assertThat(RiskEngine.computeLevel(hits, Sensitivity.STANDARD).triggeredBy()).isEqualTo(TriggeredBy.KEYWORDS);
    }

    @Test
    void triggeredByLlmWhenOnlyLlmHitsCount() {
        List<StageHit> hits = List.of(
                hit(AUTHORITY_CLAIM, SpeakerRole.CALLER, HitSource.LLM, true),
                hit(SECRECY_DEMAND, SpeakerRole.CALLER, HitSource.LLM, true));

        assertThat(RiskEngine.computeLevel(hits, Sensitivity.STANDARD).triggeredBy()).isEqualTo(TriggeredBy.LLM);
    }

    @Test
    void triggeredByBothWhenBothSourcesCount() {
        List<StageHit> hits = List.of(
                hit(AUTHORITY_CLAIM, SpeakerRole.UNCLEAR, HitSource.KEYWORDS, true),
                hit(SECRECY_DEMAND, SpeakerRole.CALLER, HitSource.LLM, true));

        assertThat(RiskEngine.computeLevel(hits, Sensitivity.STANDARD).triggeredBy()).isEqualTo(TriggeredBy.BOTH);
    }

    @Test
    void hitsThatDoNotCountDoNotInfluenceTriggeredBy() {
        List<StageHit> hits = List.of(
                hit(AUTHORITY_CLAIM, SpeakerRole.UNCLEAR, HitSource.KEYWORDS, true),
                hit(SECRECY_DEMAND, SpeakerRole.UNCLEAR, HitSource.KEYWORDS, true),
                hit(MONEY_REQUEST, SpeakerRole.SENIOR, HitSource.LLM, true),
                hit(PAYMENT_CHANNEL, SpeakerRole.CALLER, HitSource.LLM, false));

        assertThat(RiskEngine.computeLevel(hits, Sensitivity.STANDARD).triggeredBy()).isEqualTo(TriggeredBy.KEYWORDS);
    }

    // triggeredBy names the sources of the hits that decided the level, not of every hit that counts.

    @Test
    void anUnrelatedKeywordHitDoesNotMakeAHighLevelBoth() {
        List<StageHit> hits = List.of(
                hit(MONEY_REQUEST, SpeakerRole.CALLER, HitSource.LLM, true),
                hit(AUTHORITY_CLAIM, SpeakerRole.CALLER, HitSource.LLM, true),
                hit(REMOTE_ACCESS, SpeakerRole.UNCLEAR, HitSource.KEYWORDS, true));

        RiskAssessment result = RiskEngine.computeLevel(hits, Sensitivity.STANDARD);

        assertThat(result.level()).isEqualTo(RiskLevel.HIGH);
        assertThat(result.triggeredBy()).isEqualTo(TriggeredBy.LLM);
    }

    @Test
    void bothSourcesInTheDecidingStagesGiveBoth() {
        List<StageHit> hits = List.of(
                hit(MONEY_REQUEST, SpeakerRole.UNCLEAR, HitSource.KEYWORDS, true),
                hit(AUTHORITY_CLAIM, SpeakerRole.CALLER, HitSource.LLM, true));

        assertThat(RiskEngine.computeLevel(hits, Sensitivity.STANDARD).triggeredBy()).isEqualTo(TriggeredBy.BOTH);
    }

    @Test
    void forMediumEveryCountedStageDecides() {
        List<StageHit> hits = List.of(
                hit(REMOTE_ACCESS, SpeakerRole.UNCLEAR, HitSource.KEYWORDS, true),
                hit(URGENT_THREAT, SpeakerRole.CALLER, HitSource.LLM, true));

        RiskAssessment result = RiskEngine.computeLevel(hits, Sensitivity.STANDARD);

        assertThat(result.level()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(result.triggeredBy()).isEqualTo(TriggeredBy.BOTH);
    }

    @Test
    void aStageSeenByBothSourcesCountsBothEvenWhenOtherStagesAreLlmOnly() {
        List<StageHit> hits = List.of(
                hit(MONEY_REQUEST, SpeakerRole.CALLER, HitSource.LLM, true),
                hit(MONEY_REQUEST, SpeakerRole.UNCLEAR, HitSource.KEYWORDS, true),
                hit(SECRECY_DEMAND, SpeakerRole.CALLER, HitSource.LLM, true));

        assertThat(RiskEngine.computeLevel(hits, Sensitivity.STANDARD).triggeredBy()).isEqualTo(TriggeredBy.BOTH);
    }

    @Test
    void countedHitsKeepsOnlyValidatedHitsThatPassTheSpeakerRule() {
        StageHit counted = hit(AUTHORITY_CLAIM, SpeakerRole.SENIOR, HitSource.LLM, true);
        List<StageHit> hits = List.of(counted,
                hit(MONEY_REQUEST, SpeakerRole.SENIOR, HitSource.LLM, true),
                hit(SECRECY_DEMAND, SpeakerRole.CALLER, HitSource.LLM, false));

        assertThat(RiskEngine.countedHits(hits)).containsExactly(counted);
    }
}
