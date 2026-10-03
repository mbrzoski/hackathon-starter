package pl.aniolstroz.alerts;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pl.aniolstroz.contracts.Alert;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.RiskLevel;
import pl.aniolstroz.contracts.StageHit;
import pl.aniolstroz.contracts.TriggeredBy;
import pl.aniolstroz.risk.RiskEngine;

/** Builds an {@link Alert} from validated hits and a pre-written template; no text is generated (DET-04). */
@Component
public class AlertFactory {

    private final AlertTemplates templates;
    private final Clock clock;

    public AlertFactory(AlertTemplates templates, Clock clock) {
        this.templates = templates;
        this.clock = clock;
    }

    /** @throws IllegalArgumentException below MEDIUM, where there is no alert */
    public Alert create(String callId, Mode mode, RiskLevel level, List<StageHit> hits, TriggeredBy triggeredBy) {
        List<StageHit> validated = hits.stream().filter(StageHit::validated).toList();
        String templateId = TemplateSelector.select(level, RiskEngine.stagesOf(validated))
                .orElseThrow(() -> new IllegalArgumentException("No alert for level " + level));
        AlertTemplates.Template template = templates.get(templateId);
        return new Alert(UUID.randomUUID().toString(), callId, level, validated, templateId,
                template.shortText(), template.advice(), triggeredBy, clock.instant(), mode);
    }
}
