package pl.aniolstroz.events;

import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pl.aniolstroz.config.AppProperties;
import pl.aniolstroz.contracts.Component;
import pl.aniolstroz.contracts.ComponentState;
import pl.aniolstroz.contracts.EventEnvelope.SystemStatusEvent;
import pl.aniolstroz.contracts.SystemStatus;

/** Publishes {@code system.status} for the backend itself; its absence tells the UI the backend is gone. */
@Service
class HeartbeatService {

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
        var status = new SystemStatus(Component.BACKEND, ComponentState.OK, "Backend działa.", now);
        eventBus.publish(new SystemStatusEvent(eventBus.modeOrDefault(properties.mode()), now, status));
    }
}
