package pl.aniolstroz.call;

import java.time.Instant;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.RiskLevel;

/** Published when a call ended, after the call-ended hook has decided what happens to the transcript. */
public record CallClosed(String callId, Mode mode, Instant at, RiskLevel maxLevel, boolean hadAlert) {
}
