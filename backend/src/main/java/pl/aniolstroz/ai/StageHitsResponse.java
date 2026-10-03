package pl.aniolstroz.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import pl.aniolstroz.contracts.SpeakerRole;
import pl.aniolstroz.contracts.StageId;

/**
 * What Claude answers, written by hand (CON-07): the generator's annotations could change the schema sent to the API.
 * Snake_case on purpose; its JSON must satisfy components/schemas/StageHits (a test checks it).
 */
public record StageHitsResponse(@JsonProperty("stage_hits") List<Hit> stageHits) {

    public record Hit(
            @JsonProperty("stage") StageId stage,
            @JsonProperty("segment_id") String segmentId,
            @JsonProperty("quote") String quote,
            @JsonProperty("speaker_role") SpeakerRole speakerRole) {
    }
}
