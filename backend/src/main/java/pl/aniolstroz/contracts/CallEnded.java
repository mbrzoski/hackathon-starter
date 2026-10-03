package pl.aniolstroz.contracts;

import jakarta.validation.constraints.NotBlank;

public record CallEnded(@NotBlank String callId, boolean hadAlert) {
}
