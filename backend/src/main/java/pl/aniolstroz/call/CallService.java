package pl.aniolstroz.call;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import pl.aniolstroz.alerts.AlertFactory;
import pl.aniolstroz.alerts.LiveCallAccess;
import pl.aniolstroz.contracts.Alert;
import pl.aniolstroz.contracts.CallEnded;
import pl.aniolstroz.contracts.CallStarted;
import pl.aniolstroz.contracts.Component;
import pl.aniolstroz.contracts.ComponentState;
import pl.aniolstroz.contracts.EventEnvelope.AlertCreatedEvent;
import pl.aniolstroz.contracts.EventEnvelope.CallEndedEvent;
import pl.aniolstroz.contracts.EventEnvelope.CallStartedEvent;
import pl.aniolstroz.contracts.EventEnvelope.RiskUpdateEvent;
import pl.aniolstroz.contracts.EventEnvelope.SystemStatusEvent;
import pl.aniolstroz.contracts.EventEnvelope.TranscriptSegmentEvent;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.RiskLevel;
import pl.aniolstroz.contracts.RiskUpdate;
import pl.aniolstroz.contracts.StageHit;
import pl.aniolstroz.contracts.StageId;
import pl.aniolstroz.contracts.SystemStatus;
import pl.aniolstroz.contracts.TranscriptSegment;
import pl.aniolstroz.events.EventBus;
import pl.aniolstroz.risk.KeywordDetector;
import pl.aniolstroz.risk.RiskAssessment;
import pl.aniolstroz.risk.RiskEngine;
import pl.aniolstroz.risk.SensitivitySource;

/**
 * Owns the single active call (BE-04) and ties together its segments, stage hits, deterministic risk and alerts.
 * Events of one call are published under that call's lock, so subscribers see them in order. Transcript text is
 * never logged.
 */
@Service
public class CallService implements LiveCallAccess {

    private static final Logger log = LoggerFactory.getLogger(CallService.class);

    private final EventBus eventBus;
    private final Clock clock;
    private final CallEndedHook endedHook;
    private final KeywordDetector keywordDetector;
    private final SensitivitySource sensitivity;
    private final AlertFactory alertFactory;
    private final ApplicationEventPublisher publisher;

    /**
     * Guards writes to {@link #active}. Lock order is always slot lock, then call lock; code holding a call lock never
     * takes the slot lock, so the two cannot deadlock. For that reason {@link #active} is volatile and
     * {@link #active()} reads it without a lock: the listeners of a segment (the AI layer) call it while the call is
     * locked, and a lock there deadlocked with {@link #end()}.
     */
    private final ReentrantLock slotLock = new ReentrantLock();
    private volatile CallState active;

    public CallService(EventBus eventBus, Clock clock, CallEndedHook endedHook, KeywordDetector keywordDetector,
            SensitivitySource sensitivity, AlertFactory alertFactory,
            ApplicationEventPublisher publisher) {
        this.eventBus = eventBus;
        this.clock = clock;
        this.endedHook = endedHook;
        this.keywordDetector = keywordDetector;
        this.sensitivity = sensitivity;
        this.alertFactory = alertFactory;
        this.publisher = publisher;
    }

    public CallState start(Mode mode) {
        return start(mode, null);
    }

    /** @param scenarioId the demo scenario the call plays, or null; the mock AI answers by it */
    public CallState start(Mode mode, String scenarioId) {
        slotLock.lock();
        try {
            if (active != null) {
                throw new CallAlreadyActiveException();
            }
            CallState call = new CallState(UUID.randomUUID().toString(), mode, scenarioId, clock.instant());
            active = call;
            eventBus.publish(new CallStartedEvent(mode, clock.instant(), new CallStarted(call.callId())));
            announce(new CallOpened(call.callId(), mode, scenarioId, call.startedAt()));
            return call;
        } finally {
            slotLock.unlock();
        }
    }

    /**
     * Gives the segment its id in the call named by {@code segment.callId()} (s1, s2, ... for final segments; an
     * interim segment gets the id of the final one that follows), keeps it if it is final, publishes it and runs the
     * keyword detector on it (DET-03). The incoming segId is ignored.
     *
     * @throws NoActiveCallException if that call is not the active one
     */
    public TranscriptSegment addSegment(TranscriptSegment segment) {
        CallState call = activeCall(segment.callId());
        call.lock().lock();
        try {
            ensureOpen(call);
            TranscriptSegment numbered = new TranscriptSegment(call.callId(), call.segmentIdFor(segment.isFinal()),
                    segment.tStartMs(), segment.tEndMs(), segment.text(), segment.isFinal(),
                    segment.speaker(), segment.sttConfidence());
            if (numbered.isFinal()) {
                call.keep(numbered);
            }
            eventBus.publish(new TranscriptSegmentEvent(call.mode(), clock.instant(), numbered));
            applyHits(call, keywordDetector.detect(numbered));
            if (numbered.isFinal()) {
                announce(call);
            }
            return numbered;
        } finally {
            call.lock().unlock();
        }
    }

    /**
     * Adds stage hits (for example from the AI layer) to the call. New hits publish risk.update and may raise
     * the level and create an alert.
     *
     * @throws NoActiveCallException if that call is not the active one
     */
    public void addHits(String callId, List<StageHit> hits) {
        CallState call = activeCall(callId);
        call.lock().lock();
        try {
            ensureOpen(call);
            applyHits(call, hits);
        } finally {
            call.lock().unlock();
        }
    }

    /** Ends the active call. Returns false if there was none. */
    public boolean end() {
        slotLock.lock();
        try {
            return active != null && endLocked(active);
        } finally {
            slotLock.unlock();
        }
    }

    /** Ends the call only if it is still the active one, so a late caller cannot end a newer call. */
    public boolean end(String callId) {
        slotLock.lock();
        try {
            return active != null && active.callId().equals(callId) && endLocked(active);
        } finally {
            slotLock.unlock();
        }
    }

    /** Lock-free on purpose, see {@link #slotLock}. */
    public Optional<CallState> active() {
        return Optional.ofNullable(active);
    }

    private CallState activeCall(String callId) {
        CallState call = active().orElseThrow(NoActiveCallException::new);
        if (!call.callId().equals(callId)) {
            throw new NoActiveCallException();
        }
        return call;
    }

    private static void ensureOpen(CallState call) {
        if (call.isEnded()) {
            throw new NoActiveCallException();
        }
    }

    /**
     * Stores the new hits, then publishes risk.update, then (when the level rose to MEDIUM or HIGH) the one alert for
     * that level, or another alert when a more specific template applies at the same level (D-05). The level never drops
     * (DET-05). Caller holds the call lock.
     */
    private void applyHits(CallState call, List<StageHit> hits) {
        boolean changed = false;
        for (StageHit hit : hits) {
            changed |= call.addHit(hit);
        }
        if (!changed) {
            return;
        }
        List<StageHit> considered = withoutIgnoredStages(call);
        RiskAssessment assessment = RiskEngine.computeLevel(considered, sensitivity.current());
        RiskLevel previous = call.level();
        RiskLevel level = assessment.level().compareTo(previous) > 0 ? assessment.level() : previous;
        call.setLevel(level);

        List<StageHit> counted = RiskEngine.countedHits(considered);
        publishRiskUpdate(call, level, previous, counted);

        // One alert per level and call, also when the level fell (ignored stage) and rises again. Exception (D-05): at
        // the same level a more specific template (for example the payment channel appearing after authority and
        // money) raises one more alert, so the text read to the senior names the most important stage.
        boolean rose = level.compareTo(previous) > 0 && !call.hasAlertAt(level);
        boolean sharper = level == previous && call.hasAlertAt(level)
                && alertFactory.templateId(level, counted).filter(id -> !call.hasAlertWithTemplate(level, id)).isPresent();
        if (level.compareTo(RiskLevel.MEDIUM) >= 0 && (rose || sharper)) {
            Alert alert = alertFactory.create(call.callId(), call.mode(), level, counted, assessment.triggeredBy());
            call.recordAlert(alert);
            eventBus.publish(new AlertCreatedEvent(call.mode(), clock.instant(), alert));
        }
    }

    /** Tells the AI layer about a new final segment. A failing listener must not break the call. */
    private void announce(CallState call) {
        announce(new FinalSegmentAdded(call));
    }

    /** Tells the listeners (AI layer, audit) about something that happened. A failing listener must not break a call. */
    private void announce(Object event) {
        try {
            publisher.publishEvent(event);
        } catch (RuntimeException e) {
            log.error("Listener of {} failed: {}", event.getClass().getSimpleName(), e.getClass().getName());
        }
    }

    /** Alerts of the active call, for the decision endpoints. Empty if no call is active. */
    @Override
    public List<Alert> alerts() {
        return active().map(CallState::alerts).orElse(List.of());
    }

    /**
     * The family's "don't count this stage": stops counting the stages for this call only, recomputes the level
     * without them and publishes risk.update.
     *
     * <p>This is the one exception to DET-05 (the level only grows) besides a change of sensitivity: the recomputed
     * level replaces the current one even if it is lower. Alerts already raised stay as they are. Stages ignored here
     * stay ignored until the call ends.
     *
     * @return false if the call is not the active one
     */
    @Override
    public boolean ignoreStages(String callId, Set<StageId> stages) {
        CallState call = active().filter(c -> c.callId().equals(callId)).orElse(null);
        if (call == null) {
            return false;
        }
        call.lock().lock();
        try {
            if (call.isEnded()) {
                return false;
            }
            if (!call.addIgnoredStages(stages)) {
                return true;
            }
            List<StageHit> considered = withoutIgnoredStages(call);
            RiskLevel previous = call.level();
            RiskLevel level = RiskEngine.computeLevel(considered, sensitivity.current()).level();
            call.setLevel(level);
            publishRiskUpdate(call, level, previous, RiskEngine.countedHits(considered));
            return true;
        } finally {
            call.lock().unlock();
        }
    }

    private static List<StageHit> withoutIgnoredStages(CallState call) {
        Set<StageId> ignored = call.ignoredStages();
        return call.hits().stream().filter(h -> !ignored.contains(h.stage())).toList();
    }

    private void publishRiskUpdate(CallState call, RiskLevel level, RiskLevel previous, List<StageHit> counted) {
        Set<StageId> stages = RiskEngine.stagesOf(counted);
        eventBus.publish(new RiskUpdateEvent(call.mode(), clock.instant(),
                new RiskUpdate(call.callId(), level, previous, List.copyOf(stages), stages.size())));
    }

    /** Caller holds the slot lock. A failing hook must not keep the call alive or hide that the call ended. */
    private boolean endLocked(CallState call) {
        call.lock().lock();
        try {
            call.markEnded();
            try {
                endedHook.onCallEnded(call);
            } catch (RuntimeException e) {
                // Log the type only: messages may carry transcript text. Fail closed: drop the transcript.
                log.error("Call-ended hook failed: {}", e.getClass().getName());
                call.clearTranscript();
                eventBus.publish(new SystemStatusEvent(call.mode(), clock.instant(), new SystemStatus(
                        Component.BACKEND, ComponentState.DEGRADED,
                        "Nie udało się zapisać alertu z rozmowy. Ostrzeżenie było widoczne, ale nie zostanie zachowane.",
                        clock.instant())));
            }
            eventBus.publish(new CallEndedEvent(call.mode(), clock.instant(),
                    new CallEnded(call.callId(), call.hadAlert())));
            announce(new CallClosed(call.callId(), call.mode(), clock.instant(), call.maxLevel(), call.hadAlert()));
        } finally {
            call.lock().unlock();
            active = null;
        }
        return true;
    }
}
