package pl.aniolstroz.risk;

import pl.aniolstroz.contracts.RiskLevel;
import pl.aniolstroz.contracts.TriggeredBy;

/** Result of {@link RiskEngine#computeLevel}. {@code triggeredBy} is null when no hit counts (level NONE). */
public record RiskAssessment(RiskLevel level, TriggeredBy triggeredBy) {
}
