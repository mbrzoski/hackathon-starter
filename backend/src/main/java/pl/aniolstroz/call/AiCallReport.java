package pl.aniolstroz.call;

import java.util.List;
import pl.aniolstroz.ai.ClassifierResult;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.RiskLevel;
import pl.aniolstroz.contracts.StageHit;

/**
 * One finished classifier call with its context: the result (hits carry the QuoteValidator verdict), the call's risk
 * level before and after the valid hits were applied, and the keyword hits for the same segments.
 *
 * <p>{@code late} is true when the answer arrived after the call had ended. A late answer is not validated and
 * changes nothing, so its hits are not "rejected": the audit shows it as a late result instead.
 */
public record AiCallReport(String callId, Mode mode, ClassifierResult result, RiskLevel levelBefore,
        RiskLevel levelAfter, List<StageHit> keywordHits, boolean late) {

    public AiCallReport {
        keywordHits = List.copyOf(keywordHits);
    }

    public AiCallReport(String callId, Mode mode, ClassifierResult result, RiskLevel levelBefore,
            RiskLevel levelAfter, List<StageHit> keywordHits) {
        this(callId, mode, result, levelBefore, levelAfter, keywordHits, false);
    }
}
