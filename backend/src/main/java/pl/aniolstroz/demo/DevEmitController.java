package pl.aniolstroz.demo;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.aniolstroz.contracts.EventEnvelope;
import pl.aniolstroz.events.EventBus;

/** Injects an arbitrary event into /ws/events. Exists only in the dev profile (API-05). */
@Profile("dev")
@RestController
@RequestMapping("/api/dev")
class DevEmitController {

    private final EventBus eventBus;

    DevEmitController(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @PostMapping("/emit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void emit(@Valid @RequestBody EventEnvelope event) {
        eventBus.publish(event);
    }
}
