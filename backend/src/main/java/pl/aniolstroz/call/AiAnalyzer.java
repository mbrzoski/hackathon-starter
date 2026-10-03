package pl.aniolstroz.call;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
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
    /** Receives every finished result. The audit (BE-07) will plug in here. */
    private final Consumer<ClassifierResult> resultListener;

    private final ReentrantLock lock = new ReentrantLock();
    private CallClassificationQueue queue;
    private String queueCallId;
    private int consecutiveFailures;
    private boolean degraded;

    @Autowired
    public AiAnalyzer(CallService calls, StageClassifier classifier, EventBus eventBus, Clock clock,
            @Qualifier("aiExecutor") ExecutorService executor) {
        this(calls, classifier, eventBus, clock, executor, result -> { });
    }

    public AiAnalyzer(CallService calls, StageClassifier classifier, EventBus eventBus, Clock clock,
            ExecutorService executor, Consumer<ClassifierResult> resultListener) {
        this.calls = calls;
        this.classifier = classifier;
        this.eventBus = eventBus;
        this.clock = clock;
        this.executor = executor;
        this.resultListener = resultListener;
    }

    @EventListener
    public void onFinalSegment(FinalSegmentAdded event) {
        queueFor(event.callId()).segmentAdded();
    }

    /** One queue per call; a new call gets a fresh one, the old one finishes by itself. */
    private CallClassificationQueue queueFor(String callId) {
        lock.lock();
        try {
            if (queue == null || !callId.equals(queueCallId)) {
                queueCallId = callId;
                queue = new CallClassificationQueue(classifier, () -> snapshot(callId),
                        result -> handle(callId, result), executor);
            }
            return queue;
        } finally {
            lock.unlock();
        }
    }

    private Optional<CallSnapshot> snapshot(String callId) {
        return activeCall(callId).map(call -> new CallSnapshot(call.callId(), call.mode(), call.scenarioId(),
                call.transcript()));
    }

    private Optional<CallState> activeCall(String callId) {
        return calls.active().filter(call -> call.callId().equals(callId));
    }

    private void handle(String callId, ClassifierResult result) {
        ClassifierResult finished = result;
        int valid = 0;
        try {
            Optional<CallState> call = activeCall(callId);
            if (call.isPresent()) {
                if (result.failed()) {
                    recordFailure(call.get());
                } else {
                    List<StageHit> checked = QuoteValidator.validate(result.hits(), call.get().transcript());
                    finished = result.withHits(checked);
                    List<StageHit> accepted = checked.stream().filter(StageHit::validated).toList();
                    valid = accepted.size();
                    recordSuccess(call.get());
                    addHits(callId, accepted);
                }
            }
            logResult(callId, finished, valid);
        } finally {
            resultListener.accept(finished);
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

    private static void logResult(String callId, ClassifierResult r, int valid) {
        ClassifierResult.Usage usage = r.usage();
        log.info("AI call finished: callId={} range={} model={} effort={} stopReason={} inputTokens={} "
                        + "cacheReadInputTokens={} outputTokens={} latencyMs={} hits={} valid={} error={}",
                callId, r.segmentRange(), r.model(), r.effort(), r.stopReason(), usage.inputTokens(),
                usage.cacheReadInputTokens(), usage.outputTokens(), r.latencyMs(), r.hits().size(), valid, r.error());
    }
}
