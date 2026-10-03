package pl.aniolstroz.alerts;

import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.aniolstroz.contracts.Actor;
import pl.aniolstroz.contracts.Decision;
import pl.aniolstroz.contracts.DecisionType;
import pl.aniolstroz.contracts.api.AlertsApi;
import pl.aniolstroz.contracts.model.AlertWithDecisions;
import pl.aniolstroz.contracts.model.DecisionRequest;

/**
 * /api/alerts, the interface is generated from contracts/openapi.yaml. alertId comes from the path and the time from
 * the server clock. Errors leave as ProblemDetail through ApiExceptionHandler (API-06).
 */
@RestController
class AlertsController implements AlertsApi {

    private final DecisionService service;

    AlertsController(DecisionService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<Decision> submitDecision(String alertId, DecisionRequest request) {
        // The generated enums carry the same names as ours (SENIOR, HUNG_UP, ...).
        DecisionCommand command = new DecisionCommand(
                Actor.valueOf(request.getActor().name()),
                DecisionType.valueOf(request.getDecision().name()),
                Set.copyOf(request.getIgnoredStages()));
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.record(alertId, command));
        } catch (AlertNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (InvalidDecisionException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @Override
    public ResponseEntity<List<AlertWithDecisions>> listAlerts(Integer limit) {
        return ResponseEntity.ok(service.recent(limit).stream()
                .map(entry -> new AlertWithDecisions(entry.alert(), entry.decisions()))
                .toList());
    }
}
