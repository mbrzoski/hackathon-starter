package pl.aniolstroz.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.aniolstroz.contracts.EventEnvelope;
import pl.aniolstroz.contracts.EventEnvelope.CallEndedEvent;
import pl.aniolstroz.contracts.EventEnvelope.CallStartedEvent;
import pl.aniolstroz.contracts.EventEnvelope.TranscriptSegmentEvent;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.SpeakerLabel;
import pl.aniolstroz.contracts.TranscriptSegment;
import pl.aniolstroz.events.EventBus;

class CallServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-03T21:00:00Z");

    private final EventBus eventBus = mock(EventBus.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private CallService service;

    @BeforeEach
    void setUp() {
        service = CallServices.create(eventBus, clock, new DiscardTranscriptHook());
    }

    private List<EventEnvelope> published(int times) {
        var captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(eventBus, org.mockito.Mockito.times(times)).publish(captor.capture());
        return captor.getAllValues();
    }

    private static TranscriptSegment segment(String callId, String text, boolean isFinal) {
        return new TranscriptSegment(callId, "ignored", 10, 20, text, isFinal, SpeakerLabel.B, null);
    }

    @Test
    void startCreatesUuidCallAndPublishesCallStartedWithMode() {
        CallState call = service.start(Mode.SCRIPTED);

        assertThat(UUID.fromString(call.callId())).isNotNull();
        assertThat(call.mode()).isEqualTo(Mode.SCRIPTED);
        assertThat(service.active()).containsSame(call);
        var event = (CallStartedEvent) published(1).get(0);
        assertThat(event.mode()).isEqualTo(Mode.SCRIPTED);
        assertThat(event.at()).isEqualTo(NOW);
        assertThat(event.payload().callId()).isEqualTo(call.callId());
    }

    @Test
    void startWhileACallIsActiveIsRejected() {
        service.start(Mode.SCRIPTED);

        assertThatThrownBy(() -> service.start(Mode.LIVE)).isInstanceOf(CallAlreadyActiveException.class);
    }

    @Test
    void segmentsGetSequentialIdsAndAreKeptAndPublishedInOrder() {
        CallState call = service.start(Mode.SCRIPTED);

        var first = service.addSegment(segment(call.callId(), "pierwszy", true));
        var second = service.addSegment(segment(call.callId(), "drugi", true));

        assertThat(first.segId()).isEqualTo("s1");
        assertThat(second.segId()).isEqualTo("s2");
        assertThat(call.transcript()).extracting(TranscriptSegment::text).containsExactly("pierwszy", "drugi");
        List<EventEnvelope> events = published(3);
        assertThat(events.subList(1, 3)).allSatisfy(e -> assertThat(e).isInstanceOf(TranscriptSegmentEvent.class));
        assertThat(((TranscriptSegmentEvent) events.get(1)).payload().segId()).isEqualTo("s1");
        assertThat(((TranscriptSegmentEvent) events.get(2)).payload().segId()).isEqualTo("s2");
        assertThat(((TranscriptSegmentEvent) events.get(1)).mode()).isEqualTo(Mode.SCRIPTED);
    }

    @Test
    void interimSegmentsArePublishedButNotKept() {
        CallState call = service.start(Mode.SCRIPTED);

        service.addSegment(segment(call.callId(), "w trakcie", false));

        assertThat(call.transcript()).isEmpty();
        assertThat(published(2)).hasSize(2);
    }

    @Test
    void segmentForAnotherCallIsRejected() {
        service.start(Mode.SCRIPTED);

        assertThatThrownBy(() -> service.addSegment(segment("other-call", "x", true)))
                .isInstanceOf(NoActiveCallException.class);
    }

    @Test
    void segmentWithoutActiveCallIsRejected() {
        assertThatThrownBy(() -> service.addSegment(segment("any", "x", true)))
                .isInstanceOf(NoActiveCallException.class);
    }

    @Test
    void endPublishesCallEndedAndFreesTheSlot() {
        CallState call = service.start(Mode.REPLAY);

        assertThat(service.end()).isTrue();

        var event = (CallEndedEvent) published(2).get(1);
        assertThat(event.mode()).isEqualTo(Mode.REPLAY);
        assertThat(event.payload().callId()).isEqualTo(call.callId());
        assertThat(event.payload().hadAlert()).isFalse();
        assertThat(service.active()).isEmpty();
        assertThat(service.start(Mode.LIVE).callId()).isNotEqualTo(call.callId());
    }

    @Test
    void endWithoutActiveCallDoesNothing() {
        assertThat(service.end()).isFalse();

        org.mockito.Mockito.verifyNoInteractions(eventBus);
    }

    @Test
    void endByCallIdIgnoresAnotherCall() {
        CallState call = service.start(Mode.SCRIPTED);

        assertThat(service.end("some-older-call")).isFalse();

        assertThat(service.active()).containsSame(call);
    }

    @Test
    void defaultHookDeletesTheWholeTranscriptWhenCallEnds() {
        CallState call = service.start(Mode.SCRIPTED);
        service.addSegment(segment(call.callId(), "poufne", true));

        service.end();

        assertThat(call.transcript()).isEmpty();
    }

    @Test
    void hookSeesTheTranscriptBeforeItIsDeletedAndCanKeepIt() {
        List<String> seen = new ArrayList<>();
        service = CallServices.create(eventBus, clock, state -> state.transcript().forEach(s -> seen.add(s.text())));
        CallState call = service.start(Mode.SCRIPTED);
        service.addSegment(segment(call.callId(), "alert", true));

        service.end();

        assertThat(seen).containsExactly("alert");
        assertThat(call.transcript()).hasSize(1);
    }

    @Test
    void endReportsHadAlertWhenTheCallRaisedAnAlert() {
        CallState call = service.start(Mode.SCRIPTED);
        service.addSegment(segment(call.callId(), "Mówi policja. Nikomu nie mów.", true));

        service.end();

        var events = published(5);
        assertThat(((CallEndedEvent) events.get(4)).payload().hadAlert()).isTrue();
    }

    @Test
    void failingHookStillEndsTheCallFreesTheSlotAndDropsTheTranscript() {
        service = CallServices.create(eventBus, clock, state -> {
            throw new IllegalStateException("disk full");
        });
        CallState call = service.start(Mode.SCRIPTED);
        service.addSegment(segment(call.callId(), "poufne", true));

        assertThat(service.end()).isTrue();

        assertThat(service.active()).isEmpty();
        assertThat(call.transcript()).isEmpty();
        var events = published(4);
        assertThat(events.get(2)).isInstanceOf(EventEnvelope.SystemStatusEvent.class);
        assertThat(((EventEnvelope.SystemStatusEvent) events.get(2)).payload().state())
                .isEqualTo(pl.aniolstroz.contracts.ComponentState.DEGRADED);
        assertThat(events.get(3)).isInstanceOf(CallEndedEvent.class);
    }

    @Test
    void interimSegmentUsesTheIdOfTheFinalOneThatFollowsAndDoesNotConsumeIt() {
        CallState call = service.start(Mode.SCRIPTED);

        var interim = service.addSegment(segment(call.callId(), "w tra", false));
        var interimAgain = service.addSegment(segment(call.callId(), "w trakcie", false));
        var fin = service.addSegment(segment(call.callId(), "w trakcie rozmowy", true));
        var next = service.addSegment(segment(call.callId(), "dalej", true));

        assertThat(interim.segId()).isEqualTo("s1");
        assertThat(interimAgain.segId()).isEqualTo("s1");
        assertThat(fin.segId()).isEqualTo("s1");
        assertThat(next.segId()).isEqualTo("s2");
    }

    @Test
    void noSegmentsAfterTheCallEnded() {
        CallState call = service.start(Mode.SCRIPTED);
        service.end();

        assertThatThrownBy(() -> service.addSegment(segment(call.callId(), "późno", true)))
                .isInstanceOf(NoActiveCallException.class);
    }
}
