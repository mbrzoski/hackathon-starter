package pl.aniolstroz.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.aniolstroz.contracts.Alert;
import pl.aniolstroz.contracts.EventEnvelope;
import pl.aniolstroz.contracts.EventEnvelope.AlertCreatedEvent;
import pl.aniolstroz.contracts.EventEnvelope.RiskUpdateEvent;
import pl.aniolstroz.contracts.HitSource;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.RiskLevel;
import pl.aniolstroz.contracts.RiskUpdate;
import pl.aniolstroz.contracts.Sensitivity;
import pl.aniolstroz.contracts.SpeakerLabel;
import pl.aniolstroz.contracts.SpeakerRole;
import pl.aniolstroz.contracts.StageHit;
import pl.aniolstroz.contracts.StageId;
import pl.aniolstroz.contracts.TranscriptSegment;
import pl.aniolstroz.contracts.TriggeredBy;
import pl.aniolstroz.events.EventBus;

/** Keyword detection wired into the call, the risk.update stream and one alert per level (DET-05). */
class CallServiceRiskTest {

    private static final Instant NOW = Instant.parse("2026-10-03T21:00:00Z");

    private final List<EventEnvelope> events = new ArrayList<>();
    private final AtomicReference<Sensitivity> sensitivity = new AtomicReference<>(Sensitivity.STANDARD);
    private CallService service;
    private CallState call;

    @BeforeEach
    void setUp() {
        EventBus bus = mock(EventBus.class);
        doAnswer(invocation -> events.add(invocation.getArgument(0))).when(bus).publish(any());
        service = CallServices.create(bus, Clock.fixed(NOW, ZoneOffset.UTC), new DiscardTranscriptHook(),
                sensitivity::get);
        call = service.start(Mode.SCRIPTED);
    }

    private TranscriptSegment say(String text) {
        return service.addSegment(new TranscriptSegment(call.callId(), "x", 0, 0, text, true, SpeakerLabel.B, null));
    }

    private TranscriptSegment sayInterim(String text) {
        return service.addSegment(new TranscriptSegment(call.callId(), "x", 0, 0, text, false, SpeakerLabel.B, null));
    }

    private List<RiskUpdate> riskUpdates() {
        return events.stream().filter(RiskUpdateEvent.class::isInstance)
                .map(e -> ((RiskUpdateEvent) e).payload()).toList();
    }

    private List<Alert> alerts() {
        return events.stream().filter(AlertCreatedEvent.class::isInstance)
                .map(e -> ((AlertCreatedEvent) e).payload()).toList();
    }

    private static StageHit llmHit(StageId stage, String segId, SpeakerRole role, boolean validated) {
        return new StageHit(stage, segId, "cytat", role, HitSource.LLM, validated);
    }

    @Test
    void keywordHitInAnInterimSegmentPublishesRiskUpdate() {
        sayInterim("Mówi policja");

        assertThat(riskUpdates()).hasSize(1);
        RiskUpdate update = riskUpdates().get(0);
        assertThat(update.callId()).isEqualTo(call.callId());
        assertThat(update.level()).isEqualTo(RiskLevel.LOW);
        assertThat(update.previousLevel()).isEqualTo(RiskLevel.NONE);
        assertThat(update.stages()).containsExactly(StageId.AUTHORITY_CLAIM);
        assertThat(update.warningSigns()).isEqualTo(1);
        assertThat(alerts()).isEmpty();
    }

    @Test
    void riskUpdateCarriesTheModeOfTheCall() {
        say("Mówi policja.");

        assertThat(events).filteredOn(RiskUpdateEvent.class::isInstance)
                .allSatisfy(e -> assertThat(e.mode()).isEqualTo(Mode.SCRIPTED));
    }

    @Test
    void harmlessSegmentsPublishNoRiskUpdate() {
        say("Dzień dobry babciu.");
        sayInterim("co słychać");

        assertThat(riskUpdates()).isEmpty();
    }

    @Test
    void interimAndFinalOfTheSameSegmentAddOneHitAndOneRiskUpdate() {
        sayInterim("Mówi policja");
        say("Mówi policja.");

        assertThat(call.hits()).hasSize(1);
        assertThat(call.hits().get(0).segId()).isEqualTo("s1");
        assertThat(riskUpdates()).hasSize(1);
    }

    @Test
    void sameStageInAnotherSegmentIsAnotherHitButTheStageIsCountedOnce() {
        say("Mówi policja.");
        say("Tu policja, proszę słuchać.");

        assertThat(call.hits()).hasSize(2);
        assertThat(riskUpdates()).hasSize(2);
        assertThat(riskUpdates().get(1).stages()).containsExactly(StageId.AUTHORITY_CLAIM);
        assertThat(riskUpdates().get(1).warningSigns()).isEqualTo(1);
    }

    @Test
    void hitsAreKeptWithoutDuplicatesByStageSegmentAndSource() {
        StageHit hit = llmHit(StageId.SECRECY_DEMAND, "s1", SpeakerRole.CALLER, true);

        service.addHits(call.callId(), List.of(hit));
        service.addHits(call.callId(), List.of(hit, llmHit(StageId.SECRECY_DEMAND, "s1", SpeakerRole.CALLER, true)));

        assertThat(call.hits()).containsExactly(hit);
        assertThat(riskUpdates()).hasSize(1);
    }

    @Test
    void sameStageAndSegmentFromAnotherSourceIsKeptSoBothSourcesAreVisible() {
        say("Mówi policja.");
        service.addHits(call.callId(), List.of(llmHit(StageId.AUTHORITY_CLAIM, "s1", SpeakerRole.CALLER, true),
                llmHit(StageId.SECRECY_DEMAND, "s1", SpeakerRole.CALLER, true)));

        assertThat(call.hits()).hasSize(3);
        assertThat(alerts()).hasSize(1);
        assertThat(alerts().get(0).triggeredBy()).isEqualTo(TriggeredBy.BOTH);
    }

    @Test
    void oneAlertPerLevelAsRiskGrows() {
        say("Mówi policja.");
        assertThat(alerts()).isEmpty();

        say("Nikomu nie mów o tej rozmowie.");
        assertThat(alerts()).hasSize(1);
        assertThat(alerts().get(0).level()).isEqualTo(RiskLevel.MEDIUM);

        say("Dyskrecja jest ważna.");
        assertThat(alerts()).hasSize(1);

        say("Proszę wypłacić gotówkę.");
        assertThat(alerts()).hasSize(2);
        assertThat(alerts().get(1).level()).isEqualTo(RiskLevel.HIGH);

        say("Kod BLIK proszę podać.");
        assertThat(alerts()).hasSize(2);
        assertThat(riskUpdates().get(riskUpdates().size() - 1).level()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void jumpingStraightToHighCreatesOnlyTheHighAlert() {
        say("Mówi policja, proszę wypłacić pieniądze i nikomu nie mów.");

        assertThat(alerts()).hasSize(1);
        assertThat(alerts().get(0).level()).isEqualTo(RiskLevel.HIGH);
        assertThat(alerts().get(0).templateId()).isEqualTo("high-secrecy-money");
        assertThat(riskUpdates()).hasSize(1);
        assertThat(riskUpdates().get(0).previousLevel()).isEqualTo(RiskLevel.NONE);
        assertThat(riskUpdates().get(0).level()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void riskUpdateIsPublishedBeforeTheAlertItCaused() {
        say("Mówi policja. Nikomu nie mów.");

        int update = events.indexOf(events.stream().filter(RiskUpdateEvent.class::isInstance).findFirst().orElseThrow());
        int alert = events.indexOf(events.stream().filter(AlertCreatedEvent.class::isInstance).findFirst().orElseThrow());
        assertThat(update).isLessThan(alert);
    }

    @Test
    void alertCarriesTemplateTextsModeAndOnlyTheHitsThatCount() {
        say("Mówi policja. Nikomu nie mów.");
        service.addHits(call.callId(), List.of(
                llmHit(StageId.MONEY_REQUEST, "s1", SpeakerRole.SENIOR, true),
                llmHit(StageId.URGENT_THREAT, "s1", SpeakerRole.CALLER, false)));
        say("Proszę wypłacić pieniądze.");

        Alert alert = alerts().get(1);

        assertThat(alert.mode()).isEqualTo(Mode.SCRIPTED);
        assertThat(alert.callId()).isEqualTo(call.callId());
        assertThat(alert.createdAt()).isEqualTo(NOW);
        assertThat(alert.shortText()).isEqualTo("Rozmówca prosi o tajemnicę i o pieniądze.");
        assertThat(alert.stages()).extracting(StageHit::stage)
                .containsExactlyInAnyOrder(StageId.AUTHORITY_CLAIM, StageId.SECRECY_DEMAND, StageId.MONEY_REQUEST);
        assertThat(alert.stages()).allSatisfy(h -> {
            assertThat(h.validated()).isTrue();
            assertThat(h.speakerRole()).isNotEqualTo(SpeakerRole.SENIOR);
        });
        assertThat(alert.triggeredBy()).isEqualTo(TriggeredBy.KEYWORDS);
    }

    @Test
    void callStateRemembersItsAlerts() {
        say("Mówi policja. Nikomu nie mów.");

        assertThat(call.hadAlert()).isTrue();
        assertThat(call.alerts()).containsExactlyElementsOf(alerts());
        assertThat(call.level()).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void levelNeverDropsEvenIfTheSensitivityIsLoweredLater() {
        sensitivity.set(Sensitivity.SENSITIVE);
        say("Mówi policja.");
        assertThat(call.level()).isEqualTo(RiskLevel.MEDIUM);

        sensitivity.set(Sensitivity.STANDARD);
        say("Tu policja, proszę słuchać.");

        RiskUpdate last = riskUpdates().get(riskUpdates().size() - 1);
        assertThat(last.previousLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(last.level()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(call.level()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(alerts()).hasSize(1);
    }

    @Test
    void sensitivityIsReadWhenTheRiskIsComputed() {
        sensitivity.set(Sensitivity.SENSITIVE);

        say("Mówi policja.");

        assertThat(alerts()).hasSize(1);
        assertThat(alerts().get(0).level()).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void seniorMoneyHitIsStoredButDoesNotChangeTheLevel() {
        say("Mówi policja.");

        service.addHits(call.callId(), List.of(llmHit(StageId.MONEY_REQUEST, "s1", SpeakerRole.SENIOR, true)));

        RiskUpdate last = riskUpdates().get(riskUpdates().size() - 1);
        assertThat(last.level()).isEqualTo(RiskLevel.LOW);
        assertThat(last.stages()).containsExactly(StageId.AUTHORITY_CLAIM);
        assertThat(call.hits()).hasSize(2);
        assertThat(alerts()).isEmpty();
    }

    @Test
    void hitsForAnotherCallAreRejected() {
        assertThatThrownBy(() -> service.addHits("other",
                List.of(llmHit(StageId.SECRECY_DEMAND, "s1", SpeakerRole.CALLER, true))))
                .isInstanceOf(NoActiveCallException.class);
    }

    @Test
    void noHitsAfterTheCallEnded() {
        service.end();

        assertThatThrownBy(() -> service.addHits(call.callId(),
                List.of(llmHit(StageId.SECRECY_DEMAND, "s1", SpeakerRole.CALLER, true))))
                .isInstanceOf(NoActiveCallException.class);
    }
}
