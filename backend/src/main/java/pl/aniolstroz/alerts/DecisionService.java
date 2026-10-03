package pl.aniolstroz.alerts;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.aniolstroz.config.Trace;
import pl.aniolstroz.contracts.Actor;
import pl.aniolstroz.contracts.Alert;
import pl.aniolstroz.contracts.Component;
import pl.aniolstroz.contracts.ComponentState;
import pl.aniolstroz.contracts.Decision;
import pl.aniolstroz.contracts.DecisionType;
import pl.aniolstroz.contracts.EventEnvelope.AlertDecisionEvent;
import pl.aniolstroz.contracts.EventEnvelope.SystemStatusEvent;
import pl.aniolstroz.contracts.SystemStatus;
import pl.aniolstroz.events.EventBus;

/**
 * Records what people decide about an alert: stores it, labels it for later evaluation (AI-10), publishes
 * {@code alert.decision} and, for the family, applies "don't count this stage" to the call. The system itself never
 * acts on a call: a decision only records and informs.
 */
@Service
public class DecisionService {

    private static final Logger log = LoggerFactory.getLogger(DecisionService.class);

    /** Who may make which decision (architecture 6.4 and 7). */
    private static final Map<Actor, Set<DecisionType>> ALLOWED = new EnumMap<>(Map.of(
            Actor.SENIOR, EnumSet.of(DecisionType.HUNG_UP, DecisionType.CALLED_TRUSTED, DecisionType.FALSE_ALARM),
            Actor.FAMILY, EnumSet.of(DecisionType.CONFIRMED_SCAM, DecisionType.FALSE_ALARM)));

    private static final Set<DecisionType> LABELS = EnumSet.of(DecisionType.FALSE_ALARM, DecisionType.CONFIRMED_SCAM);

    private final AlertStore alerts;
    private final DecisionStore decisions;
    private final LabelWriter labels;
    private final LiveCallAccess liveCall;
    private final EventBus eventBus;
    private final Clock clock;

    public DecisionService(AlertStore alerts, DecisionStore decisions, LabelWriter labels, LiveCallAccess liveCall,
            EventBus eventBus, Clock clock) {
        this.alerts = alerts;
        this.decisions = decisions;
        this.labels = labels;
        this.liveCall = liveCall;
        this.eventBus = eventBus;
        this.clock = clock;
    }

    /**
     * @throws AlertNotFoundException if there is no such alert (in the active call or the database)
     * @throws InvalidDecisionException if the actor cannot make this decision, the senior sends ignoredStages, or the
     *     call of the alert has ended so that ignoredStages cannot be applied
     */
    public Decision record(String alertId, DecisionCommand command) {
        Alert alert = findAlert(alertId).orElseThrow(() -> new AlertNotFoundException(alertId));
        validate(command);
        // Ignoring stages changes a running call. Once the call has ended there is nothing to change, and a decision
        // that claims otherwise would be stored without effect, so the whole request is refused first.
        if (!command.ignoredStages().isEmpty() && !liveCall.ignoreStages(alert.callId(), command.ignoredStages())) {
            throw new InvalidDecisionException("The call of this alert has ended, its stages can no longer be ignored");
        }

        Decision decision = new Decision(alertId, command.actor(), command.decision(), clock.instant());
        decisions.save(decision, alert.mode(), command.ignoredStages());
        Trace.flow("alerts | decision alert={} call={} {} by {} ignoredStages={}", Trace.id(alertId), Trace.id(alert.callId()),
                decision.decision(), decision.actor(), command.ignoredStages());
        if (LABELS.contains(decision.decision())) {
            writeLabel(alert, decision);
        }
        eventBus.publish(new AlertDecisionEvent(alert.mode(), decision.at(), decision));
        return decision;
    }

    /** The latest alerts, newest first, each with its decisions: the active call's alerts and stored ones. */
    public List<AlertHistoryEntry> recent(int limit) {
        List<Alert> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Alert alert : liveCall.alerts()) {
            if (seen.add(alert.alertId())) {
                merged.add(alert);
            }
        }
        for (Alert alert : alerts.recent(limit)) {
            if (seen.add(alert.alertId())) {
                merged.add(alert);
            }
        }
        merged.sort(Comparator.comparing(Alert::createdAt).reversed());
        List<Alert> latest = merged.subList(0, Math.min(limit, merged.size()));
        Map<String, List<Decision>> byAlert = decisions.findByAlertIds(latest.stream().map(Alert::alertId).toList());
        return latest.stream()
                .map(a -> new AlertHistoryEntry(a, byAlert.getOrDefault(a.alertId(), List.of())))
                .toList();
    }

    private Optional<Alert> findAlert(String alertId) {
        return liveCall.alerts().stream().filter(a -> a.alertId().equals(alertId)).findFirst()
                .or(() -> alerts.find(alertId));
    }

    private static void validate(DecisionCommand command) {
        if (!ALLOWED.get(command.actor()).contains(command.decision())) {
            throw new InvalidDecisionException(
                    command.actor() + " cannot make the decision " + command.decision());
        }
        if (!command.ignoredStages().isEmpty() && command.actor() != Actor.FAMILY) {
            throw new InvalidDecisionException("Only the family can ignore stages");
        }
    }

    /** A label that cannot be written is reported, but the decision itself stays valid. */
    private void writeLabel(Alert alert, Decision decision) {
        try {
            labels.append(alert, decision);
        } catch (IOException e) {
            // Type only: the message contains a file path.
            log.error("Cannot write evaluation label: {}", e.getClass().getName());
            eventBus.publish(new SystemStatusEvent(alert.mode(), clock.instant(), new SystemStatus(
                    Component.BACKEND, ComponentState.DEGRADED,
                    "Nie udało się zapisać etykiety do oceny. Decyzja została zapisana.", clock.instant())));
        }
    }
}
