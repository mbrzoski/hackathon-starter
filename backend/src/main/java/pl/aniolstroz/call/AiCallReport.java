package pl.aniolstroz.call;

import java.util.List;
import pl.aniolstroz.ai.ClassifierResult;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.RiskLevel;
import pl.aniolstroz.contracts.StageHit;

/**
 * One finished classifier call with its context: the result (hits carry the QuoteValidator verdict), the call's risk
 * level before and after the valid hits were applied, and the keyword hits for the same segments.
 */
public record AiCallReport(String callId, Mode mode, ClassifierResult result, RiskLevel levelBefore,
        RiskLevel levelAfter, List<StageHit> keywordHits) {

    public AiCallReport {
        keywordHits = List.copyOf(keywordHits);
    }
}
