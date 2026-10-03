package pl.aniolstroz.ai;

import java.util.List;
import pl.aniolstroz.contracts.StageHit;

/**
 * One classifier call. {@code hits} are not trusted until {@code QuoteValidator} has set their {@code validated}
 * flag; on any error they are empty. {@code rawOutput} and {@code hits} can contain call text: log numbers only.
 */
public record ClassifierResult(
        String segmentRange,
        String model,
        String effort,
        String rawOutput,
        List<StageHit> hits,
        Usage usage,
        long latencyMs,
        String stopReason,
        ClassifierError error) {

    /**
     * Tokens as the API reports them. {@code inputTokens} are the new tokens only, {@code cacheReadInputTokens} come
     * from the cache and {@code cacheCreationInputTokens} were written to it (billed above the input price, and the
     * transcript block is written every call because it grows).
     */
    public record Usage(long inputTokens, long cacheReadInputTokens, long outputTokens, long cacheCreationInputTokens) {

        public Usage(long inputTokens, long cacheReadInputTokens, long outputTokens) {
            this(inputTokens, cacheReadInputTokens, outputTokens, 0);
        }
    }

    public ClassifierResult {
        hits = List.copyOf(hits);
    }

    public boolean failed() {
        return error != null;
    }

    public ClassifierResult withHits(List<StageHit> newHits) {
        return new ClassifierResult(segmentRange, model, effort, rawOutput, newHits, usage, latencyMs, stopReason, error);
    }

    /** A failure that happened before any answer, for example an unexpected exception in a classifier. */
    public static ClassifierResult failed(CallSnapshot snapshot, ClassifierError error) {
        return new ClassifierResult(snapshot.segmentRange(), "unknown", "unknown", null, List.of(),
                new Usage(0, 0, 0), 0, null, error);
    }
}
