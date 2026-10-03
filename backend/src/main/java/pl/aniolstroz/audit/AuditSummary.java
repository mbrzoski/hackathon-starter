package pl.aniolstroz.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.Map;
import pl.aniolstroz.ai.ClassifierError;

/**
 * Numbers measured over real AI calls (mock calls are not counted). Percentiles and averages are null, and so absent
 * from the JSON, while there is nothing to measure: a missing number is honest, a zero would not be.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditSummary(
        int aiCalls,
        int conversations,
        int mockCallsExcluded,
        Long latencyP50Ms,
        Long latencyP95Ms,
        BigDecimal avgCostPerCallUsd,
        BigDecimal avgCostPerConversationUsd,
        int rejectedQuotes,
        Map<ClassifierError, Integer> errorsByCause,
        Pricing pricing,
        String costNote) {

    public static final String COST_NOTE = "Koszt wyliczony z usage i cennika z konfiguracji.";

    /** The prices the cost was computed with, USD per 1M tokens. */
    public record Pricing(BigDecimal inputPerMillionUsd, BigDecimal cacheReadPerMillionUsd,
            BigDecimal outputPerMillionUsd) {
    }
}
