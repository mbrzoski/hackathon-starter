package pl.aniolstroz.alerts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.aniolstroz.contracts.Alert;
import pl.aniolstroz.contracts.HitSource;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.RiskLevel;
import pl.aniolstroz.contracts.SpeakerRole;
import pl.aniolstroz.contracts.StageHit;
import pl.aniolstroz.contracts.StageId;
import pl.aniolstroz.contracts.TriggeredBy;

class AlertFactoryTest {

    private static final Instant NOW = Instant.parse("2026-10-03T21:00:00Z");

    private final AlertFactory factory = new AlertFactory(AlertTemplates.bundled(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    private static StageHit hit(StageId stage, String segId, boolean validated) {
        return new StageHit(stage, segId, "cytat " + segId, SpeakerRole.UNCLEAR, HitSource.KEYWORDS, validated);
    }

    @Test
    void createsAlertWithTemplateTextsModeAndTrigger() {
        List<StageHit> hits = List.of(hit(StageId.SECRECY_DEMAND, "s2", true), hit(StageId.MONEY_REQUEST, "s3", true));

        Alert alert = factory.create("call-1", Mode.SCRIPTED, RiskLevel.HIGH, hits, TriggeredBy.KEYWORDS);

        assertThat(UUID.fromString(alert.alertId())).isNotNull();
        assertThat(alert.callId()).isEqualTo("call-1");
        assertThat(alert.level()).isEqualTo(RiskLevel.HIGH);
        assertThat(alert.templateId()).isEqualTo("high-secrecy-money");
        assertThat(alert.shortText()).isEqualTo(AlertTemplates.bundled().get("high-secrecy-money").shortText());
        assertThat(alert.advice()).isEqualTo(AlertTemplates.bundled().get("high-secrecy-money").advice());
        assertThat(alert.triggeredBy()).isEqualTo(TriggeredBy.KEYWORDS);
        assertThat(alert.createdAt()).isEqualTo(NOW);
        assertThat(alert.mode()).isEqualTo(Mode.SCRIPTED);
    }

    @Test
    void stagesContainValidatedHitsOnly() {
        StageHit good = hit(StageId.AUTHORITY_CLAIM, "s1", true);
        List<StageHit> hits = List.of(good, hit(StageId.URGENT_THREAT, "s2", false));

        Alert alert = factory.create("call-1", Mode.MOCK, RiskLevel.MEDIUM, hits, TriggeredBy.LLM);

        assertThat(alert.stages()).containsExactly(good);
    }

    @Test
    void templateIsChosenFromTheValidatedStagesOnly() {
        List<StageHit> hits = List.of(hit(StageId.AUTHORITY_CLAIM, "s1", true), hit(StageId.URGENT_THREAT, "s2", true),
                hit(StageId.ISOLATION, "s3", false));

        assertThat(factory.create("c", Mode.MOCK, RiskLevel.MEDIUM, hits, TriggeredBy.LLM).templateId())
                .isEqualTo("medium-general");
    }

    @Test
    void everyAlertGetsItsOwnId() {
        List<StageHit> hits = List.of(hit(StageId.AUTHORITY_CLAIM, "s1", true), hit(StageId.URGENT_THREAT, "s2", true));

        assertThat(factory.create("c", Mode.MOCK, RiskLevel.MEDIUM, hits, TriggeredBy.LLM).alertId())
                .isNotEqualTo(factory.create("c", Mode.MOCK, RiskLevel.MEDIUM, hits, TriggeredBy.LLM).alertId());
    }

    @Test
    void levelsBelowMediumHaveNoAlert() {
        assertThatThrownBy(() -> factory.create("c", Mode.MOCK, RiskLevel.LOW,
                List.of(hit(StageId.AUTHORITY_CLAIM, "s1", true)), TriggeredBy.LLM))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
