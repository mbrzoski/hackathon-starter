package pl.aniolstroz.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.RiskLevel;

/**
 * A call as the audit knows it. {@code endedAt} is null while the call is active; then {@code maxLevel} is the highest
 * level seen by the AI calls so far.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CallSummary(String callId, Mode mode, Instant startedAt, Instant endedAt, RiskLevel maxLevel,
        int aiCalls) {
}
