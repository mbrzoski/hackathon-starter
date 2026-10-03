package pl.aniolstroz.events;

import java.time.Duration;
import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pl.aniolstroz.config.AppProperties;
import pl.aniolstroz.contracts.Component;
import pl.aniolstroz.contracts.ComponentState;
import pl.aniolstroz.contracts.EventEnvelope.SystemStatusEvent;
import pl.aniolstroz.contracts.SystemStatus;

/**
 * Publishes {@code system.status} for the backend itself every few seconds; its absence tells the UI the backend is
 * gone. A failure reported by another part of the backend (a lost alert, an audit or retention failure) is not
 * overwritten by the next beat (F-03, rule 7): for {@link #FAILURE_HOLD} the beat repeats that failure, so it stays on
 * the screens and still proves the backend is alive; after that the backend is reported ok again.
 */
@Service
class HeartbeatService {

    /** How long a reported backend failure stays on the screens before the backend counts as ok again. */
    static final Duration FAILURE_HOLD = Duration.ofMinutes(5);

    private final EventBus eventBus;
    private final AppProperties properties;
    private final Clock clock;

    HeartbeatService(EventBus eventBus, AppProperties properties, Clock clock) {
        this.eventBus = eventBus;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(
            fixedRateString = "${app.events.heartbeat-interval-ms:10000}",
            initialDelayString = "${app.events.heartbeat-interval-ms:10000}")
    void beat() {
        var now = clock.instant();
        var failure = eventBus.lastStatus(Component.BACKEND)
                .filter(s -> s.state() != ComponentState.OK)
                .filter(s -> Duration.between(s.at(), now).compareTo(FAILURE_HOLD) < 0);
        // The repeated failure keeps its own time, so the hold runs out instead of renewing itself.
        var status = failure.orElse(new SystemStatus(Component.BACKEND, ComponentState.OK, "Backend działa.", now));
        eventBus.publish(new SystemStatusEvent(eventBus.modeOrDefault(properties.mode()), now, status));
    }
}
