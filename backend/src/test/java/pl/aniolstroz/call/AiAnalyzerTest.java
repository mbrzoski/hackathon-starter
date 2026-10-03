package pl.aniolstroz.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import pl.aniolstroz.ai.CallSnapshot;
import pl.aniolstroz.ai.ClassifierError;
import pl.aniolstroz.ai.ClassifierResult;
import pl.aniolstroz.ai.StageClassifier;
import pl.aniolstroz.contracts.Alert;
import pl.aniolstroz.contracts.Component;
import pl.aniolstroz.contracts.ComponentState;
import pl.aniolstroz.contracts.EventEnvelope;
import pl.aniolstroz.contracts.EventEnvelope.AlertCreatedEvent;
import pl.aniolstroz.contracts.EventEnvelope.RiskUpdateEvent;
import pl.aniolstroz.contracts.EventEnvelope.SystemStatusEvent;
import pl.aniolstroz.contracts.HitSource;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.RiskLevel;
import pl.aniolstroz.contracts.SpeakerLabel;
import pl.aniolstroz.contracts.SpeakerRole;
import pl.aniolstroz.contracts.StageHit;
import pl.aniolstroz.contracts.StageId;
import pl.aniolstroz.contracts.TranscriptSegment;
import pl.aniolstroz.contracts.TriggeredBy;
import pl.aniolstroz.events.EventBus;

/** The AI layer wired into a call: queue, quote validation (AI-08), hits as source llm, failure status. */
@ExtendWith(OutputCaptureExtension.class)
class AiAnalyzerTest {

    private static final long WAIT_SECONDS = 5;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-03T21:00:00Z"), ZoneOffset.UTC);

    // Texts without any keyword of the keyword layer, so only the AI can raise risk in these tests.
    private static final String SECRECY_TEXT = "Ta rozmowa musi zostać między nami.";
    private static final String MONEY_TEXT = "Proszę zabrać z konta wszystkie oszczędności.";
    private static final String HARMLESS_TEXT = "Pogoda dzisiaj ładna.";

    private final List<EventEnvelope> events = new CopyOnWriteArrayList<>();
    private final BlockingQueue<ClassifierResult> audited = new LinkedBlockingQueue<>();
    private final List<AiCallReport> reports = new CopyOnWriteArrayList<>();
    private volatile boolean observerFailsOnce;
    private final List<CallSnapshot> snapshots = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private Function<CallSnapshot, ClassifierResult> script = s -> ok(s, List.of());
    /** Runs inside the call lock, just before the analyzer hears of a final segment; null for no pause. */
    private volatile Runnable beforeAnalyzer;
    private CallService calls;
    private CallState call;

    @BeforeEach
    void setUp() {
        EventBus bus = mock(EventBus.class);
        doAnswer(invocation -> events.add(invocation.getArgument(0))).when(bus).publish(any());
        AtomicReference<AiAnalyzer> analyzer = new AtomicReference<>();
        calls = CallServices.create(bus, CLOCK, new DiscardTranscriptHook(), () -> pl.aniolstroz.contracts.Sensitivity.STANDARD,
                event -> {
                    if (event instanceof FinalSegmentAdded added) {
                        Runnable pause = beforeAnalyzer;
                        if (pause != null) {
                            pause.run();
                        }
                        analyzer.get().onFinalSegment(added);
                    }
                });
        StageClassifier classifier = snapshot -> {
            snapshots.add(snapshot);
            return script.apply(snapshot);
        };
        analyzer.set(new AiAnalyzer(calls, classifier, bus, CLOCK, executor, report -> {
            reports.add(report);
            audited.add(report.result());
            if (observerFailsOnce) {
                observerFailsOnce = false;
                throw new IllegalStateException("audit down");
            }
        }, new AiHealth()));
        call = calls.start(Mode.SCRIPTED, "scenario-x");
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private static ClassifierResult ok(CallSnapshot snapshot, List<StageHit> hits) {
        return new ClassifierResult("s1-" + snapshot.lastSegmentId(), "fake-model", "low", "{}", hits,
                new ClassifierResult.Usage(1000, 800, 50), 123, "end_turn", null);
    }

    private static ClassifierResult failed(CallSnapshot snapshot, ClassifierError error) {
        return new ClassifierResult("s1-" + snapshot.lastSegmentId(), "fake-model", "low", null, List.of(),
                new ClassifierResult.Usage(0, 0, 0), 77, null, error);
    }

    private static StageHit llm(StageId stage, String segId, String quote, SpeakerRole role) {
        return new StageHit(stage, segId, quote, role, HitSource.LLM, false);
    }

    private ClassifierResult say(String text) throws InterruptedException {
        calls.addSegment(new TranscriptSegment(call.callId(), "x", 0, 0, text, true, SpeakerLabel.B, null));
        ClassifierResult result = audited.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(result).as("classification result for: segment").isNotNull();
        return result;
    }

    private List<RiskUpdateEvent> riskUpdates() {
        return events.stream().filter(RiskUpdateEvent.class::isInstance).map(RiskUpdateEvent.class::cast).toList();
    }

    private List<Alert> alerts() {
        return events.stream().filter(AlertCreatedEvent.class::isInstance)
                .map(e -> ((AlertCreatedEvent) e).payload()).toList();
    }

    private List<SystemStatusEvent> aiStatuses() {
        return events.stream().filter(SystemStatusEvent.class::isInstance).map(SystemStatusEvent.class::cast)
                .filter(e -> e.payload().component() == Component.AI).toList();
    }

    @Test
    void validatedHitsReachTheCallAsLlmHitsAndRaiseTheRisk() throws Exception {
        script = s -> ok(s, List.of(llm(StageId.SECRECY_DEMAND, "s1", "musi zostać między nami", SpeakerRole.CALLER)));

        ClassifierResult result = say(SECRECY_TEXT);

        assertThat(result.hits()).singleElement().satisfies(h -> assertThat(h.validated()).isTrue());
        assertThat(call.hits()).containsExactly(new StageHit(StageId.SECRECY_DEMAND, "s1", "musi zostać między nami",
                SpeakerRole.CALLER, HitSource.LLM, true));
        assertThat(riskUpdates()).hasSize(1);
        assertThat(riskUpdates().get(0).payload().level()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void anAlertRaisedByAiHitsAloneIsTriggeredByLlm() throws Exception {
        script = s -> ok(s, s.segments().size() == 1
                ? List.of(llm(StageId.SECRECY_DEMAND, "s1", "musi zostać między nami", SpeakerRole.CALLER))
                : List.of(llm(StageId.SECRECY_DEMAND, "s1", "musi zostać między nami", SpeakerRole.CALLER),
                        llm(StageId.MONEY_REQUEST, "s2", "zabrać z konta wszystkie oszczędności", SpeakerRole.CALLER)));

        say(SECRECY_TEXT);
        say(MONEY_TEXT);

        assertThat(alerts()).singleElement().satisfies(a -> {
            assertThat(a.level()).isEqualTo(RiskLevel.HIGH);
            assertThat(a.triggeredBy()).isEqualTo(TriggeredBy.LLM);
            assertThat(a.stages()).extracting(StageHit::source).containsOnly(HitSource.LLM);
        });
    }

    @Test
    void anAlertDecidedByKeywordsAndAiIsTriggeredByBoth() throws Exception {
        script = s -> ok(s, s.segments().size() == 1 ? List.of()
                : List.of(llm(StageId.MONEY_REQUEST, "s2", "zabrać z konta wszystkie oszczędności", SpeakerRole.CALLER)));

        calls.addSegment(new TranscriptSegment(call.callId(), "x", 0, 0, "Mówi policja.", true, SpeakerLabel.B, null));
        audited.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        say(MONEY_TEXT);

        assertThat(alerts()).singleElement().satisfies(a -> {
            assertThat(a.level()).isEqualTo(RiskLevel.HIGH);
            assertThat(a.triggeredBy()).isEqualTo(TriggeredBy.BOTH);
        });
        assertThat(riskUpdates().get(riskUpdates().size() - 1).payload().level()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void aMadeUpQuoteIsMarkedInvalidAndNeverReachesTheCall() throws Exception {
        script = s -> ok(s, List.of(llm(StageId.MONEY_REQUEST, "s1", "proszę przelać na bezpieczne konto", SpeakerRole.CALLER)));

        ClassifierResult result = say(HARMLESS_TEXT);

        assertThat(result.hits()).singleElement().satisfies(h -> assertThat(h.validated()).isFalse());
        assertThat(call.hits()).isEmpty();
        assertThat(riskUpdates()).isEmpty();
        assertThat(alerts()).isEmpty();
    }

    @Test
    void aHitForAnUnknownSegmentIsMarkedInvalid() throws Exception {
        script = s -> ok(s, List.of(llm(StageId.MONEY_REQUEST, "s99", "Pogoda dzisiaj ładna", SpeakerRole.CALLER)));

        ClassifierResult result = say(HARMLESS_TEXT);

        assertThat(result.hits()).singleElement().satisfies(h -> assertThat(h.validated()).isFalse());
        assertThat(call.hits()).isEmpty();
    }

    @Test
    void validAndInvalidHitsInOneAnswerAreSeparated() throws Exception {
        script = s -> ok(s, List.of(
                llm(StageId.SECRECY_DEMAND, "s1", "musi zostać między nami", SpeakerRole.CALLER),
                llm(StageId.MONEY_REQUEST, "s1", "zmyślony cytat", SpeakerRole.CALLER)));

        ClassifierResult result = say(SECRECY_TEXT);

        assertThat(result.hits()).extracting(StageHit::validated).containsExactly(true, false);
        assertThat(call.hits()).extracting(StageHit::stage).containsExactly(StageId.SECRECY_DEMAND);
    }

    @Test
    void theModelReturningTheSameHitsEveryTimeAddsThemOnce() throws Exception {
        script = s -> ok(s, List.of(llm(StageId.SECRECY_DEMAND, "s1", "musi zostać między nami", SpeakerRole.CALLER)));

        say(SECRECY_TEXT);
        say(HARMLESS_TEXT);

        assertThat(call.hits()).hasSize(1);
        assertThat(riskUpdates()).hasSize(1);
    }

    @Test
    void seniorMoneyHitFromTheAiDoesNotRaiseTheLevel() throws Exception {
        script = s -> ok(s, List.of(llm(StageId.MONEY_REQUEST, "s1", "zabrać z konta wszystkie oszczędności",
                SpeakerRole.SENIOR)));

        say("Nie chcę zabrać z konta wszystkie oszczędności.");

        assertThat(call.level()).isEqualTo(RiskLevel.NONE);
        assertThat(alerts()).isEmpty();
    }

    @Test
    void interimSegmentsNeverGoToTheModel() throws Exception {
        calls.addSegment(new TranscriptSegment(call.callId(), "x", 0, 0, "w trakcie", false, SpeakerLabel.B, null));
        calls.addSegment(new TranscriptSegment(call.callId(), "x", 0, 0, "w trakcie mówienia", false, SpeakerLabel.B, null));
        Thread.sleep(150);

        assertThat(snapshots).isEmpty();
        assertThat(audited).isEmpty();
    }

    @Test
    void theModelSeesFinalSegmentsOnlyAndTheCallsModeAndScenario() throws Exception {
        say(HARMLESS_TEXT);

        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.get(0).callId()).isEqualTo(call.callId());
        assertThat(snapshots.get(0).mode()).isEqualTo(Mode.SCRIPTED);
        assertThat(snapshots.get(0).scenarioId()).isEqualTo("scenario-x");
        assertThat(snapshots.get(0).segments()).extracting(TranscriptSegment::text).containsExactly(HARMLESS_TEXT);
    }

    @Test
    void segmentsDuringACallGiveOneFollowUpCall() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        script = s -> {
            if (calls.incrementAndGet() == 1) {
                firstStarted.countDown();
                try {
                    release.await(WAIT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return ok(s, List.of());
        };

        this.calls.addSegment(new TranscriptSegment(call.callId(), "x", 0, 0, "jeden", true, SpeakerLabel.B, null));
        assertThat(firstStarted.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        this.calls.addSegment(new TranscriptSegment(call.callId(), "x", 0, 0, "dwa", true, SpeakerLabel.B, null));
        this.calls.addSegment(new TranscriptSegment(call.callId(), "x", 0, 0, "trzy", true, SpeakerLabel.B, null));
        release.countDown();
        audited.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        audited.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        Thread.sleep(150);

        assertThat(snapshots).hasSize(2);
        assertThat(snapshots.get(1).segments()).hasSize(3);
    }

    /**
     * CC-01: the slot lock is taken before a call lock, never after. The listener runs under the call lock, so if it
     * looked the call up through CallService.active() it would wait for the slot lock that end() holds while it waits
     * for the call lock: a deadlock. Here end() is made to hold the slot lock while the listener runs.
     */
    @Test
    void theFirstFinalSegmentDoesNotWaitForTheSlotLockWhileTheCallIsLocked() throws Exception {
        CountDownLatch inListener = new CountDownLatch(1);
        AtomicReference<Thread> ender = new AtomicReference<>();
        beforeAnalyzer = () -> {
            inListener.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
            Thread t;
            // Wait until end() holds the slot lock and waits for the call lock that this thread holds.
            while (((t = ender.get()) == null || t.getState() != Thread.State.WAITING) && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
        };

        Future<?> adding = executor.submit(() -> calls.addSegment(
                new TranscriptSegment(call.callId(), "x", 0, 0, HARMLESS_TEXT, true, SpeakerLabel.B, null)));
        assertThat(inListener.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        Thread endThread = Thread.ofPlatform().name("ender").start(() -> calls.end());
        ender.set(endThread);

        adding.get(WAIT_SECONDS, TimeUnit.SECONDS);
        endThread.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
        assertThat(endThread.isAlive()).as("end() finished, no deadlock").isFalse();
    }

    @Test
    void anAnswerArrivingAfterTheCallEndedIsDiscarded() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        script = s -> {
            started.countDown();
            try {
                release.await(WAIT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return ok(s, List.of(llm(StageId.SECRECY_DEMAND, "s1", "musi zostać między nami", SpeakerRole.CALLER)));
        };
        calls.addSegment(new TranscriptSegment(call.callId(), "x", 0, 0, SECRECY_TEXT, true, SpeakerLabel.B, null));
        assertThat(started.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

        calls.end();
        int eventsAtEnd = events.size();
        release.countDown();
        assertThat(audited.poll(WAIT_SECONDS, TimeUnit.SECONDS)).isNotNull();

        assertThat(call.hits()).isEmpty();
        assertThat(events).hasSize(eventsAtEnd);
    }

    @Test
    void threeFailuresInARowPublishDegradedAndTheFirstSuccessRestoresOk() throws Exception {
        AtomicInteger n = new AtomicInteger();
        script = s -> n.incrementAndGet() <= 3 ? failed(s, ClassifierError.TIMEOUT) : ok(s, List.of());

        say(HARMLESS_TEXT);
        say(HARMLESS_TEXT);
        assertThat(aiStatuses()).isEmpty();
        say(HARMLESS_TEXT);

        assertThat(aiStatuses()).singleElement().satisfies(e -> {
            assertThat(e.payload().state()).isEqualTo(ComponentState.DEGRADED);
            assertThat(e.payload().message()).isNotBlank();
            assertThat(e.mode()).isEqualTo(Mode.SCRIPTED);
        });

        say(HARMLESS_TEXT);

        assertThat(aiStatuses()).hasSize(2);
        assertThat(aiStatuses().get(1).payload().state()).isEqualTo(ComponentState.OK);
    }

    @Test
    void aSuccessBetweenFailuresResetsTheCount() throws Exception {
        AtomicInteger n = new AtomicInteger();
        script = s -> {
            int i = n.incrementAndGet();
            return i == 3 ? ok(s, List.of()) : failed(s, ClassifierError.RATE_LIMIT);
        };

        for (int i = 0; i < 5; i++) {
            say(HARMLESS_TEXT);
        }

        assertThat(aiStatuses()).isEmpty();
    }

    @Test
    void failedCallsAddNoHitsAndTheKeywordLayerKeepsWorking() throws Exception {
        script = s -> failed(s, ClassifierError.REFUSAL);

        say("Mówi policja. Nikomu nie mów.");

        assertThat(call.hits()).extracting(StageHit::source).containsOnly(HitSource.KEYWORDS);
        assertThat(alerts()).singleElement().satisfies(a -> assertThat(a.triggeredBy()).isEqualTo(TriggeredBy.KEYWORDS));
    }

    @Test
    void everyResultIsReportedWithTheCallsModeAndTheLevelBeforeAndAfter() throws Exception {
        script = s -> ok(s, s.segments().size() == 1 ? List.of()
                : List.of(llm(StageId.MONEY_REQUEST, "s2", "zabrać z konta wszystkie oszczędności", SpeakerRole.CALLER)));

        say("Mówi policja.");
        say(MONEY_TEXT);

        assertThat(reports).hasSize(2);
        AiCallReport first = reports.get(0);
        assertThat(first.callId()).isEqualTo(call.callId());
        assertThat(first.mode()).isEqualTo(Mode.SCRIPTED);
        assertThat(first.levelBefore()).isEqualTo(RiskLevel.LOW);
        assertThat(first.levelAfter()).isEqualTo(RiskLevel.LOW);
        assertThat(first.late()).isFalse();
        AiCallReport second = reports.get(1);
        assertThat(second.levelBefore()).isEqualTo(RiskLevel.LOW);
        assertThat(second.levelAfter()).isEqualTo(RiskLevel.HIGH);
        assertThat(second.late()).isFalse();
        assertThat(second.result().hits()).singleElement().satisfies(h -> assertThat(h.validated()).isTrue());
    }

    @Test
    void theReportCarriesTheKeywordHitsOfTheSameSegmentsOnly() throws Exception {
        script = s -> new ClassifierResult("s2-s2", "fake-model", "low", "{}", List.of(),
                new ClassifierResult.Usage(1, 0, 1), 5, "end_turn", null);

        say("Mówi policja.");
        say("Nikomu nie mów o tym.");

        assertThat(reports.get(1).keywordHits()).extracting(StageHit::stage).containsExactly(StageId.SECRECY_DEMAND);
        assertThat(reports.get(1).keywordHits()).allSatisfy(h -> {
            assertThat(h.source()).isEqualTo(HitSource.KEYWORDS);
            assertThat(h.segId()).isEqualTo("s2");
        });
    }

    @Test
    void failedResultsAreReportedToo() throws Exception {
        script = s -> failed(s, ClassifierError.TIMEOUT);

        say(HARMLESS_TEXT);

        assertThat(reports).singleElement()
                .satisfies(r -> assertThat(r.result().error()).isEqualTo(ClassifierError.TIMEOUT));
    }

    @Test
    void aResultThatArrivesAfterTheCallEndedIsStillReportedWithTheLastKnownLevel() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        script = s -> {
            started.countDown();
            try {
                release.await(WAIT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return ok(s, List.of(llm(StageId.MONEY_REQUEST, "s1", "Mówi policja", SpeakerRole.CALLER)));
        };
        calls.addSegment(new TranscriptSegment(call.callId(), "x", 0, 0, "Mówi policja.", true, SpeakerLabel.B, null));
        assertThat(started.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

        calls.end();
        release.countDown();
        audited.poll(WAIT_SECONDS, TimeUnit.SECONDS);

        assertThat(reports).singleElement().satisfies(r -> {
            assertThat(r.callId()).isEqualTo(call.callId());
            assertThat(r.mode()).isEqualTo(Mode.SCRIPTED);
            assertThat(r.levelBefore()).isEqualTo(RiskLevel.LOW);
            assertThat(r.levelAfter()).isEqualTo(RiskLevel.LOW);
            // Not validated and changes nothing: marked as late instead of being counted as rejected.
            assertThat(r.late()).isTrue();
            assertThat(r.result().hits()).singleElement().satisfies(h -> assertThat(h.validated()).isFalse());
        });
    }

    @Test
    void aFailingObserverDoesNotStopLaterClassifications() throws Exception {
        observerFailsOnce = true;

        say(HARMLESS_TEXT);
        say(HARMLESS_TEXT);

        assertThat(reports).hasSize(2);
    }

    @Test
    void logsOnlyNumbersNeverTranscriptOrQuotes(CapturedOutput output) throws Exception {
        script = s -> ok(s, List.of(llm(StageId.SECRECY_DEMAND, "s1", "musi zostać między nami", SpeakerRole.CALLER)));

        say(SECRECY_TEXT);

        assertThat(output.getAll()).contains("latencyMs=123").contains("inputTokens=1000")
                .contains("cacheReadInputTokens=800").contains("outputTokens=50").contains("valid=1")
                .doesNotContain("musi zostać").doesNotContain("Ta rozmowa").doesNotContain("między nami");
    }
}
