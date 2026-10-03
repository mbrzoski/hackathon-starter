package pl.aniolstroz.contracts;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

/** One interim or final transcript segment. {@code sttConfidence} is null unless the STT provider returns it. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TranscriptSegment(
        @NotBlank String callId,
        @NotBlank @Pattern(regexp = "^s[0-9]+$") String segId,
        @JsonProperty("tStartMs") @PositiveOrZero long tStartMs,
        @JsonProperty("tEndMs") @PositiveOrZero long tEndMs,
        @NotNull String text,
        @JsonProperty("isFinal") boolean isFinal,
        @NotNull SpeakerLabel speaker,
        @DecimalMin("0") @DecimalMax("1") Double sttConfidence) {
}
