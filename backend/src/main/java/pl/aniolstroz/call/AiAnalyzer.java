package pl.aniolstroz.call;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pl.aniolstroz.ai.CallClassificationQueue;
import pl.aniolstroz.ai.CallSnapshot;
import pl.aniolstroz.ai.ClassifierResult;
import pl.aniolstroz.ai.StageClassifier;
import pl.aniolstroz.contracts.ComponentState;
import pl.aniolstroz.contracts.EventEnvelope.SystemStatusEvent;
import pl.aniolstroz.contracts.HitSource;
import pl.aniolstroz.contracts.RiskLevel;
import pl.aniolstroz.contracts.StageHit;
import pl.aniolstroz.contracts.SystemStatus;
import pl.aniolstroz.events.EventBus;
import pl.aniolstroz.risk.QuoteValidator;

/**
 * Connects the call to the AI layer. Every final segment (never an interim one, AUD-06) pokes the call's queue, which
 * keeps at most one classifier call in flight (AI-02). Each answer is checked by {@link QuoteValidator} (AI-08) and only
 * the valid hits reach the call, as source llm; invalid hits stay in the result for the audit. The AI package itself
 * knows nothing of risk or calls (BE-05): this class is the seam.
 *
 * <p>Every finished call, answered or failed, is reported to the {@link AiCallObserver} (the audit, OBS-03) with the
 * risk level before and after and the keyword hits for the same segments. A result that arrives after the call ended
 * is reported too, but its hits are dropped.
 *
 * <p>Failures are never silent (rule 7): after three failed calls in a row {@code system.status} for {@code ai} turns
 * degraded, and the first success restores ok (OBS-02). The keyword layer works regardless. Only numbers are logged
 * (OBS-05): the answer can contain quotes from the call.
 */
@Component
public class AiAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(AiAnalyzer.class);
    private static final int FAILURES_BEFORE_DEGRADED = 3;

    private final CallService calls;
    private final StageClassifier classifier;
    private final EventBus eventBus;
    private final Clock clock;
    private final ExecutorService executor;
    private final AiCallObserver observer;
    private final AiHealth health;

    private final ReentrantLock lock = new ReentrantLock();
    private CallClassificationQueue queue;
    private CallState queueCall;
    private int consecutiveFailures;
    private boolean degraded;

    @Autowired
    public AiAnalyzer(CallService calls, StageClassifier classifier, EventBus eventBus, Clock clock,
            @Qualifier("aiExecutor") ExecutorService executor, AiCallObserver observer, AiHealth health) {
        this.calls = calls;
        this.classifier = classifier;
        this.eventBus = eventBus;
        this.clock = clock;
        this.executor = executor;
        this.observer = observer;
        this.health = health;
    }

    @EventListener
    public void onFinalSegment(FinalSegmentAdded event) {
        queueFor(event.call()).segmentAdded();
    }

    /**
     * One queue per call; a new call gets a fresh one, the old one finishes by itself. Runs while the call lock is held
     * (the event is published under it), so it must not look the call up through {@link CallService#active()}: that
     * takes the slot lock, and the slot lock is always taken before a call lock, never after (CC-01).
     */
    private CallClassificationQueue queueFor(CallState call) {
        lock.lock();
        try {
            if (queue == null || queueCall == null || !call.callId().equals(queueCall.callId())) {
                queueCall = call;
                queue = new CallClassificationQueue(classifier, () -> snapshot(call),
                        result -> handle(call, result), executor);
            }
            return queue;
        } finally {
            lock.unlock();
        }
    }

    private Optional<CallSnapshot> snapshot(CallState call) {
        return activeCall(call.callId()).map(c -> new CallSnapshot(c.callId(), c.mode(), c.scenarioId(), c.transcript()));
    }

    private Optional<CallState> activeCall(String callId) {
        return calls.active().filter(call -> call.callId().equals(callId));
    }

    private void handle(CallState call, ClassifierResult result) {
        ClassifierResult finished = result;
        int valid = 0;
        RiskLevel before = call.level();
        boolean late = activeCall(call.callId()).isEmpty();
        try {
            if (!late) {
                if (result.failed()) {
                    health.failed();
                    recordFailure(call);
                } else {
                    List<StageHit> checked = QuoteValidator.validate(result.hits(), call.transcript());
                    finished = result.withHits(checked);
                    // FAMILY_KEYWORD is the backend's own stage: whatever the model says about it is ignored.
                    List<StageHit> accepted = checked.stream().filter(StageHit::validated)
                            .filter(h -> h.stage() != pl.aniolstroz.contracts.StageId.FAMILY_KEYWORD).toList();
                    valid = accepted.size();
                    health.succeeded();
                    recordSuccess(call);
                    addHits(call.callId(), accepted);
                }
            }
            logResult(call.callId(), finished, valid, late);
        } finally {
            report(new AiCallReport(call.callId(), call.mode(), finished, before, call.level(),
                    keywordHitsIn(call, finished.segmentRange()), late));
        }
    }

    /** The observer must not break the queue: whatever it throws is only logged, by type. */
    private void report(AiCallReport report) {
        try {
            observer.onAiCall(report);
        } catch (RuntimeException e) {
            log.error("AI call observer failed: {}", e.getClass().getName());
        }
    }

    /** The keyword hits of the segments the model saw, for a side-by-side comparison in the audit. */
    private static List<StageHit> keywordHitsIn(CallState call, String segmentRange) {
        int[] range = parseRange(segmentRange);
        if (range == null) {
            return List.of();
        }
        return call.hits().stream()
                .filter(hit -> hit.source() == HitSource.KEYWORDS)
                .filter(hit -> {
                    int n = segmentNumber(hit.segId());
                    return n >= range[0] && n <= range[1];
                })
                .toList();
    }

    private static int[] parseRange(String range) {
        if (range == null) {
            return null;
        }
        String[] parts = range.split("-");
        if (parts.length != 2) {
            return null;
        }
        int from = segmentNumber(parts[0]);
        int to = segmentNumber(parts[1]);
        return from < 0 || to < 0 ? null : new int[] {from, to};
    }

    private static int segmentNumber(String segId) {
        try {
            return segId.startsWith("s") ? Integer.parseInt(segId.substring(1)) : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void addHits(String callId, List<StageHit> hits) {
        try {
            calls.addHits(callId, hits);
        } catch (NoActiveCallException e) {
            // The call ended while the model was thinking: the answer is of no use any more.
        }
    }

    private void recordFailure(CallState call) {
        lock.lock();
        try {
            consecutiveFailures++;
            if (consecutiveFailures == FAILURES_BEFORE_DEGRADED && !degraded) {
                degraded = true;
                publishStatus(call, ComponentState.DEGRADED,
                        "Analiza AI jest niedostępna. Działa podstawowa ochrona (słowa kluczowe).");
            }
        } finally {
            lock.unlock();
        }
    }

    private void recordSuccess(CallState call) {
        lock.lock();
        try {
            consecutiveFailures = 0;
            if (degraded) {
                degraded = false;
                publishStatus(call, ComponentState.OK, "Analiza AI działa.");
            }
        } finally {
            lock.unlock();
        }
    }

    private void publishStatus(CallState call, ComponentState state, String message) {
        eventBus.publish(new SystemStatusEvent(call.mode(), clock.instant(), new SystemStatus(
                pl.aniolstroz.contracts.Component.AI, state, message, clock.instant())));
    }

    private static void logResult(String callId, ClassifierResult r, int valid, boolean late) {
        ClassifierResult.Usage usage = r.usage();
        log.info("AI call finished: callId={} range={} model={} effort={} stopReason={} inputTokens={} "
                        + "cacheReadInputTokens={} outputTokens={} latencyMs={} hits={} valid={} late={} error={}",
                callId, r.segmentRange(), r.model(), r.effort(), r.stopReason(), usage.inputTokens(),
                usage.cacheReadInputTokens(), usage.outputTokens(), r.latencyMs(), r.hits().size(), valid, late, r.error());
    }
}
