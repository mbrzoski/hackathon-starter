package pl.aniolstroz.contracts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record Decision(
        @NotBlank String alertId,
        @NotNull Actor actor,
        @NotNull DecisionType decision,
        @NotNull Instant at) {
}
