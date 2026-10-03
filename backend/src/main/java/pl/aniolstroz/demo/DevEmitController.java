package pl.aniolstroz.demo;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pl.aniolstroz.contracts.EventEnvelope;
import pl.aniolstroz.contracts.api.DevApi;
import pl.aniolstroz.events.EventBus;

/**
 * Injects an arbitrary event into /ws/events. Exists only in the dev profile (API-05). The interface is generated
 * from contracts/openapi.yaml, the body is validated by Bean Validation on the event records.
 */
@Profile("dev")
@RestController
class DevEmitController implements DevApi {

    private final EventBus eventBus;

    DevEmitController(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public ResponseEntity<Void> emitDevEvent(EventEnvelope eventEnvelope) {
        eventBus.publish(eventEnvelope);
        return ResponseEntity.accepted().build();
    }
}
