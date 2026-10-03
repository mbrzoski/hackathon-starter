package pl.aniolstroz.call;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import pl.aniolstroz.alerts.AlertStore;
import pl.aniolstroz.alerts.TranscriptExcerpt;
import pl.aniolstroz.config.Trace;
import pl.aniolstroz.contracts.Alert;
import pl.aniolstroz.contracts.ComponentState;
import pl.aniolstroz.contracts.EventEnvelope.SystemStatusEvent;
import pl.aniolstroz.contracts.StageHit;
import pl.aniolstroz.contracts.SystemStatus;
import pl.aniolstroz.contracts.TranscriptSegment;
import pl.aniolstroz.events.EventBus;

/**
 * DAT-01 when a call ends: a call without an alert is deleted completely; a call with alerts keeps, per alert, the
 * cited segments plus two segments of context on each side, in memory and in SQLite. Everything else is deleted.
 */
@Component
public class RetainAlertedCallHook implements CallEndedHook {

    static final int CONTEXT_SEGMENTS = 2;

    private static final Logger log = LoggerFactory.getLogger(RetainAlertedCallHook.class);

    private final AlertStore store;
    private final EventBus eventBus;
    private final Clock clock;
    private final DiscardTranscriptHook discard = new DiscardTranscriptHook();

    public RetainAlertedCallHook(AlertStore store, EventBus eventBus, Clock clock) {
        this.store = store;
        this.eventBus = eventBus;
        this.clock = clock;
    }

    @Override
    public void onCallEnded(CallState state) {
        List<Alert> alerts = state.alerts();
        if (alerts.isEmpty()) {
            Trace.flow("retention | call={} had no alert: its transcript ({} segments) is discarded (DAT-01)", Trace.id(state.callId()),
                    state.transcript().size());
            discard.onCallEnded(state);
            return;
        }
        List<TranscriptSegment> transcript = state.transcript();
        Set<String> keptIds = new java.util.HashSet<>();
        boolean stored = true;
        for (Alert alert : alerts) {
            List<TranscriptSegment> excerpt = TranscriptExcerpt.select(transcript, citedBy(alert), CONTEXT_SEGMENTS);
            excerpt.forEach(s -> keptIds.add(s.segId()));
            stored &= save(state, alert, excerpt);
        }
        state.retainTranscript(transcript.stream().filter(s -> keptIds.contains(s.segId())).toList());
        Trace.flow("retention | call={} had {} alert(s): {} of {} segments kept (cited segments and their context), stored={}",
                Trace.id(state.callId()), alerts.size(), keptIds.size(), transcript.size(), stored);
        if (!stored) {
            reportStorageFailure(state);
        }
    }

    private boolean save(CallState state, Alert alert, List<TranscriptSegment> excerpt) {
        try {
            store.save(alert, excerpt);
            return true;
        } catch (DataAccessException e) {
            // Type only: the message may contain SQL parameters with transcript text.
            log.error("Cannot store alert of call {}: {}", state.callId(), e.getClass().getName());
            return false;
        }
    }

    private void reportStorageFailure(CallState state) {
        eventBus.publish(new SystemStatusEvent(state.mode(), clock.instant(), new SystemStatus(
                pl.aniolstroz.contracts.Component.BACKEND, ComponentState.DEGRADED,
                "Nie udało się zapisać alertu z rozmowy. Ostrzeżenie było widoczne, ale może nie zostać zachowane.",
                clock.instant())));
    }

    private static Set<String> citedBy(Alert alert) {
        return alert.stages().stream().map(StageHit::segId).collect(Collectors.toSet());
    }
}
