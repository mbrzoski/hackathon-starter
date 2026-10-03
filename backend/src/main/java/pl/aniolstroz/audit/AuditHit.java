package pl.aniolstroz.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import pl.aniolstroz.contracts.HitSource;
import pl.aniolstroz.contracts.SpeakerRole;
import pl.aniolstroz.contracts.StageHit;
import pl.aniolstroz.contracts.StageId;

/** A stage hit in the audit. {@code quote} is null once the text was cleared (DAT-03); the rest is numbers and ids. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditHit(StageId stage, String segId, String quote, SpeakerRole speakerRole, HitSource source,
        boolean validated) {

    static AuditHit of(StageHit hit) {
        return new AuditHit(hit.stage(), hit.segId(), hit.quote(), hit.speakerRole(), hit.source(), hit.validated());
    }

    AuditHit withoutQuote() {
        return new AuditHit(stage, segId, null, speakerRole, source, validated);
    }
}
