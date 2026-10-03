package pl.aniolstroz.contracts;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Claude structured output: evidence only. Snake_case on the wire, this is the model response schema. */
public record StageHits(@JsonProperty("stage_hits") @NotNull List<@Valid Hit> stageHits) {

    public record Hit(
            @NotNull StageId stage,
            @JsonProperty("segment_id") @NotNull String segmentId,
            @NotNull String quote,
            @JsonProperty("speaker_role") @NotNull SpeakerRole speakerRole) {
    }
}
