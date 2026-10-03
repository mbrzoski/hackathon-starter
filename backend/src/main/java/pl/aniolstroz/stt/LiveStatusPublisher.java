package pl.aniolstroz.stt;

import java.time.Clock;
import org.springframework.stereotype.Component;
import pl.aniolstroz.contracts.ComponentState;
import pl.aniolstroz.contracts.EventEnvelope.SystemStatusEvent;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.SystemStatus;
import pl.aniolstroz.events.EventBus;

/** Publishes {@code system.status} for the audio path. Only LIVE calls have one, so the mode is LIVE (rule 6). */
@Component
class LiveStatusPublisher implements StatusPublisher {

    private final EventBus eventBus;
    private final Clock clock;

    LiveStatusPublisher(EventBus eventBus, Clock clock) {
        this.eventBus = eventBus;
        this.clock = clock;
    }

    @Override
    public void publish(pl.aniolstroz.contracts.Component component, ComponentState state, String message) {
        var now = clock.instant();
        eventBus.publish(new SystemStatusEvent(Mode.LIVE, now, new SystemStatus(component, state, message, now)));
    }
}
