package pl.aniolstroz.contracts;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

/** Demo scenario. {@code audioFile} is only used in REPLAY mode. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Scenario(
        @NotBlank @Pattern(regexp = "^[a-z0-9-]+$") String scenarioId,
        @NotBlank String title,
        @NotNull String description,
        @NotNull Mode mode,
        String audioFile,
        String source,
        String sourceUrl,
        @Valid Expected expected,
        @NotEmpty List<@Valid Segment> segments) {

    /** Expected properties for the evaluation run, not scores. */
    public record Expected(
            @NotNull RiskLevel maxLevel,
            @NotNull List<StageId> mustHitStages,
            @NotNull List<StageId> mustNotHitStages) {
    }

    public record Segment(
            @NotNull SpeakerLabel speaker,
            @NotBlank String text,
            @PositiveOrZero long delayMs) {
    }
}
