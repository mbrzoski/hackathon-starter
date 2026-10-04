package pl.aniolstroz.demo;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.aniolstroz.ai.StageClassifier;
import pl.aniolstroz.call.CallAlreadyActiveException;
import pl.aniolstroz.stt.AudioReplayer;
import pl.aniolstroz.stt.RecordingNotFoundException;
import pl.aniolstroz.stt.SttUnavailableException;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.PhoneCall;
import pl.aniolstroz.contracts.api.DemoApi;
import pl.aniolstroz.contracts.model.PhoneCallRequest;
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
    private final PhoneCallSimulation phoneCall;
    private final AudioReplayer replayer;
    private final StageClassifier classifier;

    DemoController(ScenarioRepository scenarios, ScriptedPlayer player, PhoneCallSimulation phoneCall,
            AudioReplayer replayer, StageClassifier classifier) {
        this.scenarios = scenarios;
        this.player = player;
        this.phoneCall = phoneCall;
        this.replayer = replayer;
        this.classifier = classifier;
    }

    @Override
    public ResponseEntity<PhoneCall> getPhoneCall() {
        return ResponseEntity.ok(phoneCall.current());
    }

    @Override
    public ResponseEntity<PhoneCall> setPhoneCall(PhoneCallRequest request) {
        return ResponseEntity.ok(phoneCall.set(request.getActive()));
    }

    @Override
    public ResponseEntity<List<ScenarioSummary>> listScenarios() {
        return ResponseEntity.ok(scenarios.all().stream()
                .map(s -> new ScenarioSummary(s.scenarioId(), s.title(), s.description(), replayer.has(s.scenarioId())))
                .toList());
    }

    @Override
    public ResponseEntity<Void> startReplay(ReplayRequest request) {
        try {
            if (request.getMode() == Mode.SCRIPTED) {
                player.start(request.getScenarioId(), request.getSpeed());
            } else if (request.getMode() == Mode.REPLAY) {
                if (scenarios.find(request.getScenarioId()).isEmpty()) {
                    throw new ScenarioNotFoundException(request.getScenarioId());
                }
                // REPLAY promises a real AI, like SCRIPTED; with canned answers the call must say MOCK (D-19).
                replayer.start(request.getScenarioId(), classifier.isMock() ? Mode.MOCK : Mode.REPLAY,
                        request.getSpeed());
            } else {
                // LIVE comes from the microphone (/ws/audio), MOCK is chosen by the backend, not asked for.
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            }
        } catch (ScenarioNotFoundException | RecordingNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (CallAlreadyActiveException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        } catch (SttUnavailableException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), e);
        }
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Void> stopReplay() {
        player.stop();
        replayer.stop();
        return ResponseEntity.noContent().build();
    }
}
