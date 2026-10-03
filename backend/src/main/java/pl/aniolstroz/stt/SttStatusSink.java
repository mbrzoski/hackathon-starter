package pl.aniolstroz.stt;

import pl.aniolstroz.contracts.ComponentState;

/** Lets a provider report its own health (for example a full frame queue) without knowing the event bus. */
@FunctionalInterface
public interface SttStatusSink {

    void onStatus(ComponentState state, String message);
}
