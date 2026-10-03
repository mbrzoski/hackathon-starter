package pl.aniolstroz.contracts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

public record RiskUpdate(
        @NotBlank String callId,
        @NotNull RiskLevel level,
        @NotNull RiskLevel previousLevel,
        @NotNull List<StageId> stages,
        @PositiveOrZero int warningSigns) {
}
