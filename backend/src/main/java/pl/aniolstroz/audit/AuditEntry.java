package pl.aniolstroz.audit;

import java.util.List;
import pl.aniolstroz.ai.ClassifierResult;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.RiskLevel;
import pl.aniolstroz.contracts.StageHit;

/**
 * What the audit is given for one classifier call: the result (its hits already carry the QuoteValidator verdict),
 * the risk level before and after the hits were applied, and the hits of the keyword layer for the same segments.
 * {@code late} means the answer came after the call ended: it was not validated, so its hits carry no verdict and are
 * not counted as rejected.
 */
public record AuditEntry(String callId, Mode mode, ClassifierResult result, RiskLevel levelBefore, RiskLevel levelAfter,
        List<StageHit> keywordHits, boolean late) {

    public AuditEntry {
        keywordHits = List.copyOf(keywordHits);
    }

    public AuditEntry(String callId, Mode mode, ClassifierResult result, RiskLevel levelBefore, RiskLevel levelAfter,
            List<StageHit> keywordHits) {
        this(callId, mode, result, levelBefore, levelAfter, keywordHits, false);
    }
}
