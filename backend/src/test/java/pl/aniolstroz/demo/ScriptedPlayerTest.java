package pl.aniolstroz.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.aniolstroz.call.CallAlreadyActiveException;
import pl.aniolstroz.call.CallService;
import pl.aniolstroz.call.DiscardTranscriptHook;
import pl.aniolstroz.contracts.EventEnvelope;
import pl.aniolstroz.contracts.EventEnvelope.CallEndedEvent;
import pl.aniolstroz.contracts.EventEnvelope.CallStartedEvent;
import pl.aniolstroz.contracts.EventEnvelope.TranscriptSegmentEvent;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.RiskLevel;
import pl.aniolstroz.contracts.Scenario;
import pl.aniolstroz.contracts.SpeakerLabel;
import pl.aniolstroz.contracts.TranscriptSegment;
import pl.aniolstroz.events.EventBus;

/** Playback runs on a real virtual thread, but delays go through a fake {@link Sleeper}, so nothing really waits. */
class ScriptedPlayerTest {

    private static final long WAIT_SECONDS = 5;

    private final List<EventEnvelope> events = new CopyOnWriteArrayList<>();
    private final CountDownLatch callEnded = new CountDownLatch(1);
    private final List<Duration> sleeps = new CopyOnWriteArrayList<>();
    private CallService calls;
    private ScriptedPlayer player;
    private pl.aniolstroz.ai.StageClassifier classifier = snapshot -> {
        throw new AssertionError("the AI is not part of this test");
    };

    @BeforeEach
    void setUp() {
        EventBus bus = mock(EventBus.class);
        doAnswer(invocation -> {
            EventEnvelope event = invocation.getArgument(0);
            events.add(event);
            if (event instanceof CallEndedEvent) {
                callEnded.countDown();
            }
            return null;
        }).when(bus).publish(any());
        calls = pl.aniolstroz.call.CallServices.create(bus, Clock.fixed(Instant.parse("2026-10-03T21:00:00Z"), ZoneOffset.UTC),
                new DiscardTranscriptHook());
    }

    @AfterEach
    void tearDown() {
        if (player != null) {
            player.stop();
        }
    }

    private static Scenario scenario(String id, long... delays) {
        var segments = new java.util.ArrayList<Scenario.Segment>();
        for (int i = 0; i < delays.length; i++) {
            segments.add(new Scenario.Segment(i % 2 == 0 ? SpeakerLabel.B : SpeakerLabel.A, "tekst " + (i + 1), delays[i]));
        }
        return new Scenario(id, "Tytuł " + id, "Opis " + id, Mode.SCRIPTED, null, null, null,
                new Scenario.Expected(RiskLevel.NONE, List.of(), List.of()), segments);
    }

    private void playerWith(Sleeper sleeper, Scenario... scenarios) {
        player = new ScriptedPlayer(new ScenarioRepository(List.of(scenarios)), calls, sleeper, classifier);
    }

    private List<TranscriptSegment> segments() {
        return events.stream()
                .filter(TranscriptSegmentEvent.class::isInstance)
                .map(e -> ((TranscriptSegmentEvent) e).payload())
                .toList();
    }

    private void awaitCallEnded() throws InterruptedException {
        assertThat(callEnded.await(WAIT_SECONDS, TimeUnit.SECONDS)).as("call ended").isTrue();
    }

    @Test
    void playsAllSegmentsInOrderAsFinalScriptedAndThenEndsTheCall() throws Exception {
        playerWith(sleeps::add, scenario("abc", 100, 200, 300));

        player.start("abc", 1.0);
        awaitCallEnded();

        assertThat(events.get(0)).isInstanceOf(CallStartedEvent.class);
        assertThat(events).filteredOn(CallEndedEvent.class::isInstance).hasSize(1);
        assertThat(events.get(events.size() - 1)).isInstanceOf(CallEndedEvent.class);
        assertThat(segments()).extracting(TranscriptSegment::segId).containsExactly("s1", "s2", "s3");
        assertThat(segments()).extracting(TranscriptSegment::text).containsExactly("tekst 1", "tekst 2", "tekst 3");
        assertThat(segments()).extracting(TranscriptSegment::speaker)
                .containsExactly(SpeakerLabel.B, SpeakerLabel.A, SpeakerLabel.B);
        assertThat(segments()).allSatisfy(s -> assertThat(s.isFinal()).isTrue());
        assertThat(events).filteredOn(TranscriptSegmentEvent.class::isInstance)
                .allSatisfy(e -> assertThat(e.mode()).isEqualTo(Mode.SCRIPTED));
        assertThat(calls.active()).isEmpty();
    }

    @Test
    void waitsDelayMsDividedBySpeedBeforeEachSegment() throws Exception {
        playerWith(sleeps::add, scenario("abc", 1000, 500, 250));

        player.start("abc", 2.0);
        awaitCallEnded();

        assertThat(sleeps).containsExactly(Duration.ofMillis(500), Duration.ofMillis(250), Duration.ofMillis(125));
    }

    @Test
    void scriptedTimelineAdvancesByTheScaledDelays() throws Exception {
        playerWith(sleeps::add, scenario("abc", 1000, 500));

        player.start("abc", 2.0);
        awaitCallEnded();

        assertThat(segments()).extracting(TranscriptSegment::tEndMs).containsExactly(500L, 750L);
        assertThat(segments()).allSatisfy(s -> assertThat(s.tStartMs()).isLessThanOrEqualTo(s.tEndMs()));
    }

    @Test
    void theCallStartedFromAScenarioIsLabelledWithThatScenario() throws Exception {
        var sleeper = new BlockingSleeper();
        playerWith(sleeper, scenario("abc", 100, 200));

        player.start("abc", 1.0);
        assertThat(sleeper.secondSleepEntered.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

        assertThat(calls.active().orElseThrow().scenarioId()).isEqualTo("abc");
    }

    @Test
    void withAMockClassifierTheCallIsHonestlyLabelledMock() throws Exception {
        classifier = new pl.aniolstroz.ai.MockStageClassifier(new com.fasterxml.jackson.databind.ObjectMapper());
        playerWith(sleeps::add, scenario("abc", 100, 200));

        player.start("abc", 1.0);
        awaitCallEnded();

        assertThat(events).isNotEmpty().allSatisfy(e -> assertThat(e.mode()).isEqualTo(Mode.MOCK));
    }

    @Test
    void withTheRealClassifierTheCallIsLabelledScripted() throws Exception {
        playerWith(sleeps::add, scenario("abc", 100, 200));

        player.start("abc", 1.0);
        awaitCallEnded();

        assertThat(events).isNotEmpty().allSatisfy(e -> assertThat(e.mode()).isEqualTo(Mode.SCRIPTED));
    }

    @Test
    void unknownScenarioIsReportedAndNoCallStarts() {
        playerWith(sleeps::add, scenario("abc", 100));

        assertThatThrownBy(() -> player.start("nope", 1.0)).isInstanceOf(ScenarioNotFoundException.class);

        assertThat(events).isEmpty();
        assertThat(calls.active()).isEmpty();
    }

    @Test
    void speedMustBePositive() {
        playerWith(sleeps::add, scenario("abc", 100));

        assertThatThrownBy(() -> player.start("abc", 0)).isInstanceOf(IllegalArgumentException.class);

        assertThat(calls.active()).isEmpty();
    }

    /** Lets the first sleep pass, then blocks the second one until interrupted. */
    private static final class BlockingSleeper implements Sleeper {
        final CountDownLatch secondSleepEntered = new CountDownLatch(1);
        private int calls;

        @Override
        public void sleep(Duration duration) throws InterruptedException {
            if (++calls == 2) {
                secondSleepEntered.countDown();
                new CountDownLatch(1).await();
            }
        }
    }

    @Test
    void stopInterruptsPlaybackEndsTheCallAndPublishesNothingMore() throws Exception {
        var sleeper = new BlockingSleeper();
        playerWith(sleeper, scenario("abc", 100, 200, 300));
        player.start("abc", 1.0);
        assertThat(sleeper.secondSleepEntered.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

        player.stop();

        assertThat(segments()).extracting(TranscriptSegment::segId).containsExactly("s1");
        assertThat(events.get(events.size() - 1)).isInstanceOf(CallEndedEvent.class);
        assertThat(events).filteredOn(CallEndedEvent.class::isInstance).hasSize(1);
        assertThat(calls.active()).isEmpty();
    }

    @Test
    void secondReplayIsRejectedWhileOneIsRunning() throws Exception {
        var sleeper = new BlockingSleeper();
        playerWith(sleeper, scenario("abc", 100, 200));
        player.start("abc", 1.0);
        assertThat(sleeper.secondSleepEntered.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> player.start("abc", 1.0)).isInstanceOf(CallAlreadyActiveException.class);
    }

    @Test
    void canReplayAgainAfterStop() throws Exception {
        var sleeper = new BlockingSleeper();
        playerWith(sleeper, scenario("abc", 100, 200));
        player.start("abc", 1.0);
        assertThat(sleeper.secondSleepEntered.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        player.stop();

        player.start("abc", 1.0);

        assertThat(calls.active()).isPresent();
    }

    @Test
    void stopWithoutReplayDoesNothing() {
        playerWith(sleeps::add, scenario("abc", 100));

        player.stop();

        assertThat(events).isEmpty();
    }
}
