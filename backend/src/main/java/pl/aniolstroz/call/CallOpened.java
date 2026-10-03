package pl.aniolstroz.call;

import java.time.Instant;
import pl.aniolstroz.contracts.Mode;

/** Published when a call starts. Listeners must return quickly: nothing is locked, but the caller waits. */
public record CallOpened(String callId, Mode mode, String scenarioId, Instant at) {
}
