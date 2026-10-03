package pl.aniolstroz.contracts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/** Alert with texts taken from a pre-written template; {@code stages} holds validated hits only. */
public record Alert(
        @NotBlank String alertId,
        @NotBlank String callId,
        @NotNull RiskLevel level,
        @NotNull List<StageHit> stages,
        @NotBlank String templateId,
        @NotBlank String shortText,
        @NotBlank String advice,
        @NotNull TriggeredBy triggeredBy,
        @NotNull Instant createdAt,
        @NotNull Mode mode) {
}
