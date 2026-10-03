package pl.aniolstroz.stt;

import pl.aniolstroz.contracts.Component;
import pl.aniolstroz.contracts.ComponentState;

/** Where the audio path reports its health (OBS-01). */
@FunctionalInterface
public interface StatusPublisher {

    void publish(Component component, ComponentState state, String message);
}
