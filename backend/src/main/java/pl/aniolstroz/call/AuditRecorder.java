package pl.aniolstroz.call;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pl.aniolstroz.audit.AuditEntry;
import pl.aniolstroz.audit.AuditService;
import pl.aniolstroz.contracts.ComponentState;
import pl.aniolstroz.contracts.EventEnvelope.SystemStatusEvent;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.SystemStatus;
import pl.aniolstroz.events.EventBus;

/**
 * The seam between calls and the audit (the audit package knows nothing of calls): turns lifecycle events and AI call
 * reports into audit writes. The audit must never break a call, so every failure is caught here; it is reported once as
 * {@code system.status} (rule 7) and only its type is logged, because a message could carry call text (OBS-05).
 */
@Component
public class AuditRecorder implements AiCallObserver {

    private static final Logger log = LoggerFactory.getLogger(AuditRecorder.class);

    private final AuditService audit;
    private final EventBus eventBus;
    private final Clock clock;
    private final AtomicBoolean failing = new AtomicBoolean();

    public AuditRecorder(AuditService audit, EventBus eventBus, Clock clock) {
        this.audit = audit;
        this.eventBus = eventBus;
        this.clock = clock;
    }

    /** Closes calls that a crash left open and removes their text, so it does not stay on disk. */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            int closed = audit.closeOrphanedCalls();
            if (closed > 0) {
                log.warn("Closed {} call(s) left open by a previous run and removed their audit text", closed);
            }
        } catch (RuntimeException e) {
            log.error("Cannot close orphaned calls in the audit: {}", e.getClass().getName());
        }
    }

    @EventListener
    public void onCallOpened(CallOpened event) {
        guarded(event.mode(), () -> audit.callStarted(event.callId(), event.mode(), event.at()));
    }

    @EventListener
    public void onCallClosed(CallClosed event) {
        guarded(event.mode(), () -> audit.callEnded(event.callId(), event.at(), event.maxLevel(), event.hadAlert()));
    }

    @Override
    public void onAiCall(AiCallReport report) {
        guarded(report.mode(), () -> audit.record(new AuditEntry(report.callId(), report.mode(), report.result(),
                report.levelBefore(), report.levelAfter(), report.keywordHits(), report.late())));
    }

    private void guarded(Mode mode, Runnable write) {
        try {
            write.run();
            failing.set(false);
        } catch (RuntimeException e) {
            log.error("Audit write failed: {}", e.getClass().getName());
            if (failing.compareAndSet(false, true)) {
                eventBus.publish(new SystemStatusEvent(mode, clock.instant(), new SystemStatus(
                        pl.aniolstroz.contracts.Component.BACKEND, ComponentState.DEGRADED,
                        "Nie udało się zapisać dziennika audytu. Ochrona działa, ale dziennik wywołań AI może być niepełny.",
                        clock.instant())));
            }
        }
    }
}
