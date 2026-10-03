package pl.aniolstroz.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import pl.aniolstroz.ai.ClassifierError;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.RiskLevel;

/** One AI call as stored (OBS-03). {@code rawOutput} and the quotes are gone when {@code textCleared} is true. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditRecord(
        long id,
        String callId,
        Mode mode,
        Instant recordedAt,
        String segmentRange,
        String model,
        String effort,
        Usage usage,
        long latencyMs,
        String stopReason,
        ClassifierError error,
        String rawOutput,
        List<AuditHit> hits,
        List<AuditHit> keywordHits,
        RiskLevel levelBefore,
        RiskLevel levelAfter,
        boolean textCleared) {

    /** The same record with its text filled in: used for a running call, whose text is only in memory. */
    AuditRecord withText(String newRawOutput, List<AuditHit> newHits, List<AuditHit> newKeywordHits) {
        return new AuditRecord(id, callId, mode, recordedAt, segmentRange, model, effort, usage, latencyMs, stopReason,
                error, newRawOutput, newHits, newKeywordHits, levelBefore, levelAfter, false);
    }

    /** {@code cacheCreationInputTokens} are the tokens written to the prompt cache (billed above the input price). */
    public record Usage(long inputTokens, long cacheReadInputTokens, long outputTokens, long cacheCreationInputTokens) {

        public Usage(long inputTokens, long cacheReadInputTokens, long outputTokens) {
            this(inputTokens, cacheReadInputTokens, outputTokens, 0);
        }
    }
}
