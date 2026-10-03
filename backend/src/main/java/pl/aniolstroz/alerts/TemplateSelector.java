package pl.aniolstroz.alerts;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import pl.aniolstroz.contracts.RiskLevel;
import pl.aniolstroz.contracts.StageId;

/** DET-04: pure choice of the alert template from the risk level and the stages that were hit. */
public final class TemplateSelector {

    public static final String HIGH_SECRECY_MONEY = "high-secrecy-money";
    public static final String HIGH_PAYMENT_CHANNEL = "high-payment-channel";
    public static final String HIGH_GENERAL = "high-general";
    public static final String MEDIUM_ISOLATION = "medium-isolation";
    public static final String MEDIUM_GENERAL = "medium-general";
    public static final String MEDIUM_FAMILY_KEYWORD = "medium-family-keyword";

    /** Every id {@link #select} can return; each must exist in templates/alerts.pl.yml. */
    public static final List<String> ALL_IDS = List.of(
            HIGH_SECRECY_MONEY, HIGH_PAYMENT_CHANNEL, HIGH_GENERAL, MEDIUM_ISOLATION, MEDIUM_GENERAL,
            MEDIUM_FAMILY_KEYWORD);

    private TemplateSelector() {
    }

    /** Empty below MEDIUM: those levels are logged and nothing is shown to the senior. */
    public static Optional<String> select(RiskLevel level, Set<StageId> stages) {
        return switch (level) {
            case HIGH -> Optional.of(highTemplate(stages));
            case MEDIUM -> Optional.of(mediumTemplate(stages));
            case LOW, NONE -> Optional.empty();
        };
    }

    private static String mediumTemplate(Set<StageId> stages) {
        if (stages.contains(StageId.ISOLATION)) {
            return MEDIUM_ISOLATION;
        }
        // Only the family's word, nothing of the known manipulation stages: say what it is, not "signs of a scam".
        Set<StageId> others = java.util.EnumSet.copyOf(stages);
        others.remove(StageId.FAMILY_KEYWORD);
        return stages.contains(StageId.FAMILY_KEYWORD) && others.isEmpty() ? MEDIUM_FAMILY_KEYWORD : MEDIUM_GENERAL;
    }

    private static String highTemplate(Set<StageId> stages) {
        if (stages.contains(StageId.SECRECY_DEMAND) && stages.contains(StageId.MONEY_REQUEST)) {
            return HIGH_SECRECY_MONEY;
        }
        if (stages.contains(StageId.PAYMENT_CHANNEL)) {
            return HIGH_PAYMENT_CHANNEL;
        }
        return HIGH_GENERAL;
    }
}
