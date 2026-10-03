package pl.aniolstroz.call;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.aniolstroz.ai.StageClassifier;

/**
 * Decides whether the built-in keyword dictionary (risk/keywords.pl.yml) takes part in the risk. The model decides
 * which stages are in the call; the dictionary is only a safety net (rule 7, DET-03):
 * <ul>
 *   <li>{@code fallback} (default): the dictionary works only while the model is not available: canned MOCK answers
 *       (no API key) or a model that failed at its last call;</li>
 *   <li>{@code always}: it always works next to the model (the former behaviour);</li>
 *   <li>{@code off}: never. A deaf AI then means no detection, and {@code system.status} says the AI is down.</li>
 * </ul>
 * Set with {@code app.risk.keyword-baseline} (env APP_RISK_KEYWORD_BASELINE).
 */
@Component
public class KeywordBaseline {

    public enum Mode { FALLBACK, ALWAYS, OFF }

    private final Mode mode;
    private final StageClassifier classifier;
    private final AiHealth health;

    public KeywordBaseline(@Value("${app.risk.keyword-baseline:fallback}") String mode, StageClassifier classifier,
            AiHealth health) {
        this.mode = Mode.valueOf(mode.trim().toUpperCase(java.util.Locale.ROOT));
        this.classifier = classifier;
        this.health = health;
    }

    /** True when the dictionary should be applied to the next segment. */
    public boolean active() {
        return switch (mode) {
            case ALWAYS -> true;
            case OFF -> false;
            case FALLBACK -> classifier.isMock() || health.failing();
        };
    }
}
