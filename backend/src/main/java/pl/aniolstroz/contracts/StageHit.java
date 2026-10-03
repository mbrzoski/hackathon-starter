package pl.aniolstroz.contracts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Evidence of one stage: a verbatim quote from one segment. */
public record StageHit(
        @NotNull StageId stage,
        @NotBlank @Pattern(regexp = "^s[0-9]+$") String segId,
        @NotBlank String quote,
        @NotNull SpeakerRole speakerRole,
        @NotNull HitSource source,
        boolean validated) {
}
