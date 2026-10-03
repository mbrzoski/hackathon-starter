package pl.aniolstroz.call;

import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import pl.aniolstroz.alerts.AlertFactory;
import pl.aniolstroz.alerts.AlertTemplates;
import pl.aniolstroz.contracts.Sensitivity;
import pl.aniolstroz.events.EventBus;
import pl.aniolstroz.risk.KeywordDetector;
import pl.aniolstroz.risk.SensitivitySource;

/** Test helper: a {@link CallService} wired with the real keyword detector and bundled alert templates. */
public final class CallServices {

    private CallServices() {
    }

    public static CallService create(EventBus bus, Clock clock, CallEndedHook hook) {
        return create(bus, clock, hook, () -> Sensitivity.STANDARD);
    }

    public static CallService create(EventBus bus, Clock clock, CallEndedHook hook, SensitivitySource sensitivity) {
        return create(bus, clock, hook, sensitivity, event -> { });
    }

    public static CallService create(EventBus bus, Clock clock, CallEndedHook hook, SensitivitySource sensitivity,
            ApplicationEventPublisher publisher) {
        return new CallService(bus, clock, hook, KeywordDetector.bundled(), sensitivity,
                new AlertFactory(AlertTemplates.bundled(), clock), publisher);
    }
}
