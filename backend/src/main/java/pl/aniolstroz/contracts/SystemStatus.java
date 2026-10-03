package pl.aniolstroz.contracts;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record SystemStatus(
        @NotNull Component component,
        @NotNull ComponentState state,
        @NotNull String message,
        @NotNull Instant at) {
}
