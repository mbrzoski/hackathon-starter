package pl.aniolstroz.contracts;

import jakarta.validation.constraints.NotBlank;

public record CallStarted(@NotBlank String callId) {
}
