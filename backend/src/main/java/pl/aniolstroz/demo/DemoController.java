package pl.aniolstroz.demo;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.aniolstroz.call.CallAlreadyActiveException;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.api.DemoApi;
import pl.aniolstroz.contracts.model.ReplayRequest;
import pl.aniolstroz.contracts.model.ScenarioSummary;

/**
 * /api/demo/*, the interface is generated from contracts/openapi.yaml. Errors leave as ProblemDetail through
 * ApiExceptionHandler (API-06).
 */
@RestController
class DemoController implements DemoApi {

    private final ScenarioRepository scenarios;
    private final ScriptedPlayer player;

    DemoController(ScenarioRepository scenarios, ScriptedPlayer player) {
        this.scenarios = scenarios;
        this.player = player;
    }

    @Override
    public ResponseEntity<List<ScenarioSummary>> listScenarios() {
        return ResponseEntity.ok(scenarios.all().stream()
                .map(s -> new ScenarioSummary(s.scenarioId(), s.title(), s.description()))
                .toList());
    }

    @Override
    public ResponseEntity<Void> startReplay(ReplayRequest request) {
        if (request.getMode() != Mode.SCRIPTED) {
            // REPLAY (recorded audio) and MOCK come in later tasks.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        try {
            player.start(request.getScenarioId(), request.getSpeed());
        } catch (ScenarioNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (CallAlreadyActiveException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Void> stopReplay() {
        player.stop();
        return ResponseEntity.noContent().build();
    }
}
