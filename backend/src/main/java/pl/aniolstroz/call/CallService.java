package pl.aniolstroz.call;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;
import pl.aniolstroz.contracts.CallEnded;
import pl.aniolstroz.contracts.CallStarted;
import pl.aniolstroz.contracts.EventEnvelope.CallEndedEvent;
import pl.aniolstroz.contracts.EventEnvelope.CallStartedEvent;
import pl.aniolstroz.contracts.EventEnvelope.TranscriptSegmentEvent;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.TranscriptSegment;
import pl.aniolstroz.events.EventBus;

/**
 * Owns the single active call (BE-04). Events of one call are published under that call's lock, so subscribers
 * see segments in id order. Transcript text is never logged.
 */
@Service
public class CallService {

    private final EventBus eventBus;
    private final Clock clock;
    private final CallEndedHook endedHook;

    /**
     * Guards {@link #active}. Lock order is always slot lock, then call lock; code holding a call lock never
     * takes the slot lock, so the two cannot deadlock.
     */
    private final ReentrantLock slotLock = new ReentrantLock();
    private CallState active;

    public CallService(EventBus eventBus, Clock clock, CallEndedHook endedHook) {
        this.eventBus = eventBus;
        this.clock = clock;
        this.endedHook = endedHook;
    }

    public CallState start(Mode mode) {
        slotLock.lock();
        try {
            if (active != null) {
                throw new CallAlreadyActiveException();
            }
            CallState call = new CallState(UUID.randomUUID().toString(), mode, clock.instant());
            active = call;
            eventBus.publish(new CallStartedEvent(mode, clock.instant(), new CallStarted(call.callId())));
            return call;
        } finally {
            slotLock.unlock();
        }
    }

    /**
     * Gives the segment the next id (s1, s2, ...) in the call named by {@code segment.callId()}, keeps it if it is
     * final and publishes it. The incoming segId is ignored.
     *
     * @throws NoActiveCallException if that call is not the active one
     */
    public TranscriptSegment addSegment(TranscriptSegment segment) {
        CallState call = activeCall(segment.callId());
        call.lock().lock();
        try {
            if (call.isEnded()) {
                throw new NoActiveCallException();
            }
            TranscriptSegment numbered = new TranscriptSegment(call.callId(), call.nextSegmentId(),
                    segment.tStartMs(), segment.tEndMs(), segment.text(), segment.isFinal(),
                    segment.speaker(), segment.sttConfidence());
            if (numbered.isFinal()) {
                call.keep(numbered);
            }
            eventBus.publish(new TranscriptSegmentEvent(call.mode(), clock.instant(), numbered));
            return numbered;
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

    public Optional<CallState> active() {
        slotLock.lock();
        try {
            return Optional.ofNullable(active);
        } finally {
            slotLock.unlock();
        }
    }

    private CallState activeCall(String callId) {
        CallState call = active().orElseThrow(NoActiveCallException::new);
        if (!call.callId().equals(callId)) {
            throw new NoActiveCallException();
        }
        return call;
    }

    /** Caller holds the slot lock. */
    private boolean endLocked(CallState call) {
        call.lock().lock();
        try {
            call.markEnded();
            endedHook.onCallEnded(call);
            eventBus.publish(new CallEndedEvent(call.mode(), clock.instant(),
                    new CallEnded(call.callId(), call.hadAlert())));
        } finally {
            call.lock().unlock();
        }
        active = null;
        return true;
    }
}
