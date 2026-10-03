package pl.aniolstroz.risk;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import pl.aniolstroz.contracts.HitSource;
import pl.aniolstroz.contracts.RiskLevel;
import pl.aniolstroz.contracts.Sensitivity;
import pl.aniolstroz.contracts.SpeakerRole;
import pl.aniolstroz.contracts.StageHit;
import pl.aniolstroz.contracts.StageId;
import pl.aniolstroz.contracts.TriggeredBy;

/**
 * The only place where a risk level is computed (DET-01): a pure function of the hits and the sensitivity.
 *
 * <p>Rules (architecture section 5), counting distinct stages among the hits that count:
 * <ul>
 *   <li>HIGH: a MONEY_REQUEST or PAYMENT_CHANNEL stage together with one of AUTHORITY_CLAIM, URGENT_THREAT,
 *       SECRECY_DEMAND, ISOLATION.</li>
 *   <li>MEDIUM: two different stages that are not HIGH.</li>
 *   <li>LOW: one stage.</li>
 *   <li>CALM: MEDIUM needs two stages and HIGH needs three (a HIGH combination of only two stages is MEDIUM).</li>
 *   <li>SENSITIVE: the STANDARD level moved one up (LOW to MEDIUM, MEDIUM to HIGH).</li>
 *   <li>FAMILY_KEYWORD (a word the family asked for): at least MEDIUM, in every sensitivity.</li>
 * </ul>
 * Keyword hits take part in the same rules, so "policja" + "BLIK" is AUTHORITY_CLAIM + PAYMENT_CHANNEL and is HIGH.
 */
public final class RiskEngine {

    private static final Set<StageId> MONEY = EnumSet.of(StageId.MONEY_REQUEST, StageId.PAYMENT_CHANNEL);
    private static final Set<StageId> PRESSURE = EnumSet.of(
            StageId.AUTHORITY_CLAIM, StageId.URGENT_THREAT, StageId.SECRECY_DEMAND, StageId.ISOLATION);

    private RiskEngine() {
    }

    public static RiskAssessment computeLevel(List<StageHit> hits, Sensitivity sensitivity) {
        List<StageHit> counted = countedHits(hits);
        Set<StageId> stages = stagesOf(counted);
        RiskLevel level = levelFor(stages, sensitivity);
        return new RiskAssessment(level, triggeredBy(decidingHits(counted, stages, level, sensitivity)));
    }

    /**
     * The hits that decided the level. For HIGH reached by a money stage plus a pressure stage these are the hits of
     * those stages only: an unrelated extra stage (say a keyword hit on REMOTE_ACCESS) did not decide anything, so it
     * must not turn "llm" into "both". In every other case each counted stage took part in the count.
     */
    private static List<StageHit> decidingHits(
            List<StageHit> counted, Set<StageId> stages, RiskLevel level, Sensitivity sensitivity) {
        boolean combination = stages.stream().anyMatch(MONEY::contains) && stages.stream().anyMatch(PRESSURE::contains);
        if (level == RiskLevel.HIGH && combination && sensitivity != Sensitivity.CALM) {
            return counted.stream().filter(h -> MONEY.contains(h.stage()) || PRESSURE.contains(h.stage())).toList();
        }
        return counted;
    }

    /**
     * The hits that count: validated ones only (AI-08), and no MONEY_REQUEST or PAYMENT_CHANNEL said by the senior
     * or heard in the background (DET-02).
     */
    public static List<StageHit> countedHits(List<StageHit> hits) {
        return hits.stream().filter(RiskEngine::counts).toList();
    }

    public static Set<StageId> stagesOf(List<StageHit> hits) {
        Set<StageId> stages = EnumSet.noneOf(StageId.class);
        hits.forEach(h -> stages.add(h.stage()));
        return stages;
    }

    private static boolean counts(StageHit hit) {
        if (!hit.validated()) {
            return false;
        }
        boolean notFromCaller = hit.speakerRole() == SpeakerRole.SENIOR || hit.speakerRole() == SpeakerRole.BACKGROUND;
        return !(notFromCaller && MONEY.contains(hit.stage()));
    }

    private static RiskLevel levelFor(Set<StageId> stages, Sensitivity sensitivity) {
        int count = stages.size();
        if (count == 0) {
            return RiskLevel.NONE;
        }
        boolean highCombination = stages.stream().anyMatch(MONEY::contains) && stages.stream().anyMatch(PRESSURE::contains);
        RiskLevel level;
        if (sensitivity == Sensitivity.CALM) {
            level = highCombination && count >= 3 ? RiskLevel.HIGH : count >= 2 ? RiskLevel.MEDIUM : RiskLevel.LOW;
        } else {
            level = highCombination ? RiskLevel.HIGH : count >= 2 ? RiskLevel.MEDIUM : RiskLevel.LOW;
        }
        level = sensitivity == Sensitivity.SENSITIVE ? oneUp(level) : level;
        // A word the family asked to be warned about is worth an alert on its own, whatever the sensitivity.
        if (stages.contains(StageId.FAMILY_KEYWORD) && level.compareTo(RiskLevel.MEDIUM) < 0) {
            return RiskLevel.MEDIUM;
        }
        return level;
    }

    private static RiskLevel oneUp(RiskLevel level) {
        return switch (level) {
            case NONE -> RiskLevel.NONE;
            case LOW -> RiskLevel.MEDIUM;
            case MEDIUM, HIGH -> RiskLevel.HIGH;
        };
    }

    private static TriggeredBy triggeredBy(List<StageHit> counted) {
        boolean llm = counted.stream().anyMatch(h -> h.source() == HitSource.LLM);
        boolean keywords = counted.stream().anyMatch(h -> h.source() == HitSource.KEYWORDS);
        if (llm && keywords) {
            return TriggeredBy.BOTH;
        }
        if (llm) {
            return TriggeredBy.LLM;
        }
        return keywords ? TriggeredBy.KEYWORDS : null;
    }
}
